package com.mcce.dto;

import java.util.ArrayList;
import java.util.List;

public class PresenceDto {
	private String roomId;
	private int participantCount;
	private List<ParticipantDto> participants = new ArrayList<>();

	public PresenceDto() {}

	public PresenceDto(String roomId, int participantCount, List<ParticipantDto> participants) {
		this.roomId = roomId;
		this.participantCount = participantCount;
		this.participants = participants;
	}

	public String getRoomId() {
		return roomId;
	}

	public void setRoomId(String roomId) {
		this.roomId = roomId;
	}

	public int getParticipantCount() {
		return participantCount;
	}

	public void setParticipantCount(int participantCount) {
		this.participantCount = participantCount;
	}

	public List<ParticipantDto> getParticipants() {
		return participants;
	}

	public void setParticipants(List<ParticipantDto> participants) {
		this.participants = participants;
	}
}
