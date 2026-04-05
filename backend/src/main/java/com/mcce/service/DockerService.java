package com.mcce.service;

import com.mcce.dto.ExecutionRequest;
import com.mcce.dto.ExecutionResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.PullImageResultCallback;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
	name = "mcce.execution.strategy",
	havingValue = "DOCKER",
	matchIfMissing = true
)
public class DockerService implements ExecutionService {

	private final Duration executionTimeout;
	private final long memoryLimitBytes;
	private final String workspaceDir;
	private final String networkMode;
	private final boolean readonlyRootfs;
	private final String dockerHost;
	private final String javaImage;
	private final String pythonImage;
	private final String cppImage;
	private final String javascriptImage;
	private final ExecutionWorkspaceService workspaceService;
	private volatile DockerClient dockerClient;

	public DockerService(
		ExecutionWorkspaceService workspaceService,
		@Value("${mcce.execution.timeout:5s}") Duration executionTimeout,
		@Value("${mcce.execution.memory-limit:256MB}") org.springframework.util.unit.DataSize memoryLimit,
		@Value("${mcce.execution.workspace-dir:/workspace}") String workspaceDir,
		@Value("${mcce.execution.network-mode:none}") String networkMode,
		@Value("${mcce.execution.readonly-rootfs:true}") boolean readonlyRootfs,
		@Value("${mcce.execution.docker-host:}") String dockerHost,
		@Value("${mcce.execution.images.java:eclipse-temurin:17-jdk}") String javaImage,
		@Value("${mcce.execution.images.python:python:3.12-alpine}") String pythonImage,
		@Value("${mcce.execution.images.cpp:gcc:14}") String cppImage,
		@Value("${mcce.execution.images.javascript:node:20-alpine}") String javascriptImage
	) {
		this.workspaceService = workspaceService;
		this.executionTimeout = executionTimeout;
		this.memoryLimitBytes = memoryLimit.toBytes();
		this.workspaceDir = workspaceDir;
		this.networkMode = networkMode;
		this.readonlyRootfs = readonlyRootfs;
		this.dockerHost = dockerHost;
		this.javaImage = javaImage;
		this.pythonImage = pythonImage;
		this.cppImage = cppImage;
		this.javascriptImage = javascriptImage;
	}

	@Override
	public ExecutionResult execute(String code, String language)
		throws IOException, InterruptedException {
		ExecutionRequest request = new ExecutionRequest(
			language,
			defaultEntryFile(language),
			null,
			List.of(new ExecutionRequest.File(
				defaultEntryFile(language),
				defaultEntryFile(language),
				code == null ? "" : code
			))
		);
		Path preparedWorkspace = workspaceService.prepareWorkspace(request);
		try {
			String output = executeInContainer(
				preparedWorkspace,
				language,
				defaultEntryFile(language),
				null
			);
			return new ExecutionResult(language, output, "", 0, true);
		} finally {
			workspaceService.cleanupWorkspace(preparedWorkspace);
		}
	}

	public String executeInContainer(Path workspaceDir, String language)
		throws IOException, InterruptedException {
		return executeInContainer(workspaceDir, language, null, null);
	}

	public String executeInContainer(Path workspaceDir, String language, String stdin)
		throws IOException, InterruptedException {
		return executeInContainer(workspaceDir, language, null, stdin);
	}

	public String executeInContainer(
		Path workspaceDir,
		String language,
		String entryFile,
		String stdin
	) throws IOException, InterruptedException {
		validateWorkspace(workspaceDir);

		RunnerSpec runnerSpec = resolveRunnerSpec(
			workspaceDir,
			language,
			resolveEntryFile(workspaceDir, language, entryFile)
		);
		DockerClient client = getDockerClient();
		ensureImageAvailable(client, runnerSpec.image());

		String containerId = null;
		LogCollector logCollector = new LogCollector();

		try {
			containerId = createContainer(client, workspaceDir, runnerSpec).getId();

			var attachCmd = client
				.attachContainerCmd(containerId)
				.withStdOut(true)
				.withStdErr(true)
				.withFollowStream(true)
				.withLogs(false);

			InputStream stdinStream = toStdinStream(stdin);
			if (stdinStream != null) {
				attachCmd.withStdIn(stdinStream);
			}

			attachCmd.exec(logCollector);
			client.startContainerCmd(containerId).exec();

			Integer exitCode = client
				.waitContainerCmd(containerId)
				.start()
				.awaitStatusCode(executionTimeout.toMillis(), TimeUnit.MILLISECONDS);

			if (exitCode == null) {
				client.killContainerCmd(containerId).exec();
				return appendTimeoutMessage(logCollector.getOutput(), executionTimeout);
			}

			logCollector.awaitLogs();
			return logCollector.getOutput();
		} finally {
			if (containerId != null) {
				removeContainerQuietly(client, containerId);
			}
		}
	}

