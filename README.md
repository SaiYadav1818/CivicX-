# JanSeva (CivicX)

AI-powered civic issue detection, duplicate prevention, GPS tracking and complaint management platform.

## Status

**Phase 4 — Image embeddings + duplicate workflow completion: DONE** (78/78 tests pass, live-verified)

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Project skeleton, MySQL + Flyway schema, users/roles, JWT security, Swagger, exception handling, rate limiting, docker-compose, tests | ✅ Done |
| 2 | Complaint submission, image upload, GPS capture, reference numbers, admin review workflow | ✅ Done |
| 3 | AI-assisted category suggestions, image hashing, location + text duplicate detection, admin duplicate review, RabbitMQ pipeline | ✅ Done |
| 4 | Image embeddings (cosine visual similarity), citizen view of redacted matching complaints | ✅ Done |
| 5 | Tracking, authority mapping, department assignment, notifications | ⏳ Next |
| 6 | Admin dashboard APIs | ⬜ |
| 7 | Production readiness (Docker build, monitoring, hardening) | ⬜ |

## Stack

Java 21 · Spring Boot 3.4 · Spring Security + JWT · Spring Data JPA · MySQL 8 · Flyway · Redis · RabbitMQ · Swagger/OpenAPI · Testcontainers · JUnit 5 / Mockito

## Layout

```
backend/
  src/main/java/com/janseva/
    auth/        register, login, refresh (JWT access + rotating refresh tokens)
    security/    JwtService, JwtAuthenticationFilter, entry point, CurrentUser
    user/        User, Role (CITIZEN/ADMIN/SUPERVISOR/DEPARTMENT), repositories
    complaint/   Complaint, Location, Media, History, categories/wards/zones,
                 ComplaintService, ComplaintMediaService, status workflow,
                 citizen + admin controllers
    ai/          ClassificationProvider abstraction + rule-based classifier,
                 AiAnalysisResult
    hashing/     SHA-256 + perceptual hashes (average hash / difference hash)
    embedding/   128-dim local baseline image embeddings + cosine similarity
    duplicate/   Combined duplicate engine, admin review (confirm/dismiss),
                 cycle prevention
    processing/  Async pipeline: outbox relay -> RabbitMQ -> consumer
    outbox/      Transactional outbox (reliable event publishing)
    geo/         Haversine distance + bounding-box queries
    text/        Token normalization + Jaccard similarity
    storage/     ComplaintFileStorageService (interface) + local filesystem impl
    audit/       AuditLog entity + AuditService (audit_logs table)
    common/      ApiException, ApiError, GlobalExceptionHandler, PagedResponse
    config/      SecurityConfig, OpenApiConfig, AdminBootstrap, rate-limit filters,
                 RabbitConfig, CivicxProperties, AppProperties
  src/main/resources/db/migration/
    V1__init_schema.sql   full schema (27 tables)
    V2__seed_data.sql     roles, 13 categories, departments, Hyderabad zones/wards
    V3__complaints_phase2.sql  complaint_number nullable, zone_id, landmark,
                               media.original_filename, indexes
    V4__ai_duplicate_processing.sql  sha256 on image_hashes, duplicate review columns,
                                     processing_outbox table
    V5__image_embeddings.sql  unique key (media_id, model_name) on image_embeddings
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

## API

### Authentication (Phase 1)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/auth/register` | Register citizen | public (rate-limited) |
| POST | `/api/v1/auth/login` | Login, returns access + refresh tokens | public (rate-limited) |
| POST | `/api/v1/auth/refresh` | Rotate refresh token | public (rate-limited) |

### Citizen complaints (Phase 2)

| Method | Path | Description | Roles |
|--------|------|-------------|-------|
| POST | `/api/v1/complaints` | Submit complaint (category, GPS, description) | CITIZEN |
| GET | `/api/v1/complaints/my` | My complaints (paged) | CITIZEN |
| GET | `/api/v1/complaints/{id}` | My complaint details | CITIZEN (owner only) |
| GET | `/api/v1/complaints/{id}/history` | My complaint status history | CITIZEN (owner only) |
| POST | `/api/v1/complaints/{id}/images` | Upload JPEG/PNG/WebP photo (multipart `file`) | owner or staff |
| GET | `/api/v1/complaints/{id}/images/{mediaId}` | Download photo (authenticated) | owner or staff |

### Admin complaints (Phase 2)

| Method | Path | Description | Roles |
|--------|------|-------------|-------|
| GET | `/api/v1/admin/complaints` | List with filters: `status`, `categoryId`, `wardId`, `zoneId`, `from`, `to`, `page`, `size`, `sort` | ADMIN, SUPERVISOR |
| GET | `/api/v1/admin/complaints/{id}` | Details incl. photos metadata | ADMIN, SUPERVISOR |
| GET | `/api/v1/admin/complaints/{id}/history` | Status history | ADMIN, SUPERVISOR |
| POST | `/api/v1/admin/complaints/{id}/verify` | Verify → VERIFIED (optional `note`) | ADMIN |
| POST | `/api/v1/admin/complaints/{id}/reject` | Reject → REJECTED (`reason` mandatory) | ADMIN |
| PATCH | `/api/v1/admin/complaints/{id}/status` | Workflow-validated status change | ADMIN |

