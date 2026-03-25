# Birdlens Backend — Architecture Document

**Version:** 1.0  
**Status:** Approved  
**Last Updated:** 2026-03-24  
**Author:** Architecture Review  
**PRD Reference:** `docs/prd.md` v1.0

---

## Introduction

### Purpose

This document defines the technical architecture for the Birdlens backend system — a Spring Boot-based REST API and event-driven processing layer that serves the Birdlens Android application. It is intended to guide implementation across all epics and serve as the authoritative technical reference for the BMAD dev agent during story execution.

### Scope

This architecture covers the MVP backend system including:

- Core REST API (authentication, community feed, hotspots, tours, subscriptions)
- eBird data ingestion pipeline
- Asynchronous media processing via RabbitMQ
- Notification delivery worker
- Local development infrastructure via Docker Compose
- PostgreSQL with PostGIS for spatial queries

**Out of scope for this MVP:** Kubernetes orchestration, Terraform provisioning, Grafana/Prometheus observability stack, and AI-based bird identification. These are deferred to a post-MVP phase.

### Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Repository structure | Polyrepo (separate from Android app) | Clean API contract boundary, independent CI/CD |
| Service packaging | Single Spring Boot JAR with worker profiles | Avoids multi-JAR coordination overhead for a solo learning project; same app, different Spring profiles |
| Message broker | RabbitMQ | Simpler ops than Kafka for this scale; good Spring AMQP support |
| Spatial database | PostgreSQL + PostGIS | Native support for `ST_DWithin`, GiST indexing; no external geo service needed |
| Object storage | MinIO (local) / AWS S3 (prod) | S3-compatible SDK works against both; no vendor lock-in |
| Auth mechanism | Stateless JWT | Suits mobile client; no session state required on server |
| Upload strategy | API-proxied multipart | Simpler than pre-signed URL flow for MVP; avoids client-side timing race conditions |

---

## High Level Architecture

### System Overview

The system is a **modular monolith with background workers**, packaged as a single Spring Boot application that can be activated with different Spring profiles to isolate responsibilities:

- **`api` profile** — Handles all synchronous HTTP traffic from the Android client
- **`worker` profile** — Runs `@RabbitListener` consumers for image processing and notifications

Both profiles share the same codebase and JPA entity model, but activate different `@Configuration` classes and `@Component` beans. In production, they can be deployed as separate container instances from the same Docker image by passing `--spring.profiles.active=worker`.

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Client                          │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTPS REST
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot — API Profile                       │
│  Auth · Users · Posts · Hotspots · Tours · PayOS · Feed     │
└──┬──────────────────┬────────────────────────┬──────────────┘
   │                  │                        │
   ▼                  ▼                        ▼
┌──────────┐    ┌──────────┐           ┌────────────────┐
│ RabbitMQ │    │  Redis   │           │  PostgreSQL     │
│ Exchange │    │  Cache   │           │  + PostGIS      │
└──┬───────┘    └──────────┘           └────────────────┘
   │
   ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot — Worker Profile                    │
│  ImageProcessingConsumer · NotificationConsumer             │
└──────────────────────────┬──────────────────────────────────┘
                           │ Read/write processed media
                           ▼
                    ┌────────────┐
                    │  MinIO/S3  │
                    │  Storage   │
                    └────────────┘

┌─────────────────────────────────────────────────────────────┐
│              eBird Ingestion (Scheduled Jobs)                │
│  TaxonomyIngestionJob · HotspotIngestionJob                 │
│  Runs inside the API profile on startup schedule            │
└─────────────────────────────────────────────────────────────┘
```

### Request Flow — Post Creation (Happy Path)

```
Client → POST /api/v1/posts (multipart: metadata + images)
       → Spring Security validates JWT
       → PostService saves post record (status: PENDING)
       → S3Service uploads raw images to MinIO, saves URLs to post_media
       → RabbitMQPublisher publishes PostCreatedEvent { postId }
       → Returns 201 Created with post data  ← client unblocked here

[async, background]
Worker → ImageProcessingConsumer.onMessage(PostCreatedEvent)
       → Fetches post_media records for postId
       → Downloads originals from MinIO
       → Generates thumbnail + compressed versions via Thumbnailator
       → Uploads new versions to MinIO
       → Updates post_media: thumbnail_url, compressed_url, status=COMPLETED
