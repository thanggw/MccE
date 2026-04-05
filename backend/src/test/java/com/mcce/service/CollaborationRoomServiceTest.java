package com.mcce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcce.dto.CollaborationDeltaMessage;
import com.mcce.dto.CollaborationEditorChangeDto;
import com.mcce.dto.CollaborationFileDto;
import com.mcce.dto.CollaborationWorkspaceEventMessage;
import com.mcce.dto.RoomStateDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CollaborationRoomServiceTest {

	@Test
	void appliesDeltaAndBumpsFileVersion() {
		CollaborationRoomService service = createService();
		service.joinRoom("room-1", "session-a", "Alice");

		CollaborationWorkspaceEventMessage create = new CollaborationWorkspaceEventMessage();
		create.setRoomId("room-1");
		create.setType("CREATE_FILE");
		create.setFile(new CollaborationFileDto("file-1", "Main.java", "Hello", "java", 0));
		service.applyWorkspaceEvent("session-a", create);

		CollaborationDeltaMessage delta = new CollaborationDeltaMessage();
		delta.setRoomId("room-1");
		delta.setFileId("file-1");
		delta.setBaseVersion(0);
		delta.setChanges(List.of(new CollaborationEditorChangeDto(5, 0, " world")));

		CollaborationDeltaMessage applied = service.applyDelta("session-a", delta);
		RoomStateDto snapshot = service.getSnapshot("room-1");

		assertTrue(Boolean.TRUE.equals(applied.getAccepted()));
		assertEquals(1, applied.getServerVersion());
		assertEquals("Hello world", snapshot.getFiles().get(0).getContent());
		assertEquals(1, snapshot.getFiles().get(0).getVersion());
	}

	@Test
	void rejectsDeltaWithStaleBaseVersion() {
		CollaborationRoomService service = createService();
		service.joinRoom("room-1", "session-a", "Alice");

		CollaborationWorkspaceEventMessage create = new CollaborationWorkspaceEventMessage();
		create.setRoomId("room-1");
		create.setType("CREATE_FILE");
		create.setFile(new CollaborationFileDto("file-1", "Main.java", "Hello", "java", 0));
		service.applyWorkspaceEvent("session-a", create);

		CollaborationDeltaMessage firstDelta = new CollaborationDeltaMessage();
		firstDelta.setRoomId("room-1");
		firstDelta.setFileId("file-1");
		firstDelta.setBaseVersion(0);
		firstDelta.setChanges(List.of(new CollaborationEditorChangeDto(5, 0, "!")));
		service.applyDelta("session-a", firstDelta);

		CollaborationDeltaMessage staleDelta = new CollaborationDeltaMessage();
		staleDelta.setRoomId("room-1");
		staleDelta.setFileId("file-1");
		staleDelta.setBaseVersion(0);
		staleDelta.setChanges(List.of(new CollaborationEditorChangeDto(0, 0, "Oops ")));

		CollaborationDeltaMessage rejected = service.applyDelta("session-a", staleDelta);
		RoomStateDto snapshot = service.getSnapshot("room-1");

		assertFalse(Boolean.TRUE.equals(rejected.getAccepted()));
		assertEquals("RESYNC_REQUIRED", rejected.getReason());
		assertEquals(1, rejected.getServerVersion());
		assertEquals("Hello!", snapshot.getFiles().get(0).getContent());
		assertEquals(1, snapshot.getFiles().get(0).getVersion());
	}

	private CollaborationRoomService createService() {
		CollaborationRoomStorageService storageService = Mockito.mock(CollaborationRoomStorageService.class);
		Mockito.when(storageService.findRoom(Mockito.anyString())).thenReturn(java.util.Optional.empty());
		Mockito.when(storageService.getParticipants(Mockito.anyString())).thenReturn(List.of());
		return new CollaborationRoomService(storageService);
	}
}
