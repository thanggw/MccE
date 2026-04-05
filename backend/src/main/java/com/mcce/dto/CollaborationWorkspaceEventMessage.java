package com.mcce.dto;

public class CollaborationWorkspaceEventMessage {
	private String roomId;
	private String type;
	private String sourceSessionId;
	private String clientInstanceId;
	private CollaborationFileDto file;
	private String fileId;
	private String name;
	private String language;

	public CollaborationWorkspaceEventMessage() {}

	public String getRoomId() {
		return roomId;
	}

	public void setRoomId(String roomId) {
		this.roomId = roomId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSourceSessionId() {
		return sourceSessionId;
	}

	public void setSourceSessionId(String sourceSessionId) {
		this.sourceSessionId = sourceSessionId;
	}

	public String getClientInstanceId() {
		return clientInstanceId;
	}

	public void setClientInstanceId(String clientInstanceId) {
		this.clientInstanceId = clientInstanceId;
	}

	public CollaborationFileDto getFile() {
		return file;
	}

	public void setFile(CollaborationFileDto file) {
		this.file = file;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}
}