	public InteractiveContainerSession startInteractiveSession(
		Path workspaceDir,
		String language,
		String entryFile,
		Consumer<String> outputConsumer
	) throws IOException, InterruptedException {
		validateWorkspace(workspaceDir);
		Objects.requireNonNull(outputConsumer, "outputConsumer");

		String resolvedEntryFile = resolveEntryFile(workspaceDir, language, entryFile);
		RunnerSpec runnerSpec = resolveRunnerSpec(workspaceDir, language, resolvedEntryFile);
		DockerClient client = getDockerClient();
		ensureImageAvailable(client, runnerSpec.image());

		CreateContainerResponse container = createContainer(client, workspaceDir, runnerSpec);
		String containerId = container.getId();
		PipedOutputStream stdinWriter = new PipedOutputStream();
		PipedInputStream stdinReader = new PipedInputStream(stdinWriter, 8192);
		StreamingCallback callback = new StreamingCallback(outputConsumer);

		try {
			client
				.attachContainerCmd(containerId)
				.withStdOut(true)
				.withStdErr(true)
				.withStdIn(stdinReader)
				.withFollowStream(true)
				.withLogs(false)
				.exec(callback);

			client.startContainerCmd(containerId).exec();

			return new InteractiveContainerSession(
				client,
				containerId,
				stdinWriter,
				stdinReader,
				callback
			);
		} catch (RuntimeException exception) {
			closeQuietly(stdinWriter);
			closeQuietly(stdinReader);
			removeContainerQuietly(client, containerId);
			throw exception;
		}
	}

	private void validateWorkspace(Path workspaceDir) {
		if (workspaceDir == null || !Files.isDirectory(workspaceDir)) {
			throw new IllegalArgumentException("workspaceDir must be an existing directory");
		}
	}

	private String resolveEntryFile(Path workspaceDir, String language, String entryFile)
		throws IOException {
		if (entryFile != null && !entryFile.trim().isEmpty()) {
			return normalizeRelativePath(entryFile.trim());
		}

		return switch (normalizeLanguage(language)) {
			case "java" -> findFirstFile(workspaceDir, ".java");
			case "python" -> findFirstFile(workspaceDir, ".py");
			case "cpp" -> findFirstFile(workspaceDir, ".cpp", ".cc", ".cxx");
			case "javascript", "node", "nodejs" -> findFirstFile(workspaceDir, ".js", ".mjs");
			default -> throw new IllegalArgumentException("unsupported language: " + language);
		};
	}

	private RunnerSpec resolveRunnerSpec(Path workspaceDir, String language, String entryFile)
		throws IOException {
		String normalizedLanguage = normalizeLanguage(language);
		String normalizedEntryFile = normalizeRelativePath(entryFile);

		return switch (normalizedLanguage) {
			case "java" -> {
				String mainClass = Path
					.of(normalizedEntryFile)
					.getFileName()
					.toString()
					.replaceFirst("\\.java$", "");
				yield new RunnerSpec(
					javaImage,
					"javac -encoding UTF-8 *.java && java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 " + mainClass
				);
			}
			case "python" -> new RunnerSpec(
				pythonImage,
				"python " + shellQuote(normalizedEntryFile)
			);
			case "cpp" -> new RunnerSpec(
				cppImage,
				"g++ *.cpp -O2 -std=c++17 -o app && ./app"
			);
			case "javascript", "node", "nodejs" -> new RunnerSpec(
				javascriptImage,
				"node " + shellQuote(normalizedEntryFile)
			);
			default -> throw new IllegalArgumentException("unsupported language: " + language);
		};
	}

