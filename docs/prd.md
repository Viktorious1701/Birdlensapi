# Birdlens Backend System Product Requirements Document (PRD)

## Goals and Background Context

**Background Context**
Birdlens is an existing Android application focused on bird-watching, hotspot tracking, and social community engagement. Currently, the application relies on direct integrations with third-party services (like the eBird API) which poses rate-limiting risks, and synchronous handling of heavy payloads (like image uploads). To prepare the system for high traffic and to demonstrate enterprise-grade engineering practices, we are building a dedicated backend system. This project will replace direct external API calls with a robust data ingestion pipeline, implement asynchronous event-driven processing for media using message brokers, and establish a highly scalable, cloud-native infrastructure. AI identification features have been explicitly scoped out of this MVP to prioritize core system scalability, reliability, and infrastructure-as-code (IaC) principles.

**Goals**
*   Deliver a robust, secure, and scalable REST API to serve the Birdlens Android client.
*   Implement an event-driven architecture using a message broker (e.g., RabbitMQ/Kafka) to decouple and asynchronously process heavy operations, such as image compression and storage.
*   Build a resilient scheduled data ingestion pipeline to synchronize eBird API hotspot and taxonomy data into a local PostgreSQL database, protecting the system from third-party rate limits.
*   Design the system using cloud-native principles, containerizing services with Docker and preparing the architecture for Kubernetes orchestration.
*   Ensure high-performance data retrieval for read-heavy operations (like the social community feed) through caching or optimized database querying.

**Change Log**

| Date | Version | Description | Author |
| :--- | :--- | :--- | :--- |
| Current | 1.0 | Initial Draft - Goals and Background | John (PM) |

## Requirements

#### 2.1 Functional Requirements (FR)

*   **FR1 [User Management]:** The system must support user registration, login, and password management, returning secure session tokens (JWT) for authenticated requests.
*   **FR2 [Profile Management]:** The system must provide an endpoint to fetch the current authenticated user's profile and subscription status.
*   **FR3 [Data Ingestion Pipeline]:** The system must run scheduled background jobs to ingest, reconcile, and update bird taxonomy and hotspot data from the external eBird API into the local database.
*   **FR4 [Hotspot Spatial Querying]:** The system must serve nearby birding hotspots to the mobile client using local database spatial queries (e.g., bounding box or radius), rather than proxying requests directly to eBird.
*   **FR5 [Post Creation]:** The system must allow users to create community posts (both "General" and "Sighting" types) containing text, location data, and multiple image files.
*   **FR6 [Asynchronous Media Processing]:** When a post with images is created, the system must immediately save the raw assets to object storage, persist the post data, and publish an event to a message broker (e.g., RabbitMQ) for background processing (e.g., generating thumbnails, compressing images) without blocking the client response.
*   **FR7 [Community Feed]:** The system must serve a paginated feed of community posts, supporting basic sorting/filtering (e.g., "trending" vs. "latest").
*   **FR8 [Social Interactions]:** The system must allow users to add comments to posts and toggle "Like" reactions on posts.
*   **FR9 [Tours & Events]:** The system must provide read-only paginated endpoints for users to browse available birding Tours and Events.
*   **FR10 [Premium Subscriptions]:** The system must integrate with PayOS to generate payment links for users upgrading to the "ExBird" premium tier, and process webhooks to update user subscription status upon successful payment.

#### 2.2 Non-Functional Requirements (NFR)

*   **NFR1 [Performance - Async Offloading]:** API endpoints handling media uploads must respond to the client in under 250ms by offloading heavy image processing tasks to asynchronous background workers.
*   **NFR2 [Resilience & Availability]:** The mobile application must remain fully functional for hotspot viewing even if the external eBird API experiences downtime or rate-limits, relying entirely on the locally synchronized database.
*   **NFR3 [Scalability - Containerization]:** All backend components (Main API, Background Workers) must be stateless and fully containerized (Docker) to allow for horizontal scaling via Kubernetes.
*   **NFR4 [Infrastructure as Code]:** All backing services (PostgreSQL, Redis, RabbitMQ, S3-compatible storage) must be provisionable via Infrastructure as Code (e.g., Terraform or Docker Compose for local dev).
*   **NFR5 [Security]:** User passwords must be securely hashed (e.g., bcrypt). All client-facing endpoints (except login/register/webhooks) must require valid JWT authentication.
*   **NFR6 [Observability]:** The system must expose standard health check and metrics endpoints (e.g., Prometheus format) to monitor API latency, queue depths, and worker processing times.

