package com.mcce.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mcce.dto.ExecutionRequest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionWorkspaceServiceTest {

	private final ExecutionWorkspaceService workspaceService = new ExecutionWorkspaceService();

	@Test
	void rejectsPathTraversal() {
		ExecutionRequest.File file = new ExecutionRequest.File("../evil.java", "../evil.java", "boom");
		ExecutionRequest request = new ExecutionRequest("java", "Main.java", null, List.of(file));

		assertThrows(IllegalArgumentException.class, () -> workspaceService.prepareWorkspace(request));
	}

	@Test
	void acceptsNameWhenPathIsMissing() throws IOException {
		ExecutionRequest.File file = new ExecutionRequest.File("Main.java", null, "class Main {}");
		ExecutionRequest request = new ExecutionRequest("java", "Main.java", null, List.of(file));

		var workspace = workspaceService.prepareWorkspace(request);
		workspaceService.cleanupWorkspace(workspace);
	}
}
