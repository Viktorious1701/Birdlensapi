# Tech Stack

## Core Application

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

## Infrastructure (Local Dev via Docker Compose)

| Service | Image | Port | Purpose |
|---|---|---|---|
| PostgreSQL | `postgis/postgis:16-3.4` | 5432 | Primary database with spatial extension |
| Redis | `redis:7-alpine` | 6379 | Cache layer |
| RabbitMQ | `rabbitmq:3.13-management` | 5672 / 15672 | Message broker + management UI |
| MinIO | `minio/minio:latest` | 9000 / 9001 | S3-compatible object storage + console |
| Spring Boot App | `birdlens-api:local` | 8080 | Main API (api profile) |
| Spring Boot Worker | `birdlens-api:local` | 8081 | Worker (worker profile, same image) |

## External Services

| Service | Purpose | Auth |
|---|---|---|
| eBird API v2 | Taxonomy + hotspot data source | `x-ebirdapitoken` header |
| PayOS | VietQR payment link generation + webhooks | API key + HMAC checksum |

---