## Technical Assumptions

#### 3.1 Repository Structure: Polyrepo
*   **Decision:** The Spring Boot backend services will be housed in a repository entirely separate from the Android mobile application (`EXE201/app`). 
*   **Rationale:** This enforces a strict API contract between the client and the server, simplifies CI/CD pipelines (e.g., GitHub Actions building Docker images vs. building Android APKs), and allows the backend to be version-controlled and deployed independently.

#### 3.2 Service Architecture: Distributed System (Event-Driven)
*   **Decision:** The system will adopt a loosely coupled, event-driven microservices architecture (or a highly modular monolith with distinct background workers) orchestrated via a message broker.
*   **Rationale:** To satisfy the CV-building objective of demonstrating enterprise-grade, high-traffic systems:
    *   **Core API Gateway / Backend Service:** Handles synchronous HTTP traffic from the Android app (Auth, serving Feeds, serving Hotspots).
    *   **Message Broker (RabbitMQ):** Acts as the asynchronous communication backbone.
    *   **Background Worker Service:** Dedicated Spring Boot application (or isolated profile) that consumes events (e.g., `ImageUploadedEvent`, `IngestEbirdDataCommand`) from queues to handle CPU-intensive or long-running tasks.
    *   **Caching Layer (Redis):** To absorb heavy read traffic for the community feed and popular hotspots.

#### 3.3 Testing Requirements
*   **Decision:** Comprehensive automated testing is required to validate the distributed components.
    *   **Unit Testing:** (JUnit/Mockito) for core business logic, domain models, and utility functions.
    *   **Integration Testing:** (Testcontainers) is mandatory. The system must spin up real, disposable instances of PostgreSQL, Redis, and RabbitMQ via Docker during the test phase to validate repository layers, caching behavior, and message publishing/consuming logic without relying on mocks.
    *   **API Contract Testing:** Validate that the REST endpoints adhere to the expected request/response schemas required by the Android client.

#### 3.4 Additional Technical Assumptions and Requests
*   **Database (RDBMS):** PostgreSQL will be the primary datastore, utilizing the PostGIS extension to handle the spatial queries required for finding nearby birding hotspots (lat/lng bounding boxes and radii).
*   **Object Storage:** An S3-compatible object storage solution (e.g., MinIO for local development, AWS S3 for production) must be used for persisting media files uploaded by users. The database will only store the URL references.
*   **Cloud-Native & IaC:** The entire local development environment (API, Worker, Postgres, Redis, RabbitMQ, MinIO) must be orchestratable via a single `docker-compose.yml` file. Future production deployments will target Kubernetes (e.g., Minikube, EKS) using Helm or Terraform.
*   **Observability Stack:** The architecture must incorporate a standard monitoring stack (e.g., Prometheus scraping Spring Boot Actuator metrics, and Grafana for visualization) to demonstrate operational readiness.

## Epic List

*   **Epic 1: Foundation, Infrastructure as Code, & Core API**
    *   *Goal:* Establish the Spring Boot project structure, Docker/Docker Compose environments (PostgreSQL, Redis, MinIO, RabbitMQ), basic CI/CD scaffolding, and the core Authentication/User Management REST APIs (JWT).
*   **Epic 2: The eBird Data Ingestion Pipeline**
    *   *Goal:* Build the scheduled background workers, external API client (Retrofit/WebClient), and PostGIS database schemas to autonomously fetch, reconcile, and store bird taxonomy and hotspot data locally, decoupling the mobile app from eBird's rate limits.
*   **Epic 3: Geospatial Hotspot API & Caching**
    *   *Goal:* Expose high-performance, Redis-cached REST endpoints that allow the Android app to query nearby birding hotspots using spatial bounding boxes and radii against the local PostgreSQL (PostGIS) database.
