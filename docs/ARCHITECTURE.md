# Architecture

## Tổng quan luồng dữ liệu
1. Frontend (Next.js + Monaco) quản lý workspace gồm nhiều file.
2. Khi user bấm Run:
   - Frontend gửi REST request tới backend (Spring Boot) kèm danh sách file và language.
3. Backend chuẩn hóa dữ liệu:
   - tạo thư mục tạm trong volume / filesystem riêng
   - ghi từng file vào workspace tạm
4. Execution Layer:
   - tạo container sandbox tương ứng (Java/Python/C++/Node)
   - copy/ghi code vào container
   - compile (nếu cần) và run với timeout 5-10s, RAM < 256MB
5. Backend trả `stdout`/`stderr`/exitCode về frontend.
6. WebSocket (STOMP) xử lý real-time collaboration:
   - mỗi thay đổi file/editor được broadcast tới các user cùng session.

## Module (đề xuất)
### Apps
- `apps/web`: UI, Monaco, file explorer, kết nối WebSocket, gọi REST /execute
- `apps/api`: REST + WebSocket/STOMP, quản lý session/workspace, điều phối sandbox execution

### Execution
- `execution/runner`: logics tạo folder tạm, mapping ngôn ngữ -> image, build command, cleanup
- `execution/docker`: Dockerfiles/entrypoints cho từng runtime (javac/java, python, g++, node)

### Shared
- `shared/contracts`: DTO/Schema chung (request/response, websocket event types)

## Ghi chú về sandbox
- Mỗi lần chạy tạo container riêng biệt, không có network truy cập (trừ khi MVP cần).
- Chặn khả năng truy cập filesystem host:
  - mount read-only nếu có mount
  - chỉ cho phép mount thư mục tạm chứa code
- Giới hạn:
  - timeout
  - cgroup memory limit (RAM < 256MB)

