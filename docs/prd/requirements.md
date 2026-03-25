# Requirements

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
