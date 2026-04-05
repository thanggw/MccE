package com.mcce.service;

import com.mcce.dto.ExecutionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class InteractiveExecutionService {

	private static final long TIMEOUT_SECONDS = 10L;

	private final ExecutionWorkspaceService workspaceService;
	private final ObjectProvider<DockerService> dockerServiceProvider;
	private final ConcurrentMap<String, InteractiveSession> sessions = new ConcurrentHashMap<>();

	public InteractiveExecutionService(
		ExecutionWorkspaceService workspaceService,
		ObjectProvider<DockerService> dockerServiceProvider
	) {
		this.workspaceService = workspaceService;
		this.dockerServiceProvider = dockerServiceProvider;
	}

	public String startSession(ExecutionRequest request, Consumer<TerminalEvent> listener)
		throws IOException, InterruptedException {
		validateRequest(request);
		DockerService dockerService = requireDockerService();

		Path workspaceDir = workspaceService.prepareWorkspace(request);
		String entryFile = resolveEntryFile(request);
		DockerService.InteractiveContainerSession containerSession = null;

		try {
			String sessionId = UUID.randomUUID().toString();
			InteractiveSession session = new InteractiveSession(sessionId, workspaceDir, listener);

			containerSession = dockerService.startInteractiveSession(
				workspaceDir,
				request.getLanguage(),
				entryFile,
				session::appendOutput
			);
			session.setContainerSession(containerSession);
			sessions.put(sessionId, session);
			startLifecycleWatcher(session);

			if (request.getStdin() != null && !request.getStdin().isEmpty()) {
				writeInput(sessionId, request.getStdin());
			}

			return sessionId;
		} catch (IOException | InterruptedException | RuntimeException exception) {
			if (containerSession != null) {
				containerSession.close();
			}
			workspaceService.cleanupWorkspace(workspaceDir);
			throw exception;
		}
	}

	private DockerService requireDockerService() {
		DockerService dockerService = dockerServiceProvider.getIfAvailable();
		if (dockerService == null) {
			throw new IllegalStateException("Interactive execution is only available when EXECUTION_STRATEGY=DOCKER");
		}
		return dockerService;
	}

	public void writeInput(String sessionId, String input) throws IOException {
		InteractiveSession session = getSession(sessionId);
		if (input == null || input.isEmpty()) {
			return;
		}

		DockerService.InteractiveContainerSession containerSession = session.containerSession();
		if (containerSession == null) {
			throw new IllegalStateException("Interactive container is not ready");
		}

		containerSession.writeInput(input);
	}

	public void attachListener(String sessionId, Consumer<TerminalEvent> listener) {
		InteractiveSession session = getSession(sessionId);
		session.setListener(listener);
		String snapshot = session.snapshotBuffer();
		if (!snapshot.isEmpty()) {
			listener.accept(TerminalEvent.output(snapshot));
		}
		if (session.completed()) {
			listener.accept(TerminalEvent.exit(session.exitCode()));
		}
	}

	public void detachListener(String sessionId) {
		InteractiveSession session = sessions.get(sessionId);
		if (session != null) {
			session.setListener(null);
			if (session.completed()) {
				cleanupSession(sessionId);
			}
		}
	}

	private void startLifecycleWatcher(InteractiveSession session) {
		Thread watcher = new Thread(() -> {
			Integer exitCode = null;
			try {
				DockerService.InteractiveContainerSession containerSession = session.containerSession();
				exitCode = containerSession.awaitExitCode(TIMEOUT_SECONDS, TimeUnit.SECONDS);

				if (exitCode == null) {
					containerSession.kill();
					session.appendOutput(System.lineSeparator() + "Execution timed out after 10 seconds.");
				}

				containerSession.awaitLogs(1, TimeUnit.SECONDS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				session.publish(TerminalEvent.error("Execution interrupted"));
			} finally {
				session.complete(exitCode);
				if (!session.hasListener()) {
					cleanupSession(session.sessionId());
				}
			}
		});
		watcher.setDaemon(true);
		watcher.start();
	}

	private void cleanupSession(String sessionId) {
		InteractiveSession session = sessions.remove(sessionId);
		if (session == null) {
			return;
		}
		try {
			if (session.containerSession() != null) {
				session.containerSession().close();
			}
		} finally {
			try {
				workspaceService.cleanupWorkspace(session.workspaceDir());
			} catch (IOException ignored) {
			}
		}
	}

	private InteractiveSession getSession(String sessionId) {
		InteractiveSession session = sessions.get(sessionId);
		if (session == null) {
			throw new IllegalArgumentException("Unknown sessionId: " + sessionId);
		}
		return session;
	}

	private void validateRequest(ExecutionRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.getLanguage() == null || request.getLanguage().trim().isEmpty()) {
			throw new IllegalArgumentException("language is required");
		}
		if (request.getFiles() == null || request.getFiles().isEmpty()) {
			throw new IllegalArgumentException("files must not be empty");
		}
	}

	private String resolveEntryFile(ExecutionRequest request) {
		if (request.getEntryFile() != null && !request.getEntryFile().trim().isEmpty()) {
			return request.getEntryFile().trim();
		}

		ExecutionRequest.File firstFile = request.getFiles().get(0);
		String resolvedPath = firstFile.getResolvedPath();
		if (resolvedPath == null || resolvedPath.trim().isEmpty()) {
			throw new IllegalArgumentException("entryFile is required");
		}
		return resolvedPath.trim();
	}

	public static final class TerminalEvent {
		private final String type;
		private final String data;
		private final Integer exitCode;

		private TerminalEvent(String type, String data, Integer exitCode) {
			this.type = type;
			this.data = data;
			this.exitCode = exitCode;
		}

		public static TerminalEvent output(String data) {
			return new TerminalEvent("output", data, null);
		}

		public static TerminalEvent error(String data) {
			return new TerminalEvent("error", data, null);
		}

		public static TerminalEvent exit(Integer exitCode) {
			return new TerminalEvent("exit", null, exitCode);
		}

		public String getType() {
			return type;
		}

		public String getData() {
			return data;
		}

		public Integer getExitCode() {
			return exitCode;
		}
	}

	private static final class InteractiveSession {
		private final String sessionId;
		private final Path workspaceDir;
		private final StringBuilder terminalBuffer = new StringBuilder();
		private volatile Consumer<TerminalEvent> listener;
		private volatile Integer exitCode;
		private volatile boolean completed;
		private volatile DockerService.InteractiveContainerSession containerSession;

		private InteractiveSession(
			String sessionId,
			Path workspaceDir,
			Consumer<TerminalEvent> listener
		) {
			this.sessionId = sessionId;
			this.workspaceDir = workspaceDir;
			this.listener = listener;
		}

		private void appendOutput(String chunk) {
			synchronized (terminalBuffer) {
				terminalBuffer.append(chunk);
			}
			publish(TerminalEvent.output(chunk));
		}

		private String snapshotBuffer() {
			synchronized (terminalBuffer) {
				return terminalBuffer.toString();
			}
		}

		private void publish(TerminalEvent event) {
			Consumer<TerminalEvent> current = listener;
			if (current != null) {
				current.accept(event);
			}
		}

		private void complete(Integer exitCode) {
			this.exitCode = exitCode;
			this.completed = true;
			publish(TerminalEvent.exit(exitCode));
		}

		private String sessionId() {
			return sessionId;
		}

		private Path workspaceDir() {
			return workspaceDir;
		}

		private boolean completed() {
			return completed;
		}

		private Integer exitCode() {
			return exitCode;
		}

		private void setListener(Consumer<TerminalEvent> listener) {
			this.listener = listener;
		}

		private boolean hasListener() {
			return listener != null;
		}

		private DockerService.InteractiveContainerSession containerSession() {
			return containerSession;
		}

		private void setContainerSession(DockerService.InteractiveContainerSession containerSession) {
			this.containerSession = containerSession;
		}
	}
}
