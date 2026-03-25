# Epic List

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