*   **Epic 4: Event-Driven Community Feed & Media Storage**
    *   *Goal:* Implement the core social features (Posts, Comments, Likes) using an event-driven architecture. The API will generate pre-signed S3 URLs for image uploads, save post metadata, and publish events to RabbitMQ for asynchronous background processing (e.g., resizing images).
*   **Epic 5: Tours, Events, & PayOS Integration**
    *   *Goal:* Deliver the e-commerce capabilities by exposing read-only endpoints for browsing Tours/Events, integrating with the PayOS API to generate VietQR checkout links, and securely handling asynchronous payment webhooks to activate "ExBird" premium subscriptions.
*   **Epic 6: Asynchronous Notifications**
    *   *Goal:* Implement a dedicated RabbitMQ consumer worker that listens for domain events (e.g., `PostLikedEvent`, `SubscriptionActivatedEvent`) and creates user notifications, completely decoupling notification logic from the main API request thread.

## Epic Details

#### Epic 1: Foundation, Infrastructure as Code, & Core API
**Epic Goal:** Establish the Spring Boot project structure, Docker/Docker Compose environments (PostgreSQL, Redis, MinIO, RabbitMQ), basic CI/CD scaffolding, and the core Authentication/User Management REST APIs (JWT). This epic creates the resilient, containerized foundation upon which all distributed features will be built, ensuring local development perfectly mirrors a cloud-native production environment.

*   **Story 1.1: As a Backend Developer, I want to initialize a containerized Spring Boot 3 application with a unified `docker-compose.yml` so that I can reliably run PostgreSQL (PostGIS), Redis, RabbitMQ, and MinIO locally without installing dependencies on my host machine.**
    *   *Acceptance Criteria:*
        1. A Spring Boot 3 application (`birdlensAPI`) is initialized with Web, Data JPA, Security, Validation, and Actuator dependencies.
        2. A `docker-compose.yml` file at the repository root successfully spins up isolated containers for PostgreSQL (with PostGIS extension), Redis, RabbitMQ, and MinIO.
        3. The Spring Boot application successfully connects to the PostgreSQL database on startup.
        4. A basic `/api/v1/health` endpoint (unsecured) returns a `200 OK` status, verifying the application context loads.

*   **Story 1.2: As a Backend Developer, I want to configure Flyway (or Liquibase) for database migrations so that schema changes are reliably tracked, versioned, and applied automatically on application startup.**
    *   *Acceptance Criteria:*
        1. Flyway dependencies are integrated into the `build.gradle`.
        2. An initial migration script (`V1__init_schema.sql`) creates the core `users` table (id, email, password_hash, username, first_name, last_name, avatar_url, subscription_type, created_at, updated_at).
        3. The application startup logs confirm that Flyway successfully applied the migration to the Dockerized PostgreSQL database.

*   **Story 1.3: As a Mobile User, I want to register a new account securely so that I can access the Birdlens community and save my preferences.**
    *   *Acceptance Criteria:*
        1. A `POST /api/v1/auth/register` endpoint accepts a JSON payload containing `email`, `password`, and `username`.
        2. The endpoint validates the input (e.g., valid email format, minimum password length).
        3. The user's password is securely hashed (e.g., using BCrypt) before being saved to the database.
        4. The endpoint returns a `201 Created` status with the new user's basic profile data (excluding the password hash).
        5. If an email or username already exists, the endpoint returns a `409 Conflict` with an appropriate error message.
        6. A Testcontainers-backed integration test verifies successful registration and duplicate rejection against a real PostgreSQL instance.

*   **Story 1.4: As a Mobile User, I want to log in with my email and password to receive a secure session token (JWT) so that I can authenticate my future API requests.**
    *   *Acceptance Criteria:*
        1. Spring Security is configured to support stateless, JWT-based authentication.
        2. A `POST /api/v1/auth/login` endpoint accepts `email` and `password`.
        3. Upon successful credential verification, the endpoint returns a `200 OK` containing a short-lived `access_token` (JWT) and a long-lived `refresh_token`.
        4. The `access_token` payload includes the user's ID and role.
        5. Invalid credentials return a `401 Unauthorized` status.

