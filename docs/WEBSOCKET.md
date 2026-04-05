# Real-time Collaboration (WebSocket/STOMP)

## Mục tiêu
Khi User A thay đổi file hoặc nội dung editor, User B trong cùng `sessionId` thấy thay đổi ngay (độ trễ thấp).

## Gợi ý topics (STOMP)
- Subscribe: `/topic/sessions/{sessionId}/events`
- Publish: `/app/sessions/{sessionId}/events`

## Event types (draft)
- `FILE_CREATED`
- `FILE_DELETED`
- `FILE_RENAMED`
- `FILE_UPDATED`
- `CURSOR_UPDATED` (tuỳ chọn cho MVP sau)
- `RUN_REQUESTED` / `RUN_RESULT` (tuỳ chọn)

## Payload gợi ý
```json
{
  "type": "FILE_UPDATED",
  "sessionId": "abc",
  "file": { "path": "Main.java" },
  "content": "class Main {...}"
}
```

## Consistency
- MVP có thể dùng chiến lược last-write-wins.
- Nếu cần mạnh hơn (OT/CRDT) thì đổi sang CRDT/OT cho editor.

