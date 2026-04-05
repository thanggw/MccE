package com.mcce.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Exposes the WebSocket/STOMP session id in the CONNECTED frame so the browser can
 * correlate echoed collaboration messages with the local session.
 */
@Component
public class CollabStompSessionOutboundInterceptor implements ChannelInterceptor {

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || !StompCommand.CONNECTED.equals(accessor.getCommand())) {
			return message;
		}
		String sessionId = accessor.getSessionId();
		if (sessionId == null || sessionId.isBlank()) {
			return message;
		}
		accessor.setNativeHeader("session", sessionId);
		return message;
	}
}