*   **Story 1.5: As an Authenticated Mobile User, I want to retrieve my profile information securely so that the app can display my avatar, subscription status, and username.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/users/me` endpoint is created.
        2. Spring Security intercepts the request, validates the provided JWT in the `Authorization: Bearer <token>` header, and extracts the user ID.
        3. The endpoint returns a `200 OK` with the authenticated user's full profile data (id, email, username, first_name, last_name, avatar_url, subscription).
        4. Requests missing a valid token, or carrying an expired token, return a `401 Unauthorized`.
        5. A Spring Security MockMvc test verifies that an unauthenticated request is blocked, and an authenticated request with a mocked JWT succeeds.

#### Epic 2: The eBird Data Ingestion Pipeline
**Epic Goal:** Build the scheduled background workers, external API client (Retrofit/WebClient), and PostGIS database schemas to autonomously fetch, reconcile, and store bird taxonomy and hotspot data locally, decoupling the mobile app from eBird's rate limits. This epic proves you can handle external data dependencies gracefully, manage database migrations for spatial data, and design a resilient, autonomous system.

*   **Story 2.1: As a Backend Developer, I want to define the core database schemas for Bird Taxonomy and Spatial Hotspots so that ingested eBird data can be queried efficiently by the mobile application.**
    *   *Acceptance Criteria:*
        1. A Flyway migration script creates a `bird_taxonomy` table (species_code, common_name, scientific_name, category, taxon_order, bird_order, family_common_name, family_scientific_name).
        2. A Flyway migration script creates an `ebird_hotspots` table (loc_id, loc_name, country_code, subnational1_code, subnational2_code, latitude, longitude, latest_obs_dt, num_species_all_time).
        3. The `latitude` and `longitude` columns are mapped to a PostGIS `GEOGRAPHY(Point, 4326)` column named `location` for efficient spatial querying.
        4. Appropriate indexes (e.g., a GiST index on the `location` column) are created to optimize bounding box and radius queries.
        5. Spring Data JPA entities are created and accurately mapped to these tables.

*   **Story 2.2: As the System, I want a resilient external API client configured for eBird so that I can fetch taxonomy and hotspot data, handling timeouts, retries, and rate limits gracefully.**
    *   *Acceptance Criteria:*
        1. A Spring `@Configuration` class defines a `WebClient` or `RestTemplate` specifically for `https://api.ebird.org/v2/`.
        2. The client is configured with sensible connection timeouts (e.g., 5 seconds) and read timeouts (e.g., 10 seconds).
        3. A retry mechanism (e.g., using Resilience4j or Spring Retry) is configured to automatically retry `5xx` server errors or network timeouts up to 3 times with exponential backoff.
        4. An Interceptor or Filter automatically injects the eBird API key (configured via `application.properties`) into the `x-ebirdapitoken` header for every request.
        5. A MockWebServer-backed integration test proves that a `200 OK` response parses correctly into DTOs, and a simulated `503 Service Unavailable` triggers a retry before failing gracefully.

*   **Story 2.3: As the System, I want a scheduled background job to ingest the full eBird Taxonomy so that the local database has an up-to-date, searchable list of all bird species.**
    *   *Acceptance Criteria:*
        1. A Spring `@Service` containing an `@Scheduled` method runs periodically (e.g., once a week, or on application startup if the table is empty).
        2. The service calls the eBird `v2/ref/taxonomy/ebird` endpoint and parses the massive JSON response.
        3. The service processes the payload efficiently (e.g., using batch inserts or JDBC `saveAll` rather than thousands of individual `save` calls).
        4. The service performs an "Upsert" (Update if exists, Insert if new) based on the unique `species_code`, ensuring no duplicate records are created and existing names are updated.
        5. The ingestion process logs its start time, the number of records processed, any errors encountered, and its completion time using a standard logger.

