package com.mcce.service;

import com.mcce.dto.CollaborationDeltaMessage;
import com.mcce.dto.CollaborationEditorChangeDto;
import com.mcce.dto.CollaborationFileDto;
import com.mcce.dto.CollaborationWorkspaceEventMessage;
import com.mcce.dto.ParticipantDto;
import com.mcce.dto.PresenceDto;
import com.mcce.dto.RoomStateDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class CollaborationRoomService {

	private final CollaborationRoomStorageService roomStorageService;
	private final ConcurrentMap<String, RoomState> rooms = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> sessionToRoom = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, ParticipantDto> sessionParticipants = new ConcurrentHashMap<>();

	public CollaborationRoomService(CollaborationRoomStorageService roomStorageService) {
		this.roomStorageService = roomStorageService;
	}

	public RoomStateDto joinRoom(String roomId, String sessionId, String nickname) {
		validateRoomId(roomId);
		validateSessionId(sessionId);

		RoomState room = getOrLoadRoom(roomId);
		String normalizedNickname = normalizeNickname(nickname);
		ParticipantDto participant = new ParticipantDto(sessionId, normalizedNickname);
		sessionParticipants.put(sessionId, participant);
		roomStorageService.addParticipant(roomId, participant);
		sessionToRoom.put(sessionId, roomId);
		return buildSnapshot(room);
	}

	public PresenceDto getPresence(String roomId) {
		validateRoomId(roomId);
		List<ParticipantDto> participants = roomStorageService.getParticipants(roomId);
		return new PresenceDto(roomId, participants.size(), participants);
	}

	public CollaborationDeltaMessage applyDelta(
		String sessionId,
		CollaborationDeltaMessage message
	) {
		Objects.requireNonNull(message, "message");
		validateSessionId(sessionId);
		validateRoomId(message.getRoomId());
		if (message.getFileId() == null || message.getFileId().trim().isEmpty()) {
			throw new IllegalArgumentException("fileId is required");
		}

		RoomState room = getOrLoadRoom(message.getRoomId());
		CollaborationDeltaMessage applied = room.applyDelta(message);
		if (Boolean.TRUE.equals(applied.getAccepted())) {
			persistRoom(room);
		}
		return applied;
	}

	public CollaborationWorkspaceEventMessage applyWorkspaceEvent(
		String sessionId,
		CollaborationWorkspaceEventMessage message
	) {
		Objects.requireNonNull(message, "message");
		validateSessionId(sessionId);
		validateRoomId(message.getRoomId());
		if (message.getType() == null || message.getType().trim().isEmpty()) {
			throw new IllegalArgumentException("workspace event type is required");
		}

		RoomState room = getOrLoadRoom(message.getRoomId());
		room.applyWorkspaceEvent(message);
		persistRoom(room);
		return message;
	}

	public RoomStateDto getSnapshot(String roomId) {
		validateRoomId(roomId);
		return buildSnapshot(getOrLoadRoom(roomId));
	}

	public PresenceDto disconnect(String sessionId) {
		validateSessionId(sessionId);
		String roomId = sessionToRoom.remove(sessionId);
		sessionParticipants.remove(sessionId);
		if (roomId == null) {
			return null;
		}

		RoomState room = rooms.get(roomId);
		roomStorageService.removeParticipant(roomId, sessionId);
		PresenceDto presence = getPresence(roomId);
		if (room != null && presence.getParticipantCount() == 0) {
			rooms.remove(roomId, room);
		}
		return presence;
	}

	public ParticipantDto getParticipant(String sessionId) {
		validateSessionId(sessionId);
		ParticipantDto participant = sessionParticipants.get(sessionId);
		return participant == null
			? null
			: new ParticipantDto(participant.getSessionId(), participant.getNickname());
	}

	public String getRoomIdForSession(String sessionId) {
		validateSessionId(sessionId);
		return sessionToRoom.get(sessionId);
	}

	private RoomState getOrLoadRoom(String roomId) {
		return rooms.computeIfAbsent(roomId, this::loadRoomState);
	}

	private RoomState loadRoomState(String roomId) {
		return roomStorageService
			.findRoom(roomId)
			.map(RoomState::fromSnapshot)
			.orElseGet(() -> new RoomState(roomId));
	}

	private void persistRoom(RoomState room) {
		roomStorageService.saveRoom(room.snapshotForStorage());
	}

	private RoomStateDto buildSnapshot(RoomState room) {
		RoomStateDto snapshot = room.snapshot();
		snapshot.setParticipants(roomStorageService.getParticipants(room.getRoomId()));
		return snapshot;
	}

	private void validateRoomId(String roomId) {
		if (roomId == null || roomId.trim().isEmpty()) {
			throw new IllegalArgumentException("roomId is required");
		}
	}

	private void validateSessionId(String sessionId) {
		if (sessionId == null || sessionId.trim().isEmpty()) {
			throw new IllegalArgumentException("sessionId is required");
		}
	}

	private String normalizeNickname(String nickname) {
		if (nickname == null || nickname.trim().isEmpty()) {
			return "Anonymous";
		}
		return nickname.trim();
	}

	private static final class RoomState {
		private final String roomId;
		private final List<CollaborationFileDto> files = new ArrayList<>();
		private String activeFileId;

		private RoomState(String roomId) {
			this.roomId = roomId;
		}

		private String getRoomId() {
			return roomId;
		}

		private static RoomState fromSnapshot(RoomStateDto snapshot) {
			RoomState roomState = new RoomState(snapshot.getRoomId());
			roomState.activeFileId = snapshot.getActiveFileId();
			if (snapshot.getFiles() != null) {
				for (CollaborationFileDto file : snapshot.getFiles()) {
					if (file != null) {
						roomState.files.add(roomState.copyFile(file));
					}
				}
			}
			return roomState;
		}

		private synchronized CollaborationDeltaMessage applyDelta(CollaborationDeltaMessage message) {
			CollaborationFileDto file = findFile(message.getFileId());
			if (file == null) {
				message.setAccepted(false);
				message.setReason("FILE_NOT_FOUND");
				return message;
			}

			if (message.getBaseVersion() == null) {
				message.setAccepted(false);
				message.setServerVersion(file.getVersion());
				message.setReason("BASE_VERSION_REQUIRED");
				return message;
			}

			if (!message.getBaseVersion().equals(file.getVersion())) {
				message.setAccepted(false);
				message.setServerVersion(file.getVersion());
				message.setReason("RESYNC_REQUIRED");
				return message;
			}

			String content = file.getContent() == null ? "" : file.getContent();
			List<CollaborationEditorChangeDto> changes = message.getChanges() == null
				? List.of()
				: message.getChanges()
					.stream()
					.filter(Objects::nonNull)
					.sorted(Comparator.comparingInt(CollaborationEditorChangeDto::getRangeOffset).reversed())
					.toList();

			for (CollaborationEditorChangeDto change : changes) {
				int start = Math.max(0, Math.min(change.getRangeOffset(), content.length()));
				int end = Math.max(start, Math.min(start + change.getRangeLength(), content.length()));
				String replacement = change.getText() == null ? "" : change.getText();
				content = content.substring(0, start) + replacement + content.substring(end);
			}

			file.setContent(content);
			file.setVersion(file.getVersion() + 1);
			message.setAccepted(true);
			message.setServerVersion(file.getVersion());
			message.setReason("APPLIED");
			return message;
		}

		private synchronized void applyWorkspaceEvent(CollaborationWorkspaceEventMessage message) {
			String type = message.getType().trim().toUpperCase(Locale.ROOT);
			switch (type) {
				case "CREATE_FILE" -> {
					CollaborationFileDto file = message.getFile();
					if (file == null || file.getId() == null || file.getId().trim().isEmpty()) {
						throw new IllegalArgumentException("file is required for CREATE_FILE");
					}
					file.setVersion(Math.max(0, file.getVersion()));
					files.add(copyFile(file));
				}
				case "DELETE_FILE" -> {
					String fileId = requireFileId(message);
					files.removeIf(file -> fileId.equals(file.getId()));
					if (fileId.equals(activeFileId)) {
						activeFileId = files.isEmpty() ? null : files.get(0).getId();
					}
				}
				case "RENAME_FILE" -> {
					CollaborationFileDto file = requireExistingFile(message);
					file.setName(message.getName());
				}
				case "UPDATE_LANGUAGE" -> {
					CollaborationFileDto file = requireExistingFile(message);
					file.setLanguage(message.getLanguage());
				}
				case "SET_ACTIVE_FILE" -> activeFileId = requireFileId(message);
				default -> throw new IllegalArgumentException("unsupported workspace event type: " + message.getType());
			}
		}

		private synchronized RoomStateDto snapshot() {
			return new RoomStateDto(roomId, activeFileId, copyFiles(files), new ArrayList<>());
		}

		private synchronized RoomStateDto snapshotForStorage() {
			return new RoomStateDto(roomId, activeFileId, copyFiles(files), new ArrayList<>());
		}

		private CollaborationFileDto requireExistingFile(CollaborationWorkspaceEventMessage message) {
			String fileId = requireFileId(message);
			CollaborationFileDto file = findFile(fileId);
			if (file == null) {
				throw new IllegalArgumentException("file not found: " + fileId);
			}
			return file;
		}

		private String requireFileId(CollaborationWorkspaceEventMessage message) {
			if (message.getFileId() == null || message.getFileId().trim().isEmpty()) {
				throw new IllegalArgumentException("fileId is required");
			}
			return message.getFileId().trim();
		}

		private CollaborationFileDto findFile(String fileId) {
			for (CollaborationFileDto file : files) {
				if (fileId.equals(file.getId())) {
					return file;
				}
			}
			return null;
		}

		private List<CollaborationFileDto> copyFiles(List<CollaborationFileDto> source) {
			List<CollaborationFileDto> copies = new ArrayList<>(source.size());
			for (CollaborationFileDto file : source) {
				copies.add(copyFile(file));
			}
			return copies;
		}

		private CollaborationFileDto copyFile(CollaborationFileDto file) {
			return new CollaborationFileDto(
				file.getId(),
				file.getName(),
				file.getContent(),
				file.getLanguage(),
				file.getVersion()
			);
		}
	}
}