```

### Request Flow — Nearby Hotspots (Cache Hit)

```
Client → GET /api/v1/hotspots/nearby?lat=10.78&lng=106.70&radiusKm=10
       → Spring Security validates JWT
       → HotspotService.findNearby(lat, lng, radiusKm)
          → Cache key: "hotspots:10.78:106.70:10"
          → Redis hit → return cached list  (< 50ms)
          → Redis miss → PostGIS ST_DWithin query → cache result (TTL 1h) → return
```

---

## Tech Stack

### Core Application

| Layer | Technology | Version | Notes |
|---|---|---|---|
| Language | Java | 21 (LTS) | Use record types and sealed classes where appropriate |
| Framework | Spring Boot | 3.3.x | Web, Data JPA, Security, Validation, Actuator |
| Build tool | Gradle | 8.x | Kotlin DSL (`build.gradle.kts`) |
| ORM | Spring Data JPA + Hibernate | (via Boot) | Use JPQL for complex queries; native SQL for PostGIS spatial functions |
| DB Migrations | Flyway | 10.x | Versioned SQL scripts in `src/main/resources/db/migration/` |
| Security | Spring Security + JJWT | 0.12.x | Stateless JWT; BCrypt password hashing |
| Messaging | Spring AMQP | (via Boot) | RabbitMQ; `@RabbitListener` for consumers |
| Caching | Spring Cache + Lettuce | (via Boot) | Redis backend; `@Cacheable` for hotspot endpoints |
| HTTP Client | Spring WebClient | (via Boot WebFlux) | For eBird API calls; non-blocking with Resilience4j retry |
| Resilience | Resilience4j | 2.x | Retry with exponential backoff for eBird client |
| Object Storage | AWS SDK for Java | 2.x | S3-compatible; configured to point at MinIO locally |
| Image Processing | Thumbnailator | 0.4.x | Thumbnail and compression generation in the worker |
| Testing | JUnit 5 + Mockito + Testcontainers | latest | See Test Strategy section |

### Infrastructure (Local Dev via Docker Compose)

| Service | Image | Port | Purpose |
|---|---|---|---|
| PostgreSQL | `postgis/postgis:16-3.4` | 5432 | Primary database with spatial extension |
| Redis | `redis:7-alpine` | 6379 | Cache layer |
| RabbitMQ | `rabbitmq:3.13-management` | 5672 / 15672 | Message broker + management UI |
| MinIO | `minio/minio:latest` | 9000 / 9001 | S3-compatible object storage + console |
| Spring Boot App | `birdlens-api:local` | 8080 | Main API (api profile) |
| Spring Boot Worker | `birdlens-api:local` | 8081 | Worker (worker profile, same image) |

### External Services

| Service | Purpose | Auth |
|---|---|---|
| eBird API v2 | Taxonomy + hotspot data source | `x-ebirdapitoken` header |
| PayOS | VietQR payment link generation + webhooks | API key + HMAC checksum |

---

## Data Models

### Entity Relationship Overview

```
users
  ├── posts (1:N)
  │     ├── post_media (1:N)
  │     ├── post_comments (1:N)
  │     └── post_reactions (1:N)
  ├── user_notifications (1:N)
  └── subscriptions (N:1 via subscription_id FK)

ebird_hotspots (standalone, populated by ingestion job)
bird_taxonomy  (standalone, populated by ingestion job)
events (standalone)
  └── tours (N:1 via event_id FK)
subscriptions (standalone lookup table)
```

### Core Tables

#### `users`
```sql
CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 VARCHAR(255) NOT NULL UNIQUE,
    username              VARCHAR(100) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100),
    last_name             VARCHAR(100),
    avatar_url            TEXT,
    subscription_id       UUID REFERENCES subscriptions(id),
    subscription_expires_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### `posts`
```sql
CREATE TABLE posts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id),
    content           TEXT,
    location_name     VARCHAR(255),
    location_point    GEOGRAPHY(Point, 4326),
    privacy_level     VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    type              VARCHAR(20) NOT NULL,           -- 'GENERAL' | 'SIGHTING'
    sighting_date     DATE,
    tagged_species_code VARCHAR(20) REFERENCES bird_taxonomy(species_code),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
```