*   **Story 2.4: As the System, I want a scheduled background job to ingest notable birding hotspots per region so that the mobile app can query them locally without hitting eBird.**
    *   *Acceptance Criteria:*
        1. A Spring `@Service` containing an `@Scheduled` method runs periodically (e.g., every 6 hours).
        2. The service iterates through a configured list of supported regions (e.g., `["US", "VN", "GB"]`) and calls the eBird `v2/data/obs/{regionCode}/recent/notable` endpoint.
        3. The service maps the observations to unique `ebird_hotspots` and persists them to the PostGIS database.
        4. The service updates the `latest_obs_dt` and `num_species_all_time` for existing hotspots, and inserts new hotspots.
        5. The service logs its progress and failures for each region processed.

#### Epic 3: Geospatial Hotspot API & Caching
**Epic Goal:** Expose high-performance, Redis-cached REST endpoints that allow the Android app to query nearby birding hotspots using spatial bounding boxes and radii against the local PostgreSQL (PostGIS) database. This epic proves you can design fast, read-optimized endpoints, utilize spatial database extensions, and implement caching layers to handle high-traffic social mapping features.

*   **Story 3.1: As a Mobile Client, I want to query the local database for bird taxonomy by common or scientific name so that I can quickly auto-complete species searches without hitting external APIs.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/taxonomy/search?q={query}` endpoint is created.
        2. The endpoint queries the `bird_taxonomy` table for records where `common_name` or `scientific_name` matches the query (case-insensitive, partial matches allowed).
        3. The endpoint returns a maximum of 15 results to ensure fast response times.
        4. The response format matches the `BirdSpecies` model expected by the Android app (species_code, common_name, scientific_name).
        5. The endpoint is secured with JWT authentication.

*   **Story 3.2: As a Mobile Client, I want to query the local database for nearby birding hotspots using a latitude, longitude, and radius so that the map loads quickly without relying on eBird.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/hotspots/nearby?lat={lat}&lng={lng}&radiusKm={radiusKm}` endpoint is created.
        2. The endpoint utilizes a PostGIS spatial query (e.g., `ST_DWithin`) against the `location` column of the `ebird_hotspots` table.
        3. The endpoint returns a list of hotspots within the specified radius, ordered by `num_species_all_time` descending (or `latest_obs_dt` descending).
        4. The response format matches the `EbirdNearbyHotspot` model expected by the Android app.
        5. The endpoint is secured with JWT authentication.
        6. A Testcontainers-backed integration test validates that points outside the radius are correctly excluded from the response.

*   **Story 3.3: As a Backend Developer, I want to cache the results of the "nearby hotspots" endpoint in Redis so that repeated queries for the same popular locations return in under 50ms.**
    *   *Acceptance Criteria:*
        1. Spring Data Redis and `spring-boot-starter-cache` are integrated.
        2. The `@Cacheable` annotation (or manual RedisTemplate logic) is applied to the `hotspots/nearby` endpoint service method.
        3. The cache key is generated by rounding the `lat` and `lng` to a specific precision (e.g., 2 decimal places) and including the `radiusKm` to ensure high cache hit rates for users in the same general area.
        4. The cache TTL (Time To Live) is set to a reasonable duration (e.g., 1 hour) to balance freshness with performance.
        5. A Testcontainers-backed Redis integration test verifies that the first call hits the database and subsequent identical calls hit the cache.

*   **Story 3.4: As a Mobile Client, I want to fetch detailed information about a specific hotspot ID so that I can view its recent sightings and statistics.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/hotspots/{locId}` endpoint is created.
        2. The endpoint queries the `ebird_hotspots` table by its unique `loc_id`.
        3. If the hotspot is not found in the local database, the endpoint returns a `404 Not Found`.
        4. The response format matches the `EbirdNearbyHotspot` model expected by the Android app.
        5. The endpoint is secured with JWT authentication.

*   **Story 3.5: As a Premium Mobile Client, I want to fetch the analytical visiting times for a hotspot so that I can see the best months and hours to visit.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/hotspots/{locId}/visiting-times` endpoint is created.
        2. The endpoint queries an analytical view or aggregation of historical sightings (or returns mocked/calculated data based on the MVP scope) for the specified `locId`.
        3. The endpoint validates the user's JWT to ensure their `subscription_type` is "ExBird" (Premium). If they are standard, it returns a `403 Forbidden`.
        4. The response format matches the `VisitingTimesAnalysis` model expected by the Android app (Monthly and Hourly stats).

