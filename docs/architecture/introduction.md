# Introduction

## Purpose

This document defines the technical architecture for the Birdlens backend system — a Spring Boot-based REST API and event-driven processing layer that serves the Birdlens Android application. It is intended to guide implementation across all epics and serve as the authoritative technical reference for the BMAD dev agent during story execution.

## Scope

This architecture covers the MVP backend system including:

- Core REST API (authentication, community feed, hotspots, tours, subscriptions)
- eBird data ingestion pipeline
- Asynchronous media processing via RabbitMQ
- Notification delivery worker
- Local development infrastructure via Docker Compose
- PostgreSQL with PostGIS for spatial queries

**Out of scope for this MVP:** Kubernetes orchestration, Terraform provisioning, Grafana/Prometheus observability stack, and AI-based bird identification. These are deferred to a post-MVP phase.

## Key Design Decisions

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
