package com.mcce.dto;

public class ParticipantDto {
	private String sessionId;
	private String nickname;

	public ParticipantDto() {}

	public ParticipantDto(String sessionId, String nickname) {
		this.sessionId = sessionId;
		this.nickname = nickname;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}
}
