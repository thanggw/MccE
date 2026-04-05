package com.mcce.config;

import com.mcce.websocket.ExecutionTerminalWebSocketHandler;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final String[] allowedOriginPatterns;
	private final ExecutionTerminalWebSocketHandler executionTerminalWebSocketHandler;

	public WebSocketConfig(
		@Value("${mcce.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String[] allowedOriginPatterns,
		ExecutionTerminalWebSocketHandler executionTerminalWebSocketHandler
	) {
		this.allowedOriginPatterns = allowedOriginPatterns;
		this.executionTerminalWebSocketHandler = executionTerminalWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry
			.addHandler(executionTerminalWebSocketHandler, "/ws/executions/{sessionId}")
			.addInterceptors(new SessionIdHandshakeInterceptor())
			.setAllowedOriginPatterns(allowedOriginPatterns);
	}

	private static class SessionIdHandshakeInterceptor implements HandshakeInterceptor {
		@Override
		public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes
		) {
			String path = request.getURI().getPath();
			int slash = path.lastIndexOf('/');
			if (slash < 0 || slash == path.length() - 1) {
				return false;
			}
			attributes.put("sessionId", path.substring(slash + 1));
			return true;
		}

		@Override
		public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception
		) {
		}
	}
}