#### Epic 4: Event-Driven Community Feed & Media Storage
**Epic Goal:** Implement the core social features (Posts, Comments, Likes) using an event-driven architecture. The API will generate pre-signed S3 URLs for image uploads, save post metadata, and publish events to RabbitMQ for asynchronous background processing (e.g., resizing images). This epic demonstrates your ability to handle high-volume write operations, large file uploads, and decoupled background processing—all hallmarks of a senior-level engineer.

*   **Story 4.1: As a Backend Developer, I want to create the database schemas and JPA entities for Posts, Comments, and Reactions to store community-generated content.**
    *   *Acceptance Criteria:*
        1. A Flyway migration creates a `posts` table (id, user_id, content, location_name, location_point, privacy_level, type, sighting_date, tagged_species_code, created_at, updated_at).
        2. A Flyway migration creates a `post_media` table (id, post_id, original_url, thumbnail_url, compressed_url, processing_status, created_at).
        3. A Flyway migration creates a `post_comments` table (id, post_id, user_id, content, created_at).
        4. A Flyway migration creates a `post_reactions` table (id, post_id, user_id, reaction_type, created_at).
        5. JPA entities (`Post`, `PostMedia`, `PostComment`, `PostReaction`) are created and correctly mapped to these tables with appropriate relationships (`@ManyToOne`, `@OneToMany`).

*   **Story 4.2: As a Mobile Client, I want to request a secure, pre-signed URL to upload media files directly to object storage (MinIO/S3) so that large files do not need to be proxied through the backend API.**
    *   *Acceptance Criteria:*
        1. An endpoint `POST /api/v1/posts/media/request-upload` accepts a list of desired filenames and content types.
        2. The service uses the AWS SDK (configured for MinIO locally) to generate a unique, time-limited, pre-signed `PUT` URL for each requested file.
        3. The endpoint returns a list of objects, each containing the original filename and its corresponding pre-signed URL.
        4. The endpoint is secured with JWT authentication.
        5. An integration test verifies that a valid pre-signed URL is generated and can be used to upload a file to the Testcontainers MinIO instance.

*   **Story 4.3: As a Mobile Client, I want to create a new "Sighting" post with text and media references, receiving an immediate successful response while media processing happens asynchronously.**
    *   *Acceptance Criteria:*
        1. An endpoint `POST /api/v1/posts` accepts post metadata (content, location, etc.) and a list of object storage keys/URLs corresponding to the files uploaded via the pre-signed URLs.
        2. The service creates a new record in the `posts` table.
        3. The service creates records in the `post_media` table with a `processing_status` of `PENDING`.
        4. The service publishes a `PostCreatedEvent` message to a RabbitMQ exchange (e.g., `posts.exchange`) with the `post_id`.
        5. The endpoint immediately returns a `201 Created` with the new post's data to the client, without waiting for image processing.
        6. A Testcontainers-backed integration test verifies that the database records are created and a message is successfully published to RabbitMQ.

*   **Story 4.4: As the System, I want a dedicated background worker to consume `PostCreatedEvent` messages and process the associated images asynchronously.**
    *   *Acceptance Criteria:*
        1. A separate Spring Boot application or profile acts as a worker.
        2. A RabbitMQ listener (`@RabbitListener`) is configured to consume messages from the `image-processing-queue`.
        3. Upon receiving a message, the worker fetches the `post_id`, retrieves the associated `post_media` records, and downloads the original images from MinIO/S3.
        4. The worker generates a thumbnail and a compressed version of each image using a library like `imgscalr` or `thumbnailator`.
        5. The worker uploads the new image versions back to MinIO/S3.
        6. The worker updates the corresponding `post_media` records in the database with the new URLs and sets the `processing_status` to `COMPLETED`.
        7. The worker correctly handles poison pill messages (failing messages) by moving them to a Dead-Letter Queue (DLQ) after several failed retry attempts.

