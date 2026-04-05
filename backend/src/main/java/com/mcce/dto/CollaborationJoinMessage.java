package com.mcce.dto;

public class CollaborationJoinMessage {
	private String roomId;
	private String nickname;

	public CollaborationJoinMessage() {}

	public CollaborationJoinMessage(String roomId, String nickname) {
		this.roomId = roomId;
		this.nickname = nickname;
	}

	public String getRoomId() {
		return roomId;
	}

	public void setRoomId(String roomId) {
		this.roomId = roomId;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}
}