### AI, duplicates and background processing (Phase 3)

| Method | Path | Description | Roles |
|--------|------|-------------|-------|
| GET | `/api/v1/complaints/{id}/ai-summary` | Citizen view: processing status, category suggestion, duplicate flag (no other citizens' data) | CITIZEN (owner) |
| GET | `/api/v1/admin/complaints/{id}/ai-results` | Classifier results (model, category, confidence, explanation) | ADMIN, SUPERVISOR |
| GET | `/api/v1/admin/complaints/{id}/duplicates` | Duplicate candidates with per-signal scores | ADMIN, SUPERVISOR |
| POST | `/api/v1/admin/complaints/{id}/duplicates/{matchedId}/confirm` | Confirm duplicate → links to primary (nothing deleted, cycles rejected with 409) | ADMIN |
| POST | `/api/v1/admin/complaints/{id}/duplicates/{matchedId}/dismiss` | Dismiss candidate | ADMIN |
| POST | `/api/v1/admin/complaints/{id}/ai-processing/retry` | Re-queue failed background processing | ADMIN |

### Status workflow

```
SUBMITTED ──> UNDER_REVIEW ──> VERIFIED ──> ASSIGNED ──> IN_PROGRESS ──> RESOLVED ──> CLOSED
    |              |                                                                    |
    └──────────────┴──────────> REJECTED ─────────────> REOPENED <─────────────────────┘
```

Invalid transitions return **409**. Every change is written to `complaint_history` (who, when, reason) and `audit_logs`.

## Phase 3 architecture

### Background pipeline (reliable async)

```
ComplaintService.create ──(same DB tx)──> processing_outbox
                                              │  OutboxRelay (@Scheduled, every 5s)
                                              ▼
                                        RabbitMQ "complaint.created"
                                              │  ComplaintProcessingConsumer (idempotent)
                                              ▼
                    classification (rule-based) ──> ai_analysis_results
                    image hashing (SHA-256 + aHash + dHash) ──> image_hashes
                    duplicate detection ──> duplicate_detection_results
```

- Complaint submission **never blocks or fails** because of RabbitMQ: the event is written to a
  transactional outbox in the same DB transaction as the complaint.
- The relay retries publishing with backoff (configurable max attempts); the consumer is idempotent
  (re-processing an event creates no duplicate AI/hash/duplicate records).
- Failed processing is retried via `POST .../ai-processing/retry`.

### Classification

- `ComplaintClassificationProvider` interface; current implementation is a **transparent keyword-based
  classifier** (`civicx-rule-based-classifier`, method `RULE_BASED`). It never invents confidence
  scores: confidence is derived from matched keyword counts, and unclear texts get low confidence plus
  `requiresHumanReview`. **No external AI model is claimed** — a provider with real credentials can be
  plugged in later.

### Image similarity

- SHA-256 detects exact duplicates; 64-bit average hash (aHash) and difference hash (dHash) detect
  resized/re-compressed copies (Hamming distance, configurable threshold).
- **Image embeddings (Phase 4)**: every image also gets a deterministic 128-dim local baseline embedding
  (8x8 grayscale thumbnail + 4x4x4 RGB histogram, L2-normalized, model `civicx-local-embedding`). Visual
  similarity is the max of hash similarity and embedding cosine similarity. This is a transparent local
  baseline — a real neural embedding model can replace it behind the same storage format.

### Duplicate engine

- Candidates: bounding-box query (indexed) + Haversine distance within the configured radius.
- Signals: location proximity, Jaccard text similarity, image hash similarity, category match.
- Weighted combination with **weight renormalization when signals are missing** — missing GPS or images
  never silently count as zero-quality evidence.
- Scores ≥ `duplicate-threshold` → `POSSIBLE_DUPLICATE`; between thresholds → `REVIEW_REQUIRED`;
  nothing → `NO_MATCH_FOUND`; no usable signals → `INSUFFICIENT_DATA`.
- Detection never rejects or merges anything automatically — the admin decides (confirm/dismiss).
  Confirming links the complaint to the primary one; both are preserved with history, cycles are blocked.
- **Citizen view (Phase 4)**: when possible duplicates exist, the citizen's `ai-summary` includes up to 3
  redacted matches (complaint number, title, status, distance) so they can view the existing report and
  submit additional evidence. No citizen names, emails or exact coordinates are exposed.

## Example: full flow with curl

```powershell
# Register + submit
$t = (Invoke-RestMethod http://localhost:8080/api/v1/auth/register -Method Post -ContentType application/json `
  -Body '{"fullName":"Citizen","email":"c1@test.local","password":"Password1"}').accessToken

$c = Invoke-RestMethod http://localhost:8080/api/v1/complaints -Method Post -Headers @{Authorization="Bearer $t"} `
  -ContentType application/json -Body '{"categoryId":1,"title":"Garbage near market","description":"Garbage pile near market gate.","latitude":17.484,"longitude":78.391,"locationSource":"DEVICE_GPS"}'
