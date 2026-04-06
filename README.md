# Multi-Lang Collaboration Cloud Engine (McCE)

McCE is a browser-based collaborative coding platform that lets multiple users edit and execute code together in real time. The project combines a Next.js frontend, a Spring Boot backend, WebSocket-based collaboration, Redis-backed room state, and pluggable code execution strategies for both local and cloud deployment.

This repository is structured as a practical engineering project rather than a UI demo. The focus is on collaborative editing, multi-file workspace management, sandboxed execution, and deployment flexibility.

## Highlights

- Real-time collaborative editing with room-based presence
- Multi-file workspace with Java, Python, C++, and JavaScript support
- Shared room state persisted in Redis so code can be restored when users reopen a room
- WebSocket/STOMP synchronization for editor changes, presence, and workspace events
- Two execution strategies:
  - Docker for local or VPS-based sandbox execution
  - Judge0 Public API for cloud environments where Docker is not available
- Environment-variable-driven configuration for local, staging, and production deployments

## Architecture

The system is split into three main parts:

- `frontend/`
  - Next.js application
  - Monaco Editor integration
  - Room UI, file management, terminal panel, and collaboration UX

- `backend/`
  - Spring Boot application
  - REST API for room snapshots and code execution
  - WebSocket/STOMP endpoints for collaboration events
  - Redis-backed room persistence
  - Docker or Judge0 execution integration

- `docs/`
  - Project documentation and protocol notes

## Core Features

### 1. Collaborative Editor

Users can join the same room and edit together in near real time. The backend broadcasts:

- text deltas
- workspace events
- cursor movement
- participant presence

Room snapshots are stored in Redis so the workspace survives page reloads and reconnects.

### 2. Multi-File Workspace

The editor supports a workspace model instead of a single code box:

- create, rename, delete, and switch files
- filter files by language
- search files by name
- preserve the active file inside a room snapshot

### 3. Code Execution

The backend abstracts execution behind a common `ExecutionService` interface.

Execution modes:

- `DOCKER`
  - used for local development or a VPS with Docker available
  - supports interactive execution flow

- `PISTON` or `JUDGE0`
  - currently routed to Judge0 Public API for cloud-friendly execution
  - useful on platforms such as Render where Docker execution is not available

### 4. Java OOP Support for Judge0

Judge0 expects a single `Main` entry class, while users may naturally write Java using descriptive class names such as `Student`, `App`, or `Course`.

To support that workflow, the backend preprocesses Java code before execution:

- removes `package ...;`
- finds the class containing `main(...)`
- rewrites that class to `public class Main`
- downgrades other top-level `public class` declarations to package-private `class`

This allows users to write Java in a more natural OOP style while still executing successfully on Judge0.

## Tech Stack

### Frontend

- Next.js 16
- React 19
- TypeScript
- Monaco Editor
- STOMP over WebSocket
- Tailwind CSS 4

### Backend

- Java 17
- Spring Boot 3.5
- Spring Web
- Spring WebSocket
- Spring Data Redis
- Docker Java client

### Infrastructure

- Redis for room persistence and online participant state
- Judge0 Public API for cloud execution
- Docker for local sandboxed execution
- Vercel / Render / Upstash friendly deployment model

## Repository Structure

```text
.
├── backend/        Spring Boot API and collaboration/execution services
├── frontend/       Next.js client application
├── docs/           Architecture and protocol notes
├── docker-compose.yml
└── README.md
```

## Running Locally

### Prerequisites

- Node.js 20+
- Java 17+
- Maven
- Redis
- Docker (only required if using `EXECUTION_STRATEGY=DOCKER`)

### Backend

From `backend/`:

```bash
mvn spring-boot:run
```

Useful environment variables:

```env
SERVER_PORT=8080
SPRING_REDIS_URL=redis://localhost:6379
MCCE_ALLOWED_ORIGIN_PATTERNS=http://localhost:3000,http://127.0.0.1:3000
EXECUTION_STRATEGY=DOCKER
MCCE_EXECUTION_JUDGE0_URL=https://ce.judge0.com/submissions?base64_encoded=false&wait=true
MCCE_EXECUTION_JUDGE0_TIMEOUT_MS=10000
```

### Frontend

From `frontend/`:

```bash
npm install
npm run dev
```

Typical frontend environment variables:

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_BACKEND_WS_URL=ws://localhost:8080
NEXT_PUBLIC_EXECUTION_STRATEGY=DOCKER
```

### Full Local Stack

If you want a Docker-based local environment, use:

```bash
docker-compose up --build
```

## Deployment Model

The project is designed to support a practical low-cost deployment setup:

- Frontend on Vercel
- Backend on Render
- Redis on Upstash
- Code execution via Judge0 Public API

Recommended production backend configuration:

```env
EXECUTION_STRATEGY=PISTON
SPRING_REDIS_URL=rediss://default:<token>@<upstash-host>:6379
MCCE_ALLOWED_ORIGIN_PATTERNS=https://your-frontend.vercel.app,https://*.vercel.app
MCCE_EXECUTION_JUDGE0_URL=https://ce.judge0.com/submissions?base64_encoded=false&wait=true
MCCE_EXECUTION_JUDGE0_TIMEOUT_MS=10000
```

Recommended production frontend configuration:

```env
NEXT_PUBLIC_API_URL=https://your-backend.onrender.com
NEXT_PUBLIC_BACKEND_WS_URL=wss://your-backend.onrender.com
NEXT_PUBLIC_EXECUTION_STRATEGY=PISTON
```

## Testing

Backend:

```bash
cd backend
mvn test
```

Frontend:

```bash
cd frontend
npm run lint
```

## Engineering Notes

- Collaboration state is split between WebSocket broadcast and Redis persistence
- Redis snapshots keep room state available across reconnects
- Interactive execution is intentionally limited to Docker mode
- Judge0 mode is synchronous and cloud-friendly
- The codebase is configured through environment variables to reduce hard-coded deployment assumptions

## Roadmap

Potential next steps for the project include:

- stronger OT/CRDT-style conflict handling
- richer room and workspace permissions
- execution history
- file tree/folder support
- automated regression tests for collaboration flows
- metrics and observability for production deployments

## License

This repository currently does not declare a license. If you plan to share it publicly for recruiting or open-source use, add an explicit license file.
