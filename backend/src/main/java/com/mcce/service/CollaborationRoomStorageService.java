package com.mcce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcce.dto.ParticipantDto;
import com.mcce.dto.RoomStateDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CollaborationRoomStorageService {

	private static final String ROOM_KEY_PREFIX = "collab:room:";
	private static final String ROOM_PARTICIPANTS_KEY_PREFIX = "collab:room:participants:";
	private static final Duration ROOM_TTL = Duration.ofHours(24);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public CollaborationRoomStorageService(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	public Optional<RoomStateDto> findRoom(String roomId) {
		String payload = redisTemplate.opsForValue().get(buildRoomKey(roomId));
		if (payload == null || payload.isBlank()) {
			return Optional.empty();
		}

		try {
			return Optional.of(objectMapper.readValue(payload, RoomStateDto.class));
		} catch (JsonProcessingException exception) {
			throw new UncheckedIOException(
				"Failed to deserialize room state for roomId=" + roomId,
				exception
			);
		}
	}

	public void saveRoom(RoomStateDto roomState) {
		try {
			redisTemplate
				.opsForValue()
				.set(
					buildRoomKey(roomState.getRoomId()),
					objectMapper.writeValueAsString(roomState),
					ROOM_TTL
				);
		} catch (JsonProcessingException exception) {
			throw new UncheckedIOException(
				"Failed to serialize room state for roomId=" + roomState.getRoomId(),
				exception
			);
		}
	}

	public void addParticipant(String roomId, ParticipantDto participant) {
		String participantsKey = buildParticipantsKey(roomId);
		String payload = writeParticipant(participant);
		redisTemplate.opsForSet().add(participantsKey, payload);
		redisTemplate.expire(participantsKey, ROOM_TTL);
	}

	public void removeParticipant(String roomId, String sessionId) {
		String participantsKey = buildParticipantsKey(roomId);
		for (String payload : getParticipantPayloads(roomId)) {
			ParticipantDto participant = readParticipant(payload);
			if (sessionId.equals(participant.getSessionId())) {
				redisTemplate.opsForSet().remove(participantsKey, payload);
			}
		}

		Long size = redisTemplate.opsForSet().size(participantsKey);
		if (size == null || size == 0) {
			redisTemplate.delete(participantsKey);
			return;
		}

		redisTemplate.expire(participantsKey, ROOM_TTL);
	}

	public List<ParticipantDto> getParticipants(String roomId) {
		return getParticipantPayloads(roomId)
			.stream()
			.map(this::readParticipant)
			.sorted(Comparator.comparing(ParticipantDto::getNickname, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private List<String> getParticipantPayloads(String roomId) {
		var members = redisTemplate.opsForSet().members(buildParticipantsKey(roomId));
		return members == null ? Collections.emptyList() : members.stream().toList();
	}

	private String buildRoomKey(String roomId) {
		return ROOM_KEY_PREFIX + roomId;
	}

	private String buildParticipantsKey(String roomId) {
		return ROOM_PARTICIPANTS_KEY_PREFIX + roomId;
	}

	private ParticipantDto readParticipant(String payload) {
		try {
			return objectMapper.readValue(payload, ParticipantDto.class);
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to deserialize participant", exception);
		}
	}

	private String writeParticipant(ParticipantDto participant) {
		try {
			return objectMapper.writeValueAsString(participant);
		} catch (JsonProcessingException exception) {
			throw new UncheckedIOException("Failed to serialize participant", exception);
		}
	}
}