# -> {"complaintNumber":"CX-000001","status":"SUBMITTED", ...}

curl.exe -X POST "http://localhost:8080/api/v1/complaints/$($c.id)/images" -H "Authorization: Bearer $t" -F "file=@photo.jpg;type=image/jpeg"

# Admin verify
$at = (Invoke-RestMethod http://localhost:8080/api/v1/auth/login -Method Post -ContentType application/json `
  -Body '{"email":"admin@janseva.local","password":"Admin@123"}').accessToken
Invoke-RestMethod "http://localhost:8080/api/v1/admin/complaints/$($c.id)/verify" -Method Post -Headers @{Authorization="Bearer $at"} -ContentType application/json -Body '{"note":"Confirmed"}'
Invoke-RestMethod "http://localhost:8080/api/v1/admin/complaints/$($c.id)/history" -Headers @{Authorization="Bearer $at"}
```

Swagger UI (`http://localhost:8080/swagger-ui.html`) documents every endpoint; upload via the "Try it out" file picker on `POST /api/v1/complaints/{complaintId}/images`.

## Configuration (Phase 2)

| Property | Env var | Default | Meaning |
|---|---|---|---|
| `app.storage.local-dir` | `STORAGE_DIR` | `./data/object-storage` | Local file storage root (relative to backend/) |
| `app.storage.max-image-size-bytes` | `MAX_IMAGE_SIZE_BYTES` | `5242880` | Max image size (5 MB) |
| `app.storage.max-images-per-complaint` | `MAX_IMAGES_PER_COMPLAINT` | `5` | Max photos per complaint |
| `app.rate-limit.complaint-per-hour` | — | `20` | Per-citizen complaint submission limit |
| `app.rate-limit.image-upload-per-hour` | — | `30` | Per-user image upload limit |
| `civicx.ai.enabled` | `CIVICX_AI_ENABLED` | `true` | Enable background classification |
| `civicx.ai.provider` | `CIVICX_AI_PROVIDER` | `rule-based` | Classification provider selection |
| `civicx.duplicate.enabled` | `CIVICX_DUPLICATE_ENABLED` | `true` | Enable duplicate detection |
| `civicx.duplicate.location-radius-meters` | `CIVICX_DUP_RADIUS_M` | `100` | GPS match radius |
| `civicx.duplicate.text-threshold` | `CIVICX_DUP_TEXT_THRESHOLD` | `0.25` | Jaccard threshold for text signal |
| `civicx.duplicate.image-hash-threshold` | `CIVICX_DUP_IMAGE_HAMMING` | `10` | Max Hamming distance (of 64) for similar images |
| `civicx.duplicate.image-embedding-threshold` | `CIVICX_DUP_EMBED_THRESHOLD` | `0.90` | Cosine similarity threshold for embedding signal |
| `civicx.duplicate.duplicate-threshold` | `CIVICX_DUP_SCORE_THRESHOLD` | `0.55` | Combined score → POSSIBLE_DUPLICATE |
| `civicx.duplicate.review-threshold` | `CIVICX_DUP_REVIEW_THRESHOLD` | `0.45` | Combined score → REVIEW_REQUIRED |
| `civicx.outbox.poll-ms` | `CIVICX_OUTBOX_POLL_MS` | `5000` | Outbox relay interval |
| `civicx.outbox.max-attempts` | `CIVICX_OUTBOX_MAX_ATTEMPTS` | `10` | Publish attempts before marking FAILED |

Images are stored under `STORAGE_DIR/complaints/{complaintId}/{uuid}.{ext}`. Content is validated by magic bytes (JPEG/PNG/WebP), filenames are server-generated (path traversal safe), original names are sanitized metadata only. Storage goes through `ComplaintFileStorageService` so an S3-compatible implementation can be swapped in later (not implemented yet — no fake integrations).

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

## Security decisions

- Stateless JWT auth; refresh tokens stored **hashed** (SHA-256) in MySQL, rotated on every use.
- BCrypt hashing + password policy at DTO level.
- Citizen identity always taken from the JWT principal, never from request bodies.
- Ownership + role checks enforced in the **service layer** (IDOR protection: other citizens' complaints → 403).
- RBAC: citizen ops need CITIZEN; admin writes need ADMIN; admin reads allow ADMIN + SUPERVISOR; DEPARTMENT unused for now.
- Status transitions validated against a workflow map; arbitrary frontend-driven status changes are impossible (409).
- Rate limits per user on complaint submission and image upload (in-memory; will move to Redis).
- Magic-byte image validation, size caps, per-complaint image caps, server-generated storage keys.
- No filesystem paths, password hashes or tokens ever leave the API.
- Audit trail in `audit_logs` for create/upload/verify/reject/status changes (no sensitive data logged).

## What is NOT done yet

- Real external AI / neural embedding models — provider interfaces and storage formats are ready;
  currently transparent local baselines (no fake integrations).
- Object storage (S3/MinIO) — abstraction ready, local impl only.
- Thumbnail generation, notifications, department assignment / authority mapping (Phase 5).
- Admin dashboard UI, Dockerfile for the app, CI.
- Frontend (React) not started.
