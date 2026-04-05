package com.mcce.controller;

import com.mcce.dto.CodeExecutionRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mcce.dto.ExecutionRequest;
import com.mcce.dto.ExecutionResult;
import com.mcce.dto.InteractiveExecutionSessionResponse;
import com.mcce.service.ExecutionService;
import com.mcce.service.InteractiveExecutionService;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

	private final ExecutionService executionService;
	private final InteractiveExecutionService interactiveExecutionService;

	public ExecutionController(
		ExecutionService executionService,
		InteractiveExecutionService interactiveExecutionService
	) {
		this.executionService = executionService;
		this.interactiveExecutionService = interactiveExecutionService;
	}

	@PostMapping
	public ResponseEntity<ExecutionResult> execute(
		@RequestBody CodeExecutionRequest request
	) throws IOException, InterruptedException {
		return ResponseEntity.ok(executionService.execute(request.getCode(), request.getLanguage()));
	}

	@PostMapping("/interactive")
	public ResponseEntity<InteractiveExecutionSessionResponse> startInteractiveSession(
		@RequestBody ExecutionRequest request
	) throws IOException, InterruptedException {
		String sessionId = interactiveExecutionService.startSession(request, event -> {});
		return ResponseEntity.ok(new InteractiveExecutionSessionResponse(sessionId));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleValidation(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(Map.of(
			"status", "BAD_REQUEST",
			"message", exception.getMessage()
		));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException exception) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
			"status", "UNAVAILABLE",
			"message", exception.getMessage()
		));
	}

	@ExceptionHandler(IOException.class)
	public ResponseEntity<Map<String, String>> handleIo(IOException exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
			"status", "IO_ERROR",
			"message", exception.getMessage()
		));
	}

	@ExceptionHandler(InterruptedException.class)
	public ResponseEntity<Map<String, String>> handleInterrupted(InterruptedException exception) {
		Thread.currentThread().interrupt();
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
			"status", "INTERRUPTED",
			"message", exception.getMessage() == null ? "Execution interrupted" : exception.getMessage()
		));
	}
}
