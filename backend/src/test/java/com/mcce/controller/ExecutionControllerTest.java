package com.mcce.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mcce.dto.ExecutionResult;
import com.mcce.service.ExecutionService;
import com.mcce.service.InteractiveExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

	@Autowired
	private MockMvc mockMvc;


	@MockBean
	private ExecutionService executionService;

	@MockBean
	private InteractiveExecutionService interactiveExecutionService;

	@Test
	void executesCodeWithConfiguredExecutionService() throws Exception {
		when(executionService.execute(any(), any()))
			.thenReturn(new ExecutionResult("python", "Hello\n", "", 0, true));

		mockMvc.perform(post("/api/executions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "python",
					  "code": "print('Hello')"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.language").value("python"))
			.andExpect(jsonPath("$.output").value("Hello\n"))
			.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void startsInteractiveExecutionSession() throws Exception {
		when(interactiveExecutionService.startSession(any(), any()))
			.thenReturn("session-123");

		mockMvc.perform(post("/api/executions/interactive")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "java",
					  "entryFile": "Main.java",
					  "files": [
					    { "name": "Main.java", "content": "public class Main {}" }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sessionId").value("session-123"));
	}
}