#### `post_media`
```sql
CREATE TABLE post_media (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id           UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    original_url      TEXT NOT NULL,
    thumbnail_url     TEXT,
    compressed_url    TEXT,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PROCESSING | COMPLETED | FAILED
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

#### `ebird_hotspots`
```sql
CREATE TABLE ebird_hotspots (
    loc_id                VARCHAR(50) PRIMARY KEY,
    loc_name              VARCHAR(255) NOT NULL,
    country_code          VARCHAR(5),
    subnational1_code     VARCHAR(20),
    subnational2_code     VARCHAR(20),
    latitude              DOUBLE PRECISION NOT NULL,
    longitude             DOUBLE PRECISION NOT NULL,
    location              GEOGRAPHY(Point, 4326) NOT NULL,
    latest_obs_dt         DATE,
    num_species_all_time  INT DEFAULT 0,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hotspots_location ON ebird_hotspots USING GIST(location);
```

#### `bird_taxonomy`
```sql
CREATE TABLE bird_taxonomy (
    species_code          VARCHAR(20) PRIMARY KEY,
    common_name           VARCHAR(255) NOT NULL,
    scientific_name       VARCHAR(255) NOT NULL,
    category              VARCHAR(50),
    taxon_order           NUMERIC,
    bird_order            VARCHAR(100),
    family_common_name    VARCHAR(100),
    family_scientific_name VARCHAR(100)
);
CREATE INDEX idx_taxonomy_common_name ON bird_taxonomy(lower(common_name) text_pattern_ops);
CREATE INDEX idx_taxonomy_scientific_name ON bird_taxonomy(lower(scientific_name) text_pattern_ops);
```

#### `user_notifications`
```sql
CREATE TABLE user_notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    message     TEXT NOT NULL,
    type        VARCHAR(50) NOT NULL,  -- 'POST_LIKED' | 'NEW_COMMENT' | 'SUBSCRIPTION_ACTIVATED'
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_unread ON user_notifications(user_id, is_read);
```

### JPA Entity Conventions

- All entities use `@UuidGenerator` strategy for primary keys
- Auditing columns (`created_at`, `updated_at`) use `@CreationTimestamp` / `@UpdateTimestamp`
- Spatial columns (PostGIS `GEOGRAPHY`) mapped via Hibernate Spatial with `@Column(columnDefinition = "GEOGRAPHY(Point, 4326)")`
- Enums stored as `@Enumerated(EnumType.STRING)` — never `ORDINAL`
- No bidirectional `@OneToMany` unless explicitly needed; prefer repository queries

---

## Components

### Package Structure

```
com.birdlens.api
├── config/
│   ├── SecurityConfig.java          # Spring Security + JWT filter chain
│   ├── RabbitMQConfig.java          # Exchange, queue, binding declarations
│   ├── RedisConfig.java             # Cache manager, TTL config
│   ├── S3Config.java                # AWS SDK client pointed at MinIO
│   ├── WebClientConfig.java         # eBird API WebClient + Resilience4j
│   └── WorkerConfig.java            # @ConditionalOnProfile("worker") beans
│
├── domain/
│   ├── user/
│   │   ├── User.java                # JPA entity
│   │   ├── UserRepository.java
│   │   ├── UserService.java
│   │   └── AuthController.java      # /api/v1/auth/*
│   ├── post/
│   │   ├── Post.java
│   │   ├── PostMedia.java
│   │   ├── PostComment.java
│   │   ├── PostReaction.java
│   │   ├── PostRepository.java
│   │   ├── PostMediaRepository.java
│   │   ├── PostService.java
│   │   └── PostController.java      # /api/v1/posts/*
│   ├── hotspot/
│   │   ├── EbirdHotspot.java
│   │   ├── HotspotRepository.java   # Custom @Query with ST_DWithin
│   │   ├── HotspotService.java      # @Cacheable logic
│   │   └── HotspotController.java   # /api/v1/hotspots/*
│   ├── taxonomy/
│   │   ├── BirdTaxonomy.java
│   │   ├── TaxonomyRepository.java
│   │   └── TaxonomyController.java  # /api/v1/taxonomy/search
│   ├── notification/
│   │   ├── UserNotification.java
│   │   ├── NotificationRepository.java
│   │   └── NotificationController.java
│   ├── subscription/
│   │   ├── Subscription.java
│   │   ├── SubscriptionRepository.java
│   │   └── SubscriptionController.java
│   ├── tour/
│   │   ├── Event.java
│   │   ├── Tour.java
│   │   └── TourController.java
│   └── payment/
│       ├── PayOSService.java
│       └── PayOSWebhookController.java
│
├── ingestion/
│   ├── EbirdApiClient.java          # WebClient wrapper for eBird API
│   ├── TaxonomyIngestionJob.java    # @Scheduled taxonomy sync
│   └── HotspotIngestionJob.java     # @Scheduled hotspot sync
│
├── messaging/
│   ├── events/
│   │   ├── PostCreatedEvent.java
│   │   ├── PostLikedEvent.java
│   │   ├── NewCommentEvent.java
│   │   └── SubscriptionActivatedEvent.java
│   ├── publishers/
│   │   └── DomainEventPublisher.java
│   └── consumers/
│       ├── ImageProcessingConsumer.java  # @Profile("worker")
│       └── NotificationConsumer.java     # @Profile("worker")
│
├── storage/
│   └── S3StorageService.java        # MinIO/S3 upload/download
│
├── security/
│   ├── JwtService.java
│   ├── JwtAuthFilter.java
│   └── UserDetailsServiceImpl.java
│
└── common/
    ├── exception/
    │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice
    │   ├── ResourceNotFoundException.java
    │   └── ConflictException.java
    └── dto/
        └── ApiResponse.java             # Standardized response wrapper
```

### Key Component Responsibilities

**`SecurityConfig`** — Configures the Spring Security filter chain. All routes under `/api/v1/**` require a valid JWT except `/api/v1/auth/**` and `/api/v1/webhooks/**`. Sets session management to `STATELESS`.

**`RabbitMQConfig`** — Declares the following topology:
- `posts.exchange` (direct) → `image-processing-queue` (binding key: `post.created`)
- `notifications.exchange` (topic) → `notifications-queue` (binding key: `notification.*`)
- Dead-letter exchange `dlx.exchange` → `dlq` for both queues (after 3 failed nack attempts)

**`HotspotRepository`** — Contains a custom `@Query` using native SQL with PostGIS:
```java
@Query(value = """
    SELECT * FROM ebird_hotspots
    WHERE ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
    ORDER BY num_species_all_time DESC
    LIMIT 50
    """, nativeQuery = true)
List<EbirdHotspot> findNearby(double lat, double lng, double radiusMeters);
```

**`ImageProcessingConsumer`** — Activated only on `worker` profile. Reads `PostCreatedEvent`, downloads originals from MinIO, uses Thumbnailator to produce a 300×300 thumbnail and a web-compressed JPEG (quality 70%), re-uploads, then updates `post_media` records.

**`NotificationConsumer`** — Activated only on `worker` profile. Listens on `notifications-queue`. Deserializes event type from message headers, then creates a `UserNotification` record with an appropriate human-readable message string.

---

## External APIs

### eBird API v2

**Base URL:** `https://api.ebird.org/v2/`  
**Auth:** Request header `x-ebirdapitoken: {API_KEY}` (configured via `app.ebird.api-key` property)

| Endpoint | Used by | Schedule |
|---|---|---|
| `GET /ref/taxonomy/ebird?fmt=json` | `TaxonomyIngestionJob` | Weekly (or on startup if table empty) |
| `GET /ref/hotspot/geo?lat={lat}&lng={lng}&dist={km}&fmt=json` | `HotspotIngestionJob` | Every 6 hours, per configured region list |

**Resilience4j configuration (applied to WebClient):**
```yaml
resilience4j:
  retry:
    instances:
      ebirdClient:
        max-attempts: 3
        wait-duration: 2s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
```

**eBird Upsert pattern:** Both ingestion jobs use `JdbcTemplate.batchUpdate()` with an `INSERT ... ON CONFLICT (species_code) DO UPDATE` statement. This avoids N+1 save calls for potentially 17,000+ taxonomy records.

### PayOS

**Base URL:** Configured via `app.payos.base-url` property  
**Auth:** API key in header; HMAC-SHA256 checksum validation on all webhook payloads

**Webhook flow:**
1. `POST /api/v1/webhooks/payos` receives event payload
2. `PayOSService.verifyChecksum(payload, signature)` validates integrity using the configured `app.payos.checksum-key`
3. If `status == "PAID"`, look up `orderCode` in Redis (set during payment link creation with TTL 24h), retrieve `userId`, update user subscription fields, publish `SubscriptionActivatedEvent`
4. Always return `200 OK` to PayOS regardless of business logic outcome (prevents PayOS retry storms)

---

## Core Workflows

### eBird Taxonomy Ingestion

```
TaxonomyIngestionJob (@Scheduled, weekly)
  → Check bird_taxonomy row count
  → If empty OR scheduled run: call EbirdApiClient.fetchTaxonomy()
  → Receive List<TaxonomyDto> (up to 17,000 records)
  → Partition into batches of 500
  → JdbcTemplate.batchUpdate() with UPSERT SQL per batch
  → Log: start time, records processed, duration, any errors
  → On partial failure: log failed batch, continue remaining batches (best-effort)
```

### Community Feed with Caching

```
GET /api/v1/posts?page=0&size=20&sort=latest

PostService.getFeed(pageable)
  → Redis cache key: "feed:{page}:{size}:{sort}"
  → Cache TTL: 5 minutes (feed is acceptable to be slightly stale)
  → Cache miss: JPA query joining posts + users + post_media (WHERE status = 'COMPLETED')
  → Return only thumbnail_url (not original_url) in response
  → Post-like/comment counts fetched via COUNT subquery in the same query
```

### JWT Authentication Flow

```
POST /api/v1/auth/login { email, password }
  → Load UserDetails by email
  → BCrypt.matches(rawPassword, storedHash)
  → On success: JwtService.generateAccessToken(userId, role)  [15 min expiry]
              + JwtService.generateRefreshToken(userId)       [7 day expiry]
  → Return { access_token, refresh_token }

Subsequent requests:
  → JwtAuthFilter extracts Bearer token from Authorization header
  → JwtService.validateToken(token) → extract userId
  → Set SecurityContextHolder with authenticated principal
  → Downstream controllers call SecurityContextHolder.getContext().getAuthentication()
```

---

## Source Tree

```
birdlens-api/
├── src/
│   ├── main/
│   │   ├── java/com/birdlens/api/      # All source code (see Components section)
│   │   └── resources/
│   │       ├── application.yml          # Base config
│   │       ├── application-api.yml      # API profile overrides
│   │       ├── application-worker.yml   # Worker profile overrides
│   │       ├── application-local.yml    # Local dev secrets (gitignored)
│   │       └── db/migration/
│   │           ├── V1__init_users.sql
│   │           ├── V2__init_taxonomy_hotspots.sql
│   │           ├── V3__init_posts_media_reactions.sql
│   │           ├── V4__init_notifications.sql
│   │           └── V5__init_tours_events_subscriptions.sql
│   └── test/
│       ├── java/com/birdlens/api/
│       │   ├── integration/             # Testcontainers-backed tests
│       │   │   ├── AuthIntegrationTest.java
│       │   │   ├── HotspotIntegrationTest.java
│       │   │   ├── PostCreationIntegrationTest.java
│       │   │   └── PayOSWebhookIntegrationTest.java
│       │   └── unit/                    # Mockito unit tests
│       │       ├── JwtServiceTest.java
│       │       ├── HotspotServiceTest.java
│       │       └── ImageProcessingConsumerTest.java
│       └── resources/
│           └── application-test.yml     # Testcontainers config overrides
├── docker/
│   └── init-postgis.sql                 # Ensures PostGIS extension is created
├── docker-compose.yml                   # Full local stack
├── Dockerfile                           # Multi-stage build for API + Worker (same image)
├── build.gradle.kts
├── settings.gradle.kts
└── docs/
    ├── prd.md
    └── architecture.md                  # This file
```

---

## Infrastructure and Deployment

### Docker Compose (Local Development)

The single `docker-compose.yml` at the repository root starts all services. Run with:

```bash
docker-compose up -d         # Start all services
docker-compose up api        # Start only API (Postgres, Redis, RabbitMQ, MinIO must be running)
```

**Service startup order** (enforced via `depends_on` + health checks):
1. `postgres` (with PostGIS)
2. `redis`, `rabbitmq`, `minio`
3. `api` (waits for postgres healthy)
4. `worker` (waits for api healthy — ensures Flyway migrations have run)

**MinIO bucket setup:** A `minio-init` one-shot service runs `mc` to create the `birdlens-media` bucket on first startup.

### Application Profiles

| Profile | Activated by | Runs |
|---|---|---|
| `api` | Default, or `--spring.profiles.active=api` | Web server, scheduled jobs, event publishers |
| `worker` | `--spring.profiles.active=worker` | RabbitMQ consumers only; no web server |
| `local` | Combined with above in local dev | Local DB/Redis/MinIO connection strings |
| `test` | Activated by Testcontainers test base class | Spins up real containers; no external dependencies |

### Dockerfile (Multi-Stage)

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The worker container runs the same image with `SPRING_PROFILES_ACTIVE=worker,local` passed as an environment variable in `docker-compose.yml`.

### Environment Variables

All sensitive configuration is externalized. Never hardcode secrets. Required variables:

```
# Database
DB_URL=jdbc:postgresql://postgres:5432/birdlens
DB_USERNAME=birdlens
DB_PASSWORD=<secret>

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# RabbitMQ
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# MinIO / S3
S3_ENDPOINT=http://minio:9000
S3_BUCKET=birdlens-media
S3_ACCESS_KEY=<secret>
S3_SECRET_KEY=<secret>

# JWT
JWT_SECRET=<min-256-bit-secret>
JWT_EXPIRY_MINUTES=15
JWT_REFRESH_EXPIRY_DAYS=7

# eBird
EBIRD_API_KEY=<secret>

# PayOS
PAYOS_API_KEY=<secret>
PAYOS_CHECKSUM_KEY=<secret>
PAYOS_BASE_URL=https://api-merchant.payos.vn
```

---

## Error Handling Strategy

### Global Exception Handler

A `@RestControllerAdvice` class (`GlobalExceptionHandler`) maps all exceptions to a consistent JSON response shape:

```json
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Hotspot with id 'L123456' not found"
  },
  "timestamp": "2026-03-24T10:00:00Z"
}
```

| Exception | HTTP Status | Error Code |
|---|---|---|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `ConflictException` | 409 | `CONFLICT` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `AuthenticationException` | 401 | `UNAUTHORIZED` |
| Any uncaught `Exception` | 500 | `INTERNAL_ERROR` (generic message only, no stack trace in response) |

### RabbitMQ Dead Letter Queue

Both worker consumers are configured with a retry policy before rejecting to the DLQ:

```java
// In RabbitMQConfig.java
@Bean
SimpleRabbitListenerContainerFactory containerFactory(...) {
    factory.setDefaultRequeueRejected(false);  // Do not requeue on exception
    // Retry 3 times with 2s backoff before sending to DLQ
    factory.setAdviceChain(RetryInterceptorBuilder.stateless()
        .maxAttempts(3)
        .backOffOptions(2000, 2.0, 10000)
        .build());
}
```

**DLQ monitoring:** Check `rabbitmq_management` UI at `localhost:15672` (guest/guest) during local development to inspect failed messages.

### eBird Ingestion Failures

The ingestion jobs use a try-catch per batch and log failures without halting the overall job. A complete failure of the eBird API results in a log warning but does not affect the API's ability to serve from the existing local dataset (satisfying NFR2).

---

## Coding Standards

### General Java Rules

- Target Java 21; use `record` for DTOs and event payloads, `sealed interface` for discriminated union types if needed
- No `var` for non-obvious types; use explicit types for method parameters and return types
- Prefer constructor injection over field injection (`@Autowired` on fields is forbidden)
- `@Transactional` belongs on the service layer, never on the controller or repository
- Never return `null` from a service method; use `Optional<T>` or throw a typed exception
- All `@RestController` methods must declare `@RequestMapping` with explicit HTTP method and path

### Naming Conventions

| Artifact | Convention | Example |
|---|---|---|
| Packages | `com.birdlens.api.{domain}` | `com.birdlens.api.post` |
| Classes | `PascalCase` | `PostCreationService` |
| Methods | `camelCase`, verb-first | `findNearbyHotspots`, `publishPostCreatedEvent` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_FEED_PAGE_SIZE` |
| DB tables | `snake_case`, plural | `post_reactions` |
| DB columns | `snake_case` | `species_code` |
| RabbitMQ queues | `kebab-case` | `image-processing-queue` |
| Cache keys | `domain:param1:param2` | `hotspots:10.78:106.70:10` |
| DTO records | Suffix `Request` or `Response` | `CreatePostRequest`, `PostResponse` |

### API Design Rules

- All endpoints versioned under `/api/v1/`
- Use standard HTTP verbs: `GET` (read), `POST` (create), `PUT` (replace), `PATCH` (partial update), `DELETE`
- Return `201 Created` with the created resource body on successful POST
- Pagination via `?page=0&size=20` using Spring Data `Pageable`; response wrapped in `Page<T>`
- Validation on all request bodies using `@Valid` + Bean Validation annotations (`@NotNull`, `@Email`, `@Size`, etc.)
- Never expose `password_hash`, internal IDs of other users, or raw exception stack traces in responses

### Flyway Migration Rules

- Never modify an existing migration file; always create a new version
- Migration scripts are idempotent where possible (use `IF NOT EXISTS`, `ON CONFLICT DO NOTHING`)
- Each migration has a single responsibility (one table or one index batch per file)
- Migration filenames: `V{n}__{description_in_snake_case}.sql`

---

## Test Strategy and Standards

### Test Pyramid

```
         [E2E / Contract Tests]         ← minimal; API contract smoke tests only
       [Integration Tests - Testcontainers]   ← primary confidence layer
     [Unit Tests - JUnit + Mockito]            ← fast; business logic isolation
```

### Unit Tests

- Located in `src/test/java/.../unit/`
- Use Mockito to mock all dependencies; test a single class in isolation
- Required for: `JwtService`, `HotspotService` (cache logic), `PostService` (state transitions), `PayOSService` (checksum validation logic), all `@Scheduled` job logic
- Do not use `@SpringBootTest` — these tests should run in milliseconds

### Integration Tests

- Located in `src/test/java/.../integration/`
- Extend a shared `AbstractIntegrationTest` base class that starts Testcontainers for PostgreSQL, Redis, and RabbitMQ using `@Testcontainers` and `@Container`
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` or `MockMvc`
- Required scenarios:
  - Auth registration, duplicate rejection, login, token validation
  - Hotspot spatial query — verify point inside radius included, point outside excluded
  - Redis cache — verify second call does not hit database (use `@SpyBean` on repository)
  - Post creation — verify DB record exists AND message published to RabbitMQ queue
  - PayOS webhook — valid checksum triggers subscription update; invalid checksum returns 400

### Testcontainers Base Class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:16-3.4");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }
}
```

---

## Security

### Authentication and Authorization

- Passwords hashed with BCrypt, strength factor 12
- JWT access tokens expire in 15 minutes; refresh tokens in 7 days
- JWT secret must be at least 256 bits; loaded from environment variable, never from `application.yml` in source control
- All endpoints require a valid JWT except: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/webhooks/payos`, `GET /api/v1/health`
- Premium-only endpoints (`GET /api/v1/hotspots/{locId}/visiting-times`) validate `subscription_type` in the service layer and throw `AccessDeniedException` for standard users

### Input Validation

- All request bodies validated via `@Valid` and Bean Validation; validation errors return `400 Bad Request`
- Path variables and query params validated with `@Validated` + constraint annotations at the controller level
- No raw SQL concatenation; all queries use JPA JPQL or `JdbcTemplate` parameterized queries (no SQL injection risk)

### Webhook Security

- PayOS webhook endpoint verifies HMAC-SHA256 checksum of the raw request body against the configured `PAYOS_CHECKSUM_KEY`
- Verification must happen before any business logic is executed
- On checksum mismatch, return `400 Bad Request` immediately

### Secrets Management

- All secrets in environment variables; `.env` files are gitignored
- `.env.example` file committed to repository with placeholder values only
- No credentials committed to version control under any circumstance

### CORS

- CORS configured in `SecurityConfig` to allow only the Android app's registered origins (not `*`) in production
- During local development, CORS permits all origins for convenience

---

*End of architecture document. This file is intended to be sharded by BMAD tooling using `##` headings as section boundaries.*
