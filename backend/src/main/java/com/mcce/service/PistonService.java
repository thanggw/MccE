package com.mcce.service;

import com.mcce.dto.ExecutionResult;
import com.mcce.dto.Judge0SubmissionRequest;
import com.mcce.dto.Judge0SubmissionResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnExpression(
	"'${mcce.execution.strategy:DOCKER}'.equalsIgnoreCase('PISTON') || " +
	"'${mcce.execution.strategy:DOCKER}'.equalsIgnoreCase('JUDGE0')"
)
public class PistonService implements ExecutionService {

	private static final int JUDGE0_STATUS_ACCEPTED = 3;
	private static final java.util.regex.Pattern JAVA_PACKAGE_PATTERN = java.util.regex.Pattern.compile(
		"(?m)^\\s*package\\s+[\\w.]+\\s*;\\s*"
	);
	private static final java.util.regex.Pattern JAVA_CLASS_DECLARATION_PATTERN = java.util.regex.Pattern.compile(
		"(?m)(?:public\\s+)?class\\s+[A-Za-z_$][A-Za-z0-9_$]*[^\\{]*\\{"
	);
	private static final java.util.regex.Pattern JAVA_CLASS_NAME_PATTERN = java.util.regex.Pattern.compile(
		"(?:public\\s+)?class\\s+[A-Za-z_$][A-Za-z0-9_$]*"
	);
	private static final java.util.regex.Pattern JAVA_PUBLIC_CLASS_PREFIX_PATTERN = java.util.regex.Pattern.compile(
		"\\bpublic\\s+class\\b"
	);
	private static final java.util.regex.Pattern JAVA_MAIN_METHOD_PATTERN = java.util.regex.Pattern.compile(
		"\\bstatic\\s+void\\s+main\\s*\\("
	);
	private static final Map<String, Integer> LANGUAGE_ID_MAP = Map.of(
		"java", 62,
		"python", 71,
		"cpp", 54,
		"javascript", 63
	);

	private final RestTemplate restTemplate;
	private final String judge0ApiUrl;

	public PistonService(
		RestTemplateBuilder restTemplateBuilder,
		@Value("${mcce.execution.judge0.url:https://ce.judge0.com/submissions?base64_encoded=false&wait=true}") String judge0ApiUrl,
		@Value("${mcce.execution.judge0.timeout-ms:10000}") int timeoutMillis
	) {
		this.restTemplate = restTemplateBuilder
			.setConnectTimeout(Duration.ofMillis(timeoutMillis))
			.setReadTimeout(Duration.ofMillis(timeoutMillis))
			.build();
		this.judge0ApiUrl = judge0ApiUrl;
	}

	@Override
	public ExecutionResult execute(String code, String language) throws IOException {
		String normalizedLanguage = normalizeLanguage(language);
		Integer languageId = mapLanguageId(normalizedLanguage);
		String processedCode = preprocessSourceCode(normalizedLanguage, code == null ? "" : code);
		Judge0SubmissionRequest request = new Judge0SubmissionRequest(
			languageId,
			processedCode,
			""
		);

		ResponseEntity<Judge0SubmissionResponse> response;
		try {
			response = restTemplate.postForEntity(
				judge0ApiUrl,
				request,
				Judge0SubmissionResponse.class
			);
		} catch (RestClientResponseException exception) {
			String message = "Judge0 API returned status " + exception.getRawStatusCode();
			if (StringUtils.isNotBlank(exception.getResponseBodyAsString())) {
				message += ": " + exception.getResponseBodyAsString();
			}
			throw new IOException(message, exception);
		}

		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IOException("Judge0 API returned status " + response.getStatusCode().value());
		}

		Judge0SubmissionResponse body = response.getBody();
		if (body == null || body.getStatus() == null || body.getStatus().getId() == null) {
			throw new IOException("Judge0 API returned an incomplete response");
		}

