package com.mcce.dto;

public class CollaborationCursorMessage {
	private String roomId;
	private String fileId;
	private String sourceSessionId;
	private String clientInstanceId;
	private String nickname;
	private Integer lineNumber;
	private Integer column;
	private Boolean visible;

	public CollaborationCursorMessage() {}

	public String getRoomId() {
		return roomId;
	}

	public void setRoomId(String roomId) {
		this.roomId = roomId;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
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

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public Integer getLineNumber() {
		return lineNumber;
	}

	public void setLineNumber(Integer lineNumber) {
		this.lineNumber = lineNumber;
	}

	public Integer getColumn() {
		return column;
	}

	public void setColumn(Integer column) {
		this.column = column;
	}

	public Boolean getVisible() {
		return visible;
	}

	public void setVisible(Boolean visible) {
		this.visible = visible;
	}
}
