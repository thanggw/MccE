package com.mcce.controller;

import com.mcce.dto.RoomStateDto;
import com.mcce.service.CollaborationRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

	private final CollaborationRoomService collaborationRoomService;

	public RoomController(CollaborationRoomService collaborationRoomService) {
		this.collaborationRoomService = collaborationRoomService;
	}

	@GetMapping("/{roomId}")
	public ResponseEntity<RoomStateDto> getRoomSnapshot(@PathVariable String roomId) {
		return ResponseEntity.ok(collaborationRoomService.getSnapshot(roomId));
	}
}