	private CreateContainerResponse createContainer(
		DockerClient client,
		Path workspaceDir,
		RunnerSpec runnerSpec
	) {
		return client
			.createContainerCmd(runnerSpec.image())
			.withWorkingDir(this.workspaceDir)
			.withCmd("sh", "-lc", runnerSpec.command())
			.withAttachStdin(true)
			.withAttachStdout(true)
			.withAttachStderr(true)
			.withStdinOpen(true)
			.withStdInOnce(true)
			.withHostConfig(
				HostConfig.newHostConfig()
					.withMemory(memoryLimitBytes)
					.withNetworkMode(networkMode)
					.withReadonlyRootfs(readonlyRootfs)
					.withBinds(new Bind(
						workspaceDir.toAbsolutePath().toString(),
						new Volume(this.workspaceDir)
					))
			)
			.exec();
	}

	private DockerClient getDockerClient() {
		if (dockerClient == null) {
			synchronized (this) {
				if (dockerClient == null) {
					DefaultDockerClientConfig config = DefaultDockerClientConfig
						.createDefaultConfigBuilder()
						.withDockerHost(resolveDockerHost())
						.build();
					ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
						.dockerHost(config.getDockerHost())
						.sslConfig(config.getSSLConfig())
						.build();

					dockerClient = DockerClientImpl
						.getInstance(config, httpClient);
				}
			}
		}
		return dockerClient;
	}

	private String resolveDockerHost() {
		return StringUtils.isBlank(dockerHost)
			? DefaultDockerClientConfig.createDefaultConfigBuilder().build().getDockerHost().toString()
			: dockerHost;
	}

	private void ensureImageAvailable(DockerClient client, String image)
		throws InterruptedException {
		try {
			InspectImageResponse ignored = client.inspectImageCmd(image).exec();
		} catch (NotFoundException exception) {
			client.pullImageCmd(image).exec(new PullImageResultCallback()).awaitCompletion();
		}
	}

	private String findFirstFile(Path workspaceDir, String... extensions) throws IOException {
		try (var paths = Files.walk(workspaceDir)) {
			return paths
				.filter(Files::isRegularFile)
				.map(workspaceDir::relativize)
				.map(Path::toString)
				.map(this::normalizeRelativePath)
				.filter(path -> hasAnyExtension(path, extensions))
				.sorted(Comparator.naturalOrder())
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No source file found for requested language"));
		}
	}

	private String normalizeLanguage(String language) {
		if (language == null || language.trim().isEmpty()) {
			throw new IllegalArgumentException("language is required");
		}
		return language.trim().toLowerCase(Locale.ROOT);
	}

	private String defaultEntryFile(String language) {
		return switch (normalizeLanguage(language)) {
			case "java" -> "Main.java";
			case "python" -> "main.py";
			case "cpp" -> "main.cpp";
			case "javascript", "node", "nodejs" -> "main.js";
			default -> throw new IllegalArgumentException("unsupported language: " + language);
		};
	}

	private String normalizeRelativePath(String path) {
		return path.replace('\\', '/');
	}

	private boolean hasAnyExtension(String path, String... extensions) {
		for (String extension : extensions) {
			if (path.endsWith(extension)) {
				return true;
			}
		}
		return false;
	}

	private String shellQuote(String value) {
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}

