# ChatOps

> **Real-time group chat backend** built with Java 25, Spring Boot, WebSockets/STOMP, JWT auth,
> Redis presence tracking, cursor-paginated message history, rate limiting, Docker, and GitHub Actions CI/CD.

[![CI](https://github.com/YOUR_USERNAME/chatops/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/chatops/actions/workflows/ci.yml)
[![CD](https://github.com/YOUR_USERNAME/chatops/actions/workflows/cd.yml/badge.svg)](https://github.com/YOUR_USERNAME/chatops/actions/workflows/cd.yml)

---

## Architecture

```
Client ──HTTP/WS──► Spring Boot App (:8080)
                         │
                    ┌────┴────┐
                    │         │
               PostgreSQL   Redis
               (messages,   (presence:online set,
                users,       presence:sessions:<user> sorted set,
                rooms)       auth:refresh:<token>,
                             jwt:blacklist:<jti>)
```

## Tech Stack

| Layer         | Technology                                |
|---------------|-------------------------------------------|
| Language      | Java 25                                   |
| Framework     | Spring Boot 3.3                           |
| Real-time     | WebSocket / STOMP (SockJS fallback)       |
| Auth          | JWT (JJWT 0.12.6) + Redis token blacklist |
| Database      | PostgreSQL 16 + Flyway migrations         |
| Cache/Pub-Sub | Redis 7                                   |
| Rate Limiting | Bucket4j (per-IP token bucket)            |
| Container     | Docker + Docker Compose                   |
| CI/CD         | GitHub Actions + GHCR                     |
| Docs          | SpringDoc / OpenAPI 3                     |
| Logging       | Logback + Logstash JSON encoder (prod)    |

---

## Quickstart

### Prerequisites
- Docker Desktop
- Java 25+ (for local development)

### Run with Docker Compose

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/chatops.git
cd chatops

# 2. Copy and configure environment variables
cp .env.example .env
# Edit .env — at minimum change JWT_SECRET

# 3. Start everything
docker compose up -d

# 4. App is available at
open http://localhost:8080/swagger-ui.html
```

### Run locally (dev mode)

```bash
# Start only the infrastructure
docker compose up -d postgres redis

# Run the app with dev profile
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## API Endpoints

### Authentication (`/api/auth`) — public, rate-limited: 10 req/min per IP

| Method | Path        | Description              | Auth |
|--------|-------------|--------------------------|------|
| POST   | `/register` | Register a new account   | ❌   |
| POST   | `/login`    | Login and receive tokens | ❌   |
| POST   | `/refresh`  | Refresh access token     | ❌   |
| POST   | `/logout`   | Revoke tokens            | ✅   |

### Rooms (`/api/rooms`) — rate-limited: 120 req/min per IP

| Method | Path          | Description      | Auth |
|--------|---------------|------------------|------|
| POST   | `/`           | Create a room    | ✅   |
| GET    | `/`           | List my rooms    | ✅   |
| GET    | `/{id}`       | Get room details | ✅   |
| POST   | `/{id}/join`  | Join a public room | ✅   |
| DELETE | `/{id}/leave` | Leave a room     | ✅   |

Room details and room topics require membership. Public rooms allow self-join; private-room
self-join is denied until an invitation flow is added.

### Messages (`/api/rooms/{id}/messages`)

| Method | Path              | Description                      | Auth |
|--------|-------------------|----------------------------------|------|
| GET    | `?before=&limit=` | Cursor-paginated message history | ✅   |

### Presence (`/api/presence`)

| Method | Path           | Description                                   | Auth |
|--------|----------------|-----------------------------------------------|------|
| GET    | `/online`      | All currently online usernames                | ✅   |
| GET    | `/rooms/{id}`  | Online status of every member in a room       | ✅   |
| POST   | `/heartbeat`   | Renew presence TTL (call every ~60s)          | ✅   |

### Users (`/api/users`)

| Method | Path  | Description             | Auth |
|--------|-------|-------------------------|------|
| GET    | `/me` | Get current user profile | ✅  |

---

## WebSocket (STOMP over SockJS)

Connect to `/ws` with an `Authorization: Bearer <jwt>` header in the CONNECT frame.

### Client → Server (publish)

| Destination              | Payload                   | Description              |
|--------------------------|---------------------------|--------------------------|
| `/app/rooms/{id}/send`   | `{"content":"Hello!"}`    | Send a chat message      |
| `/app/rooms/{id}/typing` | *(empty)*                 | Broadcast typing indicator |

### Server → Client (subscribe)

| Destination                   | Payload type    | Description                    |
|-------------------------------|-----------------|--------------------------------|
| `/topic/rooms/{id}`           | `MessageResponse` | Incoming chat messages        |
| `/topic/rooms/{id}/typing`    | `TypingEvent`   | Typing indicators (ephemeral)  |
| `/topic/rooms/{id}/presence`  | `PresenceEvent` | Online/offline events          |
| `/user/queue/errors`          | `ErrorMessage`  | Private error replies          |

---

## Cursor Pagination

Messages use cursor-based pagination for stable, efficient history loading.

```http
# First page (most recent 50 messages)
GET /api/rooms/1/messages?limit=50

# Next page (pass nextCursor from previous response)
GET /api/rooms/1/messages?before=123456&limit=50
```

`limit` must be between 1 and 100 (default 50), and `before` must be a positive cursor.

Response:
```json
{
  "messages": [...],
  "nextCursor": 123406,
  "hasMore": true
}
```

---

## Environment Variables

| Variable                | Default                  | Description                          |
|-------------------------|--------------------------|--------------------------------------|
| `DB_HOST`               | `localhost`              | PostgreSQL host                      |
| `DB_PORT`               | `5432`                   | PostgreSQL port                      |
| `DB_NAME`               | `chatops`                | Database name                        |
| `DB_USER`               | `chatops`                | Database user                        |
| `DB_PASSWORD`           | —                        | **Required** database password        |
| `REDIS_HOST`            | `localhost`              | Redis host                           |
| `REDIS_PORT`            | `6379`                   | Redis port                           |
| `JWT_SECRET`            | —                        | **Required in production** — high-entropy secret (at least 64 characters recommended); dev has a local-only fallback |
| `JWT_ACCESS_EXPIRY`     | `3600000`                | Access token TTL (ms)                |
| `JWT_REFRESH_EXPIRY`    | `604800000`              | Refresh token TTL (ms)               |
| `CORS_ORIGINS`          | `http://localhost:3000`  | Comma-separated allowed origins       |
| `SPRING_PROFILES_ACTIVE`| `dev`                    | Spring profile (`dev`/`prod`/`test`) |

### CD-only secrets (GitHub repository secrets)

| Secret           | Description                                |
|------------------|--------------------------------------------|
| `SSH_HOST`       | Production server IP or hostname           |
| `SSH_USER`       | SSH username                               |
| `SSH_PRIVATE_KEY`| PEM private key for SSH access             |
| `SSH_PORT`       | SSH port (default 22)                      |
| `GHCR_TOKEN`     | GitHub PAT with `read:packages` scope      |

---

## Project Structure

```
src/main/java/com/chatops/
├── auth/          # JWT, AuthController, AuthService
├── config/        # SecurityConfig, RedisConfig, WebSocketConfig,
│                  # OpenApiConfig, RateLimitFilter
├── exception/     # Global error handling, custom exceptions
├── message/       # Message entity, service, REST controller
├── presence/      # PresenceService (Redis), PresenceController
├── room/          # Room entity, service, controller
├── user/          # User entity, UserDetailsService, UserController
└── websocket/     # ChatController, StompChannelInterceptor,
                   # WebSocketEventListener
```

---

## Running Tests

```bash
# Unit + integration tests (requires Docker for Testcontainers)
sh ./mvnw verify

# Tests only
sh ./mvnw test
```

---

## License

MIT
