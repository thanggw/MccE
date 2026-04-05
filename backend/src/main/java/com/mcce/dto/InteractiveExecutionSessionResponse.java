package com.mcce.dto;

public class InteractiveExecutionSessionResponse {
	private String sessionId;

	public InteractiveExecutionSessionResponse() {}

	public InteractiveExecutionSessionResponse(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}
}
