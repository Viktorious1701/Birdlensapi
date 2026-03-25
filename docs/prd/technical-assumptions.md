# Technical Assumptions

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
