# REST API (draft for MVP)

## Execute code
`POST /api/v1/execute`

### Request
```json
{
  "sessionId": "string",
  "language": "java|python|cpp|node",
  "files": [
    { "path": "Main.java", "content": "public class Main {...}" }
  ],
  "entryFile": "Main.java"
}
```

### Response
```json
{
  "exitCode": 0,
  "stdout": "....",
  "stderr": "....",
  "timingMs": 123
}
```

## File operations (draft)
`POST /api/v1/sessions/{sessionId}/files`
`DELETE /api/v1/sessions/{sessionId}/files`
`PATCH /api/v1/sessions/{sessionId}/files/{path}`

Gợi ý payload:
```json
{ "content": "..." }
```

## Validation
- Backend phải validate `path` (chống `..`, absolute paths).
- Giới hạn tổng size request (ví dụ <= vài MB) để tránh lạm dụng.

