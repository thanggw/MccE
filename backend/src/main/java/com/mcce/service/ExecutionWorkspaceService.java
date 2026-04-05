package com.mcce.service;

import com.mcce.dto.ExecutionRequest;
import com.mcce.dto.ExecutionRequest.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExecutionWorkspaceService {

	public Path prepareWorkspace(ExecutionRequest request) throws IOException {
		String sessionPrefix = "mcce-exec-";
		Path tmpRoot = Paths.get(System.getProperty("java.io.tmpdir"));
		Path workspaceDir =
			Files.createTempDirectory(tmpRoot, sessionPrefix + UUID.randomUUID() + "-");

		List<File> files = request.getFiles();
		if (files == null || files.isEmpty()) {
			return workspaceDir;
		}

		Path normalizedWorkspace = workspaceDir.toAbsolutePath().normalize();
		for (File file : files) {
			writeOneFile(normalizedWorkspace, file);
		}

		return normalizedWorkspace;
	}

	public void cleanupWorkspace(Path workspaceDir) throws IOException {
		if (workspaceDir == null || !Files.exists(workspaceDir)) {
			return;
		}

		try (var paths = Files.walk(workspaceDir)) {
			paths
				.sorted(Comparator.reverseOrder())
				.forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException ignored) {
					}
				});
		}
	}

	private void writeOneFile(Path workspaceDir, File file) throws IOException {
		Objects.requireNonNull(file, "file");
		String relativePath = file.getResolvedPath();
		if (relativePath == null || relativePath.trim().isEmpty()) {
			throw new IllegalArgumentException("file.path or file.name is required");
		}

		Path relative = Paths.get(relativePath).normalize();
		if (relative.isAbsolute()) {
			throw new IllegalArgumentException("file path must be relative");
		}

		for (Path part : relative) {
			if ("..".equals(part.toString())) {
				throw new IllegalArgumentException("file path must not contain '..'");
			}
		}

		Path target = workspaceDir.resolve(relative).toAbsolutePath().normalize();
		if (!target.startsWith(workspaceDir)) {
			throw new IllegalArgumentException("file path escapes workspace directory");
		}

		Path parent = target.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		String content = file.getContent();
		if (content == null) {
			content = "";
		}

		Files.writeString(target, content, StandardCharsets.UTF_8);
	}
}
