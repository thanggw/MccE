package com.mcce.dto;

import java.util.ArrayList;
import java.util.List;

public class CollaborationDeltaMessage {
	private String roomId;
	private String fileId;
	private String sourceSessionId;
	private String clientInstanceId;
	private Integer baseVersion;
	private Integer serverVersion;
	private Boolean accepted;
	private String reason;
	private List<CollaborationEditorChangeDto> changes = new ArrayList<>();

	public CollaborationDeltaMessage() {}

	public CollaborationDeltaMessage(
		String roomId,
		String fileId,
		List<CollaborationEditorChangeDto> changes
	) {
		this.roomId = roomId;
		this.fileId = fileId;
		this.changes = changes;
	}

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

	public Integer getBaseVersion() {
		return baseVersion;
	}

	public void setBaseVersion(Integer baseVersion) {
		this.baseVersion = baseVersion;
	}

	public Integer getServerVersion() {
		return serverVersion;
	}

	public void setServerVersion(Integer serverVersion) {
		this.serverVersion = serverVersion;
	}

	public Boolean getAccepted() {
		return accepted;
	}

	public void setAccepted(Boolean accepted) {
		this.accepted = accepted;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public List<CollaborationEditorChangeDto> getChanges() {
		return changes;
	}

	public void setChanges(List<CollaborationEditorChangeDto> changes) {
		this.changes = changes;
	}
}
