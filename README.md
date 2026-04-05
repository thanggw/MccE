# Multi-Lang Collaboration Cloud Engine (McCE)

McCE là nền tảng lập trình trực tuyến cho phép nhiều người cùng soạn thảo và thực thi mã nguồn đa ngôn ngữ (Java, Python, C++, Node.js) trong môi trường biệt lập (Sandbox).

## MVP (khả thi tối thiểu)
1. Web app Next.js + Monaco Editor: chọn ngôn ngữ, quản lý danh sách file, soạn thảo đa file.
2. API Spring Boot (REST) nhận danh sách file -> ghi vào workspace tạm -> chạy thử command.
3. Chuyển sang chạy trong Container bằng Docker Engine (thời gian + RAM giới hạn).
4. WebSocket (STOMP) đồng bộ nội dung editor theo real-time giữa nhiều user.

## Repo layout (monorepo)
Mục lục kiến trúc và hợp đồng giao tiếp nằm trong `docs/`.

## Cách chạy (skeleton)
Sau khi bạn scaffold code thật cho `apps/web` và `apps/api`, bạn có thể dùng `docker-compose.yml` để chạy môi trường dev (API + sandbox/runner).

