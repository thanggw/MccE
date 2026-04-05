package com.mcce.dto;

import java.util.ArrayList;
import java.util.List;

public class RoomStateDto {
	private String roomId;
	private String activeFileId;
	private List<CollaborationFileDto> files = new ArrayList<>();
	private List<ParticipantDto> participants = new ArrayList<>();

	public RoomStateDto() {}

	public RoomStateDto(
		String roomId,
		String activeFileId,
		List<CollaborationFileDto> files,
		List<ParticipantDto> participants
	) {
		this.roomId = roomId;
		this.activeFileId = activeFileId;
		this.files = files;
		this.participants = participants;
	}

	public String getRoomId() {
		return roomId;
	}

	public void setRoomId(String roomId) {
		this.roomId = roomId;
	}

	public String getActiveFileId() {
		return activeFileId;
	}

	public void setActiveFileId(String activeFileId) {
		this.activeFileId = activeFileId;
	}

	public List<CollaborationFileDto> getFiles() {
		return files;
	}

	public void setFiles(List<CollaborationFileDto> files) {
		this.files = files;
	}

	public List<ParticipantDto> getParticipants() {
		return participants;
	}

	public void setParticipants(List<ParticipantDto> participants) {
		this.participants = participants;
	}
}
