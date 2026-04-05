package com.mcce.config;

import com.mcce.dto.PresenceDto;
import com.mcce.service.CollaborationRoomService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class CollaborationSessionListener {

	private final CollaborationRoomService collaborationRoomService;
	private final SimpMessagingTemplate messagingTemplate;

	public CollaborationSessionListener(
		CollaborationRoomService collaborationRoomService,
		SimpMessagingTemplate messagingTemplate
	) {
		this.collaborationRoomService = collaborationRoomService;
		this.messagingTemplate = messagingTemplate;
	}

	@EventListener
	public void handleDisconnect(SessionDisconnectEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = accessor.getSessionId();
		if (sessionId == null || sessionId.isBlank()) {
			return;
		}

		PresenceDto presence = collaborationRoomService.disconnect(sessionId);
		if (presence == null) {
			return;
		}

		messagingTemplate.convertAndSend(
			"/topic/rooms/" + presence.getRoomId() + "/presence",
			presence
		);
	}
}
