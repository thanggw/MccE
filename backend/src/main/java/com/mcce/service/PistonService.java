package com.mcce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcce.dto.ExecutionResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mcce.execution.strategy", havingValue = "PISTON")
public class PistonService implements ExecutionService {

	private static final Map<String, String> STABLE_VERSION_MAP = Map.of(
		"java",
		"15.0.2",
		"python",
		"3.10.0",
		"javascript",
		"18.15.0"
	);

	private static final Map<String, String> PREFERRED_RUNTIME_MAP = Map.of(
		"javascript",
		"node"
	);

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String pistonApiUrl;
	private final String pistonRuntimesUrl;
	private final String pistonVersion;
	private final int runTimeoutMillis;
	private final int compileTimeoutMillis;

	public PistonService(
		ObjectMapper objectMapper,
		@Value("${mcce.execution.piston.url:https://emkc.org/api/v2/piston/execute}") String pistonApiUrl,
		@Value("${mcce.execution.piston.runtimes-url:https://emkc.org/api/v2/piston/runtimes}") String pistonRuntimesUrl,
		@Value("${mcce.execution.piston.version:*}") String pistonVersion,
		@Value("${mcce.execution.piston.run-timeout-ms:5000}") int runTimeoutMillis,
		@Value("${mcce.execution.piston.compile-timeout-ms:5000}") int compileTimeoutMillis
	) {
		this.httpClient = HttpClient.newHttpClient();
		this.objectMapper = objectMapper;
		this.pistonApiUrl = pistonApiUrl;
		this.pistonRuntimesUrl = pistonRuntimesUrl;
		this.pistonVersion = pistonVersion;
		this.runTimeoutMillis = runTimeoutMillis;
		this.compileTimeoutMillis = compileTimeoutMillis;
	}

	@Override
	public ExecutionResult execute(String code, String language) throws IOException, InterruptedException {
		String normalizedLanguage = normalizeLanguage(language);
		String pistonLanguage = mapLanguage(normalizedLanguage);
		String resolvedVersion = resolveVersion(pistonLanguage);
		String requestBody = objectMapper.writeValueAsString(Map.of(
			"language",
			pistonLanguage,
			"version",
			resolvedVersion,
			"files",
			new Object[] {
				Map.of(
					"name",
					defaultFileName(normalizedLanguage),
					"content",
					code == null ? "" : code
				),
			},
			"stdin",
			"",
			"compile_timeout",
			compileTimeoutMillis,
			"run_timeout",
			runTimeoutMillis
		));

		HttpRequest request = HttpRequest.newBuilder(URI.create(pistonApiUrl))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(requestBody))
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Piston API returned status " + response.statusCode());
		}

		JsonNode root = objectMapper.readTree(response.body());
		JsonNode run = root.path("run");
		String stdout = run.path("stdout").asText("");
		String stderr = run.path("stderr").asText("");
		String combinedOutput = stdout + (stderr.isEmpty() ? "" : stderr);
		int exitCode = run.path("code").isMissingNode() ? 1 : run.path("code").asInt(1);
		return new ExecutionResult(
			language,
			combinedOutput,
			stderr,
			exitCode,
			exitCode == 0
		);
	}

	private String resolveVersion(String pistonLanguage) throws IOException, InterruptedException {
		if (StringUtils.isNotBlank(pistonVersion) && !"*".equals(pistonVersion.trim())) {
			return pistonVersion.trim();
		}

		List<PistonRuntime> runtimes = fetchRuntimes();

		String mappedVersion = STABLE_VERSION_MAP.get(pistonLanguage);
		if (mappedVersion != null && isVersionAvailable(runtimes, pistonLanguage, mappedVersion)) {
			return mappedVersion;
		}

		return findLatestVersion(runtimes, pistonLanguage)
			.orElseThrow(() -> new IllegalArgumentException(
				"No supported Piston runtime found for language: " + pistonLanguage
			));
	}

	private boolean isVersionAvailable(
		List<PistonRuntime> runtimes,
		String pistonLanguage,
		String version
	) {
		return runtimes
			.stream()
			.filter(runtime -> runtime.language().equals(pistonLanguage))
			.anyMatch(runtime -> runtime.version().equals(version) && matchesPreferredRuntime(pistonLanguage, runtime));
	}

	private Optional<String> findLatestVersion(List<PistonRuntime> runtimes, String pistonLanguage) {
		return runtimes
			.stream()
			.filter(runtime -> runtime.language().equals(pistonLanguage))
			.filter(runtime -> matchesPreferredRuntime(pistonLanguage, runtime))
			.max(Comparator.comparing(PistonRuntime::version, this::compareVersions))
			.map(PistonRuntime::version);
	}

	private boolean matchesPreferredRuntime(String pistonLanguage, PistonRuntime runtime) {
		String preferredRuntime = PREFERRED_RUNTIME_MAP.get(pistonLanguage);
		if (preferredRuntime == null) {
			return true;
		}
		return preferredRuntime.equals(runtime.runtime());
	}

	private List<PistonRuntime> fetchRuntimes() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(pistonRuntimesUrl))
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Piston runtimes API returned status " + response.statusCode());
		}

		JsonNode runtimes = objectMapper.readTree(response.body());
		return objectMapper.readerForListOf(PistonRuntime.class).readValue(runtimes);
	}

	private int compareVersions(String left, String right) {
		String[] leftParts = left.split("\\.");
		String[] rightParts = right.split("\\.");
		int maxParts = Math.max(leftParts.length, rightParts.length);
		for (int index = 0; index < maxParts; index++) {
			int leftValue = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
			int rightValue = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
			if (leftValue != rightValue) {
				return Integer.compare(leftValue, rightValue);
			}
		}
		return left.compareTo(right);
	}

	private int parseVersionPart(String part) {
		String digits = part.replaceAll("[^0-9].*$", "");
		if (digits.isEmpty()) {
			return 0;
		}
		return Integer.parseInt(digits);
	}

	private String normalizeLanguage(String language) {
		if (language == null || language.trim().isEmpty()) {
			throw new IllegalArgumentException("language is required");
		}
		return language.trim().toLowerCase(Locale.ROOT);
	}

	private String mapLanguage(String language) {
		return switch (language) {
			case "javascript", "node", "nodejs" -> "javascript";
			case "cpp" -> "c++";
			case "java", "python" -> language;
			default -> throw new IllegalArgumentException("unsupported language: " + language);
		};
	}

	private String defaultFileName(String language) {
		return switch (language) {
			case "java" -> "Main.java";
			case "python" -> "main.py";
			case "cpp" -> "main.cpp";
			case "javascript", "node", "nodejs" -> "main.js";
			default -> throw new IllegalArgumentException("unsupported language: " + language);
		};
	}

	private record PistonRuntime(
		String language,
		String version,
		String runtime
	) {}
}