*   **Story 4.5: As a Mobile Client, I want to fetch a paginated feed of community posts with their processed media so that I can view what other users are sharing.**
    *   *Acceptance Criteria:*
        1. An endpoint `GET /api/v1/posts` is created.
        2. The service queries the `posts` table, joining with `post_media` and `users` tables to construct the response payload.
        3. The response includes the post content, user information, and URLs for the *thumbnail* or *compressed* images (not the original uploads).
        4. The service should be designed for high performance, utilizing caching (Redis) to serve popular or recent posts.

*   **Story 4.6: As a Mobile Client, I want to add comments and toggle likes on posts so that I can interact with the community.**
    *   *Acceptance Criteria:*
        1. Endpoints `GET /api/v1/posts/{postId}/comments` and `POST /api/v1/posts/{postId}/comments` are implemented to manage comments.
        2. An endpoint `POST /api/v1/posts/{postId}/reactions` is implemented to add or remove a 'like' reaction.
        3. The reaction endpoint business logic correctly handles toggling (if a user likes a post they already liked, it should unlike it).
        4. Optionally, a `PostLikedEvent` can be published to RabbitMQ to feed into the notification system (Epic 6).

#### Epic 5: Tours, Events, & PayOS Integration
**Epic Goal:** Deliver the e-commerce capabilities by exposing read-only endpoints for browsing Tours/Events, integrating with the PayOS API to generate VietQR checkout links, and securely handling asynchronous payment webhooks to activate "ExBird" premium subscriptions. This epic showcases your ability to integrate with external payment systems and manage critical, state-changing business logic triggered by asynchronous events.

*   **Story 5.1: As a Backend Developer, I want to create the database schemas and JPA entities for Events, Tours, and Subscriptions to store product and user entitlement data.**
    *   *Acceptance Criteria:*
        1. A Flyway migration creates an `events` table (id, title, description, cover_photo_url, start_date, end_date, etc.).
        2. A Flyway migration creates a `tours` table (id, event_id, price, capacity, name, description, thumbnail_url, duration, etc.).
        3. A Flyway migration creates a `subscriptions` table (id, name, description, price, duration_days, product_id).
        4. A Flyway migration script modifies the `users` table to add a `subscription_id` foreign key and a `subscription_expires_at` timestamp.
        5. JPA entities are created and correctly mapped to these tables.

*   **Story 5.2: As a Mobile Client, I want to browse paginated lists of available Tours and Events so that I can discover bird-watching opportunities.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/events` endpoint is created that returns a paginated list of events.
        2. A `GET /api/v1/tours` endpoint is created that returns a paginated list of tours.
        3. The responses are read-only and do not need to be heavily cached, as this data is not considered high-traffic for the MVP.
        4. The endpoints are secured with JWT authentication.

*   **Story 5.3: As a Standard Mobile User, I want to fetch a list of available subscription plans so that I can choose one to purchase.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/subscriptions` endpoint is created.
        2. The endpoint returns a list of all available subscription plans (e.g., "ExBird") from the `subscriptions` table.
        3. The endpoint is secured with JWT authentication.

*   **Story 5.4: As a Standard Mobile User, I want to initiate a payment for the "ExBird" subscription via PayOS so that I can upgrade my account.**
    *   *Acceptance Criteria:*
        1. A `POST /api/v1/payos/create-payment-link` endpoint is created that accepts a product ID (e.g., "sub_premium").
        2. The service uses the PayOS API client to create a new payment link with a unique order code, price, description, and configured `returnUrl` and `cancelUrl`.
        3. The unique order code and associated `user_id` are temporarily stored (e.g., in Redis or a `pending_payments` table) with a `PENDING` status.
        4. The endpoint returns a `200 OK` with the `checkoutUrl` provided by PayOS.
        5. The endpoint is secured with JWT authentication.

