# Data Models

## Entity Relationship Overview

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

## Core Tables

### `users`
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

### `posts`
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

### `post_media`
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

### `ebird_hotspots`
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

### `bird_taxonomy`
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

### `user_notifications`
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

## JPA Entity Conventions

- All entities use `@UuidGenerator` strategy for primary keys
- Auditing columns (`created_at`, `updated_at`) use `@CreationTimestamp` / `@UpdateTimestamp`
- Spatial columns (PostGIS `GEOGRAPHY`) mapped via Hibernate Spatial with `@Column(columnDefinition = "GEOGRAPHY(Point, 4326)")`
- Enums stored as `@Enumerated(EnumType.STRING)` — never `ORDINAL`
- No bidirectional `@OneToMany` unless explicitly needed; prefer repository queries

---
