package com.mcce.service;

import com.mcce.dto.ExecutionResult;
import com.mcce.dto.Judge0SubmissionRequest;
import com.mcce.dto.Judge0SubmissionResponse;
import java.io.IOException;
import java.time.Duration;
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
		Judge0SubmissionRequest request = new Judge0SubmissionRequest(
			languageId,
			code == null ? "" : code,
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

	private String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		return "";
	}
}
