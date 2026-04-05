package com.mcce.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcce.service.InteractiveExecutionService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ExecutionTerminalWebSocketHandler extends TextWebSocketHandler {

	private final InteractiveExecutionService interactiveExecutionService;
	private final ObjectMapper objectMapper;

	public ExecutionTerminalWebSocketHandler(
		InteractiveExecutionService interactiveExecutionService,
		ObjectMapper objectMapper
	) {
		this.interactiveExecutionService = interactiveExecutionService;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		String sessionId = extractSessionId(session);
		interactiveExecutionService.attachListener(sessionId, event -> sendEvent(session, event));
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message)
		throws IOException {
		String sessionId = extractSessionId(session);
		ClientTerminalMessage clientMessage =
			objectMapper.readValue(message.getPayload(), ClientTerminalMessage.class);

		if ("input".equals(clientMessage.type()) && clientMessage.data() != null) {
			interactiveExecutionService.writeInput(sessionId, clientMessage.data());
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		interactiveExecutionService.detachListener(extractSessionId(session));
	}

	private void sendEvent(
		WebSocketSession webSocketSession,
		InteractiveExecutionService.TerminalEvent event
	) {
		if (!webSocketSession.isOpen()) {
			return;
		}
		try {
			Map<String, Object> payloadMap = new LinkedHashMap<>();
			payloadMap.put("type", event.getType());
			payloadMap.put("data", event.getData() == null ? "" : event.getData());
			payloadMap.put("exitCode", event.getExitCode());
			String payload = objectMapper.writeValueAsString(payloadMap);
			webSocketSession.sendMessage(new TextMessage(payload));
		} catch (IOException ignored) {
		}
	}

	private String extractSessionId(WebSocketSession session) {
		Object value = session.getAttributes().get("sessionId");
		if (value == null) {
			throw new IllegalArgumentException("Missing sessionId");
		}
		return value.toString();
	}

	private record ClientTerminalMessage(String type, String data) {}
}