*   **Story 5.5: As the System, I want a public webhook endpoint to securely receive and process payment status updates from PayOS so that I can automatically activate user subscriptions.**
    *   *Acceptance Criteria:*
        1. A `POST /api/v1/webhooks/payos` endpoint is created that is publicly accessible (not protected by JWT).
        2. The webhook handler first verifies the integrity of the incoming request by checking the checksum/signature provided by PayOS against the configured PayOS checksum key.
        3. If the signature is valid, the handler parses the payment status from the webhook body.
        4. If the payment status is `PAID`, the handler retrieves the user associated with the `orderCode`, updates their `subscription_id` and `subscription_expires_at` in the `users` table.
        5. The handler publishes a `SubscriptionActivatedEvent` to RabbitMQ for the notification system (Epic 6).
        6. The webhook returns a `200 OK` response to PayOS to acknowledge successful receipt.
        7. An integration test simulates a valid and an invalid webhook call to verify the signature validation and business logic.

#### Epic 6: Asynchronous Notifications
**Epic Goal:** Implement a dedicated RabbitMQ consumer worker that listens for domain events (e.g., `PostLikedEvent`, `SubscriptionActivatedEvent`, `NewCommentEvent`) and creates user notifications, completely decoupling notification logic from the main API request thread. This epic proves you can design a scalable, non-blocking notification system, a common requirement in any high-traffic social or e-commerce platform.

*   **Story 6.1: As a Backend Developer, I want to create the database schema and JPA entity for storing user notifications.**
    *   *Acceptance Criteria:*
        1. A Flyway migration creates a `user_notifications` table (id, user_id, message, type, is_read, created_at).
        2. The `user_id` column is a foreign key to the `users` table.
        3. An index is created on `user_id` and `is_read` to optimize queries for fetching unread notifications.
        4. A JPA entity (`Notification`) is created and correctly mapped to this table.

*   **Story 6.2: As a Backend Developer, I want to refactor the Community Feed and PayOS services to publish specific domain events to RabbitMQ.**
    *   *Acceptance Criteria:*
        1. When a user likes a post, the `POST /api/v1/posts/{postId}/reactions` endpoint publishes a `PostLikedEvent` (containing `postId`, `likerUserId`, `postOwnerUserId`) to a dedicated `notifications.exchange`.
        2. When a user comments on a post, the `POST /api/v1/posts/{postId}/comments` endpoint publishes a `NewCommentEvent` (containing `postId`, `commenterUserId`, `postOwnerUserId`, `commentContent`) to the `notifications.exchange`.
        3. When the PayOS webhook successfully processes a `PAID` status, it publishes a `SubscriptionActivatedEvent` (containing `userId`) to the `notifications.exchange`.
        4. Integration tests are updated to verify that these events are published to the Testcontainers RabbitMQ instance when the corresponding actions are performed.

*   **Story 6.3: As the System, I want a dedicated Notification Worker to consume domain events and generate user notifications.**
    *   *Acceptance Criteria:*
        1. A new Spring Boot application or profile is created for the `notification-worker`.
        2. A `@RabbitListener` is configured to consume messages from a `notifications-queue` which is bound to the `notifications.exchange`.
        3. The listener correctly deserializes the different event types (`PostLikedEvent`, `NewCommentEvent`, `SubscriptionActivatedEvent`).
        4. For a `PostLikedEvent`, the worker creates a new record in the `user_notifications` table for the `postOwnerUserId` with a message like "[Liker's Name] liked your post."
        5. For a `NewCommentEvent`, the worker creates a new notification for the `postOwnerUserId` with a message like "[Commenter's Name] commented on your post."
        6. For a `SubscriptionActivatedEvent`, the worker creates a new notification for the `userId` with a message like "Welcome to ExBird! Your premium subscription is now active."
        7. The worker includes retry logic and a Dead-Letter Queue (DLQ) for handling processing failures.

*   **Story 6.4: As an Authenticated Mobile User, I want to fetch my notifications so that I can see recent activity related to my account.**
    *   *Acceptance Criteria:*
        1. A `GET /api/v1/notifications` endpoint is created.
        2. The endpoint queries the `user_notifications` table for the authenticated user's ID, returning a paginated list ordered by `created_at` descending.
        3. The response format matches the `PaginatedNotificationsResponse` model expected by the Android app.
        4. The endpoint is secured with JWT authentication.

