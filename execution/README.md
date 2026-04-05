# Execution Layer (Docker sandbox)

## Trách nhiệm
- Nhận `sessionId` + language + các file.
- Tạo thư mục tạm (temp workspace).
- Ghi file vào workspace tạm.
- Tạo container sandbox tương ứng:
  - Java: compile (javac) + run (java)
  - Python: run (python)
  - C++: compile (g++) + run
  - Node: run (node)
- Lấy `stdout`/`stderr`/exitCode.
- Cleanup workspace tạm + container.

## MVP progression
1. (Bước thử) chạy trực tiếp command trên server (sử dụng `Runtime.exec()`).
2. (Bước chính) chuyển sang Docker Engine + Docker Java SDK.

