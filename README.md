# JanSeva (CivicX)

AI-powered civic issue detection, duplicate prevention, GPS tracking and complaint management platform.

## Status

**Phase 1 — Foundation: DONE** (compiles, all tests pass, smoke-tested end to end)

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Project skeleton, MySQL + Flyway schema, users/roles, JWT security, Swagger, exception handling, rate limiting, docker-compose, tests | ✅ Done |
| 2 | Complaint submission, image upload, GPS capture, reference numbers | ⏳ Next |
| 3 | AI detection (async via RabbitMQ, confidence thresholds, review) | ⬜ |
| 4 | Duplicate detection engine (GPS + image + category + text signals) | ⬜ |
| 5 | Tracking, authority mapping, notifications | ⬜ |
| 6 | Admin dashboard APIs | ⬜ |
| 7 | Production readiness (Docker build, monitoring, hardening) | ⬜ |

## Stack

Java 21 · Spring Boot 3.4 · Spring Security + JWT · Spring Data JPA · MySQL 8 · Flyway · Redis · RabbitMQ · Swagger/OpenAPI · Testcontainers · JUnit 5 / Mockito

## Layout

```
backend/
  src/main/java/com/janseva/
    auth/        register, login, refresh (JWT access + rotating refresh tokens)
    security/    JwtService, JwtAuthenticationFilter, entry point
    user/        User, Role (CITIZEN/ADMIN/SUPERVISOR/DEPARTMENT), repositories
    common/      ApiException, ApiError, GlobalExceptionHandler
    config/      SecurityConfig, OpenApiConfig, AdminBootstrap, AuthRateLimitFilter, AppProperties
  src/main/resources/db/migration/
    V1__init_schema.sql   full schema (27 tables: complaints, media, locations, history,
                          AI results, embeddings, hashes, duplicate results/links, zones/wards,
                          departments, service areas, notifications, audit logs, escalation rules)
    V2__seed_data.sql     roles, 13 complaint categories, departments, Hyderabad zones/wards
docker-compose.yml        MySQL 8 + Redis 7 + RabbitMQ 3
```

## Run

```powershell
# 1. Infrastructure
docker compose up -d

# 2. Backend
cd backend
mvn spring-boot:run

# 3. Swagger UI
#    http://localhost:8080/swagger-ui.html
#    OpenAPI JSON: http://localhost:8080/v3/api-docs
```

Default admin: `admin@janseva.local` / `Admin@123` (override with `ADMIN_EMAIL` / `ADMIN_DEFAULT_PASSWORD` env vars — change it in production).

## API (Phase 1)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/auth/register` | Register citizen | public (rate-limited) |
| POST | `/api/v1/auth/login` | Login, returns access + refresh tokens | public (rate-limited) |
| POST | `/api/v1/auth/refresh` | Rotate refresh token | public (rate-limited) |

## Tests

```powershell
cd backend
mvn test
```

Unit tests run always. Integration tests (Testcontainers MySQL) run when Docker is available and are skipped otherwise.

Note for this machine: Docker Desktop's default pipe returns empty engine info to Testcontainers, and Docker engine 29 requires API >= 1.44. Run:

```powershell
$env:DOCKER_HOST = "npipe:////./pipe/docker_engine_linux"
mvn test "-Ddocker.java.api.version=1.53"
```

## Security decisions (Phase 1)

- Stateless JWT auth; refresh tokens are opaque random strings stored **hashed** (SHA-256) in MySQL and rotated on every use.
- BCrypt password hashing; password policy enforced at DTO level.
- Role-based access via Spring Security authorities; `@EnableMethodSecurity` ready for later phases.
- Auth endpoints rate-limited per IP (in-memory; will move to Redis).
- Centralized exception handling; no stack traces in responses.
- CORS restricted to configured origins via env.

## What is NOT done yet

- Complaint/upload/GPS APIs, AI analysis, duplicate engine (Phases 2–6).
- Object storage (S3/MinIO) integration — `complaint_media` table is ready but no storage client yet.
- Real AI model / embedding service integration — schema is ready; no credentials wired (per prompt rule 20, no fake integrations).
- Notifications, admin dashboard, Dockerfile for the app, CI.
- Frontend (React) not started.
