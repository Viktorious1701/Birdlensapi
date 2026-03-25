# Components

## Package Structure

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

## Key Component Responsibilities

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