		Integer statusId = body.getStatus().getId();
		boolean success = statusId == JUDGE0_STATUS_ACCEPTED;
		String output;
		String error;
		if (success) {
			output = StringUtils.defaultString(body.getStdout());
			error = "";
		} else {
			output = firstNonBlank(
				body.getStderr(),
				body.getCompileOutput(),
				body.getStdout(),
				body.getStatus().getDescription(),
				"Execution failed"
			);
			error = firstNonBlank(
				body.getStderr(),
				body.getCompileOutput(),
				body.getStatus().getDescription(),
				output
			);
		}

		return new ExecutionResult(language, output, error, statusId, success);
	}

	private String normalizeLanguage(String language) {
		if (language == null || language.trim().isEmpty()) {
			throw new IllegalArgumentException("language is required");
		}
		return language.trim().toLowerCase(Locale.ROOT);
	}

	private Integer mapLanguageId(String language) {
		String normalized = switch (language) {
			case "javascript", "node", "nodejs" -> "javascript";
			case "c++" -> "cpp";
			default -> language;
		};

		Integer languageId = LANGUAGE_ID_MAP.get(normalized);
		if (languageId == null) {
			throw new IllegalArgumentException("unsupported language: " + language);
		}
		return languageId;
	}

	String preprocessSourceCode(String language, String code) {
		if (!"java".equals(language)) {
			return code;
		}

		String withoutPackage = JAVA_PACKAGE_PATTERN.matcher(code).replaceAll("");
		List<JavaClassBlock> classBlocks = findTopLevelJavaClassBlocks(withoutPackage);
		if (classBlocks.isEmpty()) {
			return withoutPackage;
		}

		int mainClassIndex = findMainClassIndex(classBlocks);
		if (mainClassIndex < 0) {
			return withoutPackage;
		}

		StringBuilder rewrittenSource = new StringBuilder(withoutPackage.length());
		int cursor = 0;
		for (int index = 0; index < classBlocks.size(); index++) {
			JavaClassBlock classBlock = classBlocks.get(index);
			rewrittenSource.append(withoutPackage, cursor, classBlock.declarationStart());
			rewrittenSource.append(rewriteClassDeclaration(classBlock.declaration(), index == mainClassIndex));
			cursor = classBlock.declarationEnd();
		}
		rewrittenSource.append(withoutPackage.substring(cursor));
		return rewrittenSource.toString();
	}

	private List<JavaClassBlock> findTopLevelJavaClassBlocks(String source) {
		List<JavaClassBlock> classBlocks = new ArrayList<>();
		var matcher = JAVA_CLASS_DECLARATION_PATTERN.matcher(source);
		int searchFrom = 0;
		while (matcher.find(searchFrom)) {
			int declarationStart = matcher.start();
			int declarationEnd = matcher.end();
			int bodyStart = declarationEnd - 1;
			int bodyEnd = findMatchingBrace(source, bodyStart);
			if (bodyEnd < 0) {
				break;
			}

			String declaration = source.substring(declarationStart, declarationEnd);
			String body = source.substring(bodyStart + 1, bodyEnd);
			classBlocks.add(new JavaClassBlock(declarationStart, declarationEnd, declaration, body));
			searchFrom = bodyEnd + 1;
		}
		return classBlocks;
	}

	private int findMatchingBrace(String source, int openingBraceIndex) {
		int depth = 0;
		for (int index = openingBraceIndex; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return index;
				}
			}
		}
		return -1;
	}

	private int findMainClassIndex(List<JavaClassBlock> classBlocks) {
		for (int index = 0; index < classBlocks.size(); index++) {
			if (JAVA_MAIN_METHOD_PATTERN.matcher(classBlocks.get(index).body()).find()) {
				return index;
			}
		}
		return -1;
	}

	private String rewriteClassDeclaration(String declaration, boolean mainClass) {
		if (mainClass) {
			return JAVA_CLASS_NAME_PATTERN.matcher(declaration).replaceFirst("public class Main");
		}

		return JAVA_PUBLIC_CLASS_PREFIX_PATTERN.matcher(declaration).replaceFirst("class");
	}

	private String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		return "";
	}

	private record JavaClassBlock(
		int declarationStart,
		int declarationEnd,
		String declaration,
		String body
	) {}
}
