# High Level Architecture

## System Overview

The system is a **modular monolith with background workers**, packaged as a single Spring Boot application that can be activated with different Spring profiles to isolate responsibilities:

- **`api` profile** — Handles all synchronous HTTP traffic from the Android client
- **`worker` profile** — Runs `@RabbitListener` consumers for image processing and notifications

Both profiles share the same codebase and JPA entity model, but activate different `@Configuration` classes and `@Component` beans. In production, they can be deployed as separate container instances from the same Docker image by passing `--spring.profiles.active=worker`.

## Architecture Diagram

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

## Request Flow — Post Creation (Happy Path)

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

## Request Flow — Nearby Hotspots (Cache Hit)

```
Client → GET /api/v1/hotspots/nearby?lat=10.78&lng=106.70&radiusKm=10
       → Spring Security validates JWT
       → HotspotService.findNearby(lat, lng, radiusKm)
          → Cache key: "hotspots:10.78:106.70:10"
          → Redis hit → return cached list  (< 50ms)
          → Redis miss → PostGIS ST_DWithin query → cache result (TTL 1h) → return
```

---
