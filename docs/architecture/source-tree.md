# Source Tree

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
