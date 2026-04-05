package com.mcce.controller;

import com.mcce.dto.CollaborationCursorMessage;
import com.mcce.dto.CollaborationDeltaMessage;
import com.mcce.dto.CollaborationJoinMessage;
import com.mcce.dto.ParticipantDto;
import com.mcce.dto.CollaborationWorkspaceEventMessage;
import com.mcce.service.CollaborationRoomService;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class CollaborationController {

	private final CollaborationRoomService collaborationRoomService;
	private final SimpMessagingTemplate messagingTemplate;

	public CollaborationController(
		CollaborationRoomService collaborationRoomService,
		SimpMessagingTemplate messagingTemplate
	) {
		this.collaborationRoomService = collaborationRoomService;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/collab.join")
	public void joinRoom(
		CollaborationJoinMessage message,
		@Header("simpSessionId") String sessionId
	) {
		collaborationRoomService.joinRoom(
			message.getRoomId(),
			sessionId,
			message.getNickname()
		);
		broadcastPresence(message.getRoomId());
	}

	@MessageMapping("/collab.delta")
	public void applyDelta(
		CollaborationDeltaMessage message,
		@Header("simpSessionId") String sessionId
	) {
		CollaborationDeltaMessage applied = collaborationRoomService.applyDelta(sessionId, message);
		applied.setSourceSessionId(sessionId);
		messagingTemplate.convertAndSend(
			"/topic/rooms/" + applied.getRoomId() + "/delta",
			applied
		);
	}

	@MessageMapping("/collab.workspace")
	public void applyWorkspaceEvent(
		CollaborationWorkspaceEventMessage message,
		@Header("simpSessionId") String sessionId
	) {
		CollaborationWorkspaceEventMessage applied =
			collaborationRoomService.applyWorkspaceEvent(sessionId, message);
		applied.setSourceSessionId(sessionId);
		messagingTemplate.convertAndSend(
			"/topic/rooms/" + applied.getRoomId() + "/workspace",
			applied
		);
	}

	@MessageMapping("/collab.cursor")
	public void broadcastCursor(
		CollaborationCursorMessage message,
		@Header("simpSessionId") String sessionId
	) {
		ParticipantDto participant = collaborationRoomService.getParticipant(sessionId);
		String sessionRoomId = collaborationRoomService.getRoomIdForSession(sessionId);
		if (
			participant == null ||
			sessionRoomId == null ||
			message.getRoomId() == null ||
			message.getRoomId().trim().isEmpty() ||
			!sessionRoomId.equals(message.getRoomId())
		) {
			return;
		}

		message.setSourceSessionId(sessionId);
		message.setNickname(participant.getNickname());
		messagingTemplate.convertAndSend(
			"/topic/rooms/" + message.getRoomId() + "/cursor",
			message
		);
	}

	private void broadcastPresence(String roomId) {
		messagingTemplate.convertAndSend(
			"/topic/rooms/" + roomId + "/presence",
			collaborationRoomService.getPresence(roomId)
		);
	}
}