	private InputStream toStdinStream(String stdin) {
		if (stdin == null || stdin.isEmpty()) {
			return null;
		}
		return new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));
	}

	private String appendTimeoutMessage(String output, Duration timeout) {
		String suffix = "Execution timed out after " + timeout.toSeconds() + " seconds.";
		if (output == null || output.isBlank()) {
			return suffix;
		}
		return output + System.lineSeparator() + suffix;
	}

	private void removeContainerQuietly(DockerClient client, String containerId) {
		try {
			client.removeContainerCmd(containerId).withForce(true).exec();
		} catch (RuntimeException ignored) {
		}
	}

	private static void closeQuietly(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException ignored) {
		}
	}

	private record RunnerSpec(String image, String command) {}

	public static final class InteractiveContainerSession implements Closeable {
		private final DockerClient client;
		private final String containerId;
		private final PipedOutputStream stdinWriter;
		private final PipedInputStream stdinReader;
		private final StreamingCallback callback;

		private InteractiveContainerSession(
			DockerClient client,
			String containerId,
			PipedOutputStream stdinWriter,
			PipedInputStream stdinReader,
			StreamingCallback callback
		) {
			this.client = client;
			this.containerId = containerId;
			this.stdinWriter = stdinWriter;
			this.stdinReader = stdinReader;
			this.callback = callback;
		}

		public synchronized void writeInput(String input) throws IOException {
			if (input == null || input.isEmpty()) {
				return;
			}
			stdinWriter.write(input.getBytes(StandardCharsets.UTF_8));
			stdinWriter.flush();
		}

		public Integer awaitExitCode(long timeout, TimeUnit unit) throws InterruptedException {
			return client
				.waitContainerCmd(containerId)
				.start()
				.awaitStatusCode(timeout, unit);
		}

		public void kill() {
			try {
				client.killContainerCmd(containerId).exec();
			} catch (RuntimeException ignored) {
			}
		}

		public void awaitLogs(long timeout, TimeUnit unit) throws InterruptedException {
			callback.awaitStreamingCompletion(timeout, unit);
		}

		@Override
		public void close() {
			closeQuietly(stdinWriter);
			closeQuietly(stdinReader);
			try {
				callback.close();
			} catch (IOException ignored) {
			}
			try {
				client.removeContainerCmd(containerId).withForce(true).exec();
			} catch (RuntimeException ignored) {
			}
		}
	}

	private static final class LogCollector extends ResultCallback.Adapter<Frame> {
		private final ByteArrayOutputStream output = new ByteArrayOutputStream();

		@Override
		public void onNext(Frame frame) {
			writeFrame(output, frame);
		}

		public String getOutput() {
			return output.toString(StandardCharsets.UTF_8);
		}

		public void awaitLogs() throws InterruptedException {
			awaitCompletion(1, TimeUnit.SECONDS);
		}
	}

	private static final class StreamingCallback extends ResultCallback.Adapter<Frame> {
		private final Consumer<String> outputConsumer;
		private final CountDownLatch completed = new CountDownLatch(1);

		private StreamingCallback(Consumer<String> outputConsumer) {
			this.outputConsumer = outputConsumer;
		}

		@Override
		public void onNext(Frame frame) {
			String chunk = decodeFrame(frame);
			if (!chunk.isEmpty()) {
				outputConsumer.accept(chunk);
			}
		}

		@Override
		public void onComplete() {
			completed.countDown();
			super.onComplete();
		}

		@Override
		public void onError(Throwable throwable) {
			if (
				throwable != null &&
				throwable.getMessage() != null &&
				!throwable.getMessage().isBlank() &&
				!isIgnorableStreamTermination(throwable.getMessage())
			) {
				outputConsumer.accept(throwable.getMessage());
			}
			completed.countDown();
			super.onError(throwable);
		}

		public void awaitStreamingCompletion(long timeout, TimeUnit unit) throws InterruptedException {
			completed.await(timeout, unit);
		}
	}

	private static String decodeFrame(Frame frame) {
		StreamType streamType = frame.getStreamType();
		if (streamType == StreamType.STDOUT || streamType == StreamType.STDERR || streamType == StreamType.RAW) {
			return new String(frame.getPayload(), StandardCharsets.UTF_8);
		}
		return "";
	}

	private static void writeFrame(ByteArrayOutputStream output, Frame frame) {
		String chunk = decodeFrame(frame);
		if (chunk.isEmpty()) {
			return;
		}
		try {
			output.write(chunk.getBytes(StandardCharsets.UTF_8));
		} catch (IOException ignored) {
		}
	}

	private static boolean isIgnorableStreamTermination(String message) {
		String normalized = message.toLowerCase(Locale.ROOT);
		return normalized.contains("the pipe has been ended") ||
			normalized.contains("broken pipe") ||
			normalized.contains("pipe is being closed");
	}
}
