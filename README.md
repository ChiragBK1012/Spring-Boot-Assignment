# Grid07 Backend API — Spring Boot Intern Assignment

## Tech Stack
- Java 17, Spring Boot 3.2, PostgreSQL 16, Redis 7

---

## Quick Start

### 1. Start infrastructure
```bash
docker-compose up -d
```
This spins up PostgreSQL on port **5432** and Redis on port **6379**.

### 2. Build & run the application
```bash
./mvnw spring-boot:run
```
The API starts on **http://localhost:8050**.  
Hibernate auto-creates all tables on first boot (`ddl-auto=update`).

### 3. Import the Postman collection
Import `Grid07_API.postman_collection.json` into Postman, then set the `baseUrl`, `userId`, `botId`, and `postId` collection variables.

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users` | Create a user |
| GET  | `/api/users` | List all users |
| POST | `/api/bots` | Create a bot |
| GET  | `/api/bots` | List all bots |
| POST | `/api/posts` | Create a post |
| POST | `/api/posts/{id}/like` | Like a post (+20 virality) |
| POST | `/api/posts/{id}/comments` | Add a comment (guardrails apply for bots) |

---

## Phase 2 — Thread-Safety for Atomic Locks

### Problem
200 concurrent HTTP requests can all read `bot_count = 99` simultaneously, pass the cap check independently, and all write to the DB — ending up with 200 comments instead of 100.

### Solution: Atomic Lua Script in Redis

The **Horizontal Cap** is enforced by a Lua script executed on the Redis server:

```lua
local current = redis.call('INCR', KEYS[1])
if current > tonumber(ARGV[1]) then
    redis.call('DECR', KEYS[1])
    return 0
end
return 1
```

Redis executes Lua scripts **atomically and single-threadedly** — no two scripts can interleave. This means the INCR + compare + conditional DECR is **one indivisible unit**, making it impossible for two threads to both see `current == 100` and both proceed.

- Returns `1` → bot reply is allowed (counter incremented).  
- Returns `0` → cap exceeded (counter unchanged), API returns HTTP 429.

The DB write only happens **after** the Redis script returns `1`. If the DB write fails for any reason, `decrementBotCount()` is called to roll back the Redis counter, keeping both stores in sync.

### Cooldown Cap
Uses Redis `SET key 1 NX EX 600` — a **single atomic command**. `NX` (set-if-not-exists) guarantees only one bot-human pair acquires the lock per 10-minute window, even under concurrency.

### Vertical Cap
A simple integer comparison on `depthLevel`; no shared state, inherently thread-safe.

---

## Phase 3 — Notification Engine

- On every bot interaction, `NotificationService` checks for a `notif:cooldown:user_{id}` key.
- **No cooldown active** → logs `Push Notification Sent to User {id}` and sets a 15-minute TTL key.
- **Cooldown active** → pushes the message string to `user:{id}:pending_notifs` (a Redis List).
- `NotificationScheduler` runs every **5 minutes** via `@Scheduled`, scans all `user:*:pending_notifs` keys, drains each list, and logs a summarised message:  
  `Summarized Push Notification: BotName and [N] others interacted with your posts.`

---

## Running the Concurrency Test

Requires Docker containers to be running.

```bash
./mvnw test -Dtest=HorizontalCapConcurrencyTest
```

Expected output:
```
Allowed:  100
Rejected: 100
```

---

## Project Structure

```
src/main/java/com/grid07/api/
├── config/         # RedisConfig, RedisKeys constants
├── controller/     # PostController, UserController, BotController, GlobalExceptionHandler
├── dto/            # Request/Response DTOs
├── entity/         # User, Bot, Post, Comment, PostLike
├── repository/     # Spring Data JPA repositories
├── scheduler/      # NotificationScheduler (CRON sweeper)
└── service/        # PostService, GuardrailService, ViralityService,
                    # NotificationService, UserService, BotService
```
