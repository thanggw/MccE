package com.mcce.dto;

import java.util.ArrayList;
import java.util.List;

public class CollaborationSyncMessage {
	private String roomId;
	private String activeFileId;
	private List<CollaborationFileDto> files = new ArrayList<>();

	public CollaborationSyncMessage() {}

	public CollaborationSyncMessage(
		String roomId,
		String activeFileId,
		List<CollaborationFileDto> files
	) {
		this.roomId = roomId;
		this.activeFileId = activeFileId;
		this.files = files;
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
}
