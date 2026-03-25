# Core Workflows

## eBird Taxonomy Ingestion

```
TaxonomyIngestionJob (@Scheduled, weekly)
  → Check bird_taxonomy row count
  → If empty OR scheduled run: call EbirdApiClient.fetchTaxonomy()
  → Receive List<TaxonomyDto> (up to 17,000 records)
  → Partition into batches of 500
  → JdbcTemplate.batchUpdate() with UPSERT SQL per batch
  → Log: start time, records processed, duration, any errors
  → On partial failure: log failed batch, continue remaining batches (best-effort)
```

## Community Feed with Caching

```
GET /api/v1/posts?page=0&size=20&sort=latest

PostService.getFeed(pageable)
  → Redis cache key: "feed:{page}:{size}:{sort}"
  → Cache TTL: 5 minutes (feed is acceptable to be slightly stale)
  → Cache miss: JPA query joining posts + users + post_media (WHERE status = 'COMPLETED')
  → Return only thumbnail_url (not original_url) in response
  → Post-like/comment counts fetched via COUNT subquery in the same query
```

## JWT Authentication Flow

```
POST /api/v1/auth/login { email, password }
  → Load UserDetails by email
  → BCrypt.matches(rawPassword, storedHash)
  → On success: JwtService.generateAccessToken(userId, role)  [15 min expiry]
              + JwtService.generateRefreshToken(userId)       [7 day expiry]
  → Return { access_token, refresh_token }

Subsequent requests:
  → JwtAuthFilter extracts Bearer token from Authorization header
  → JwtService.validateToken(token) → extract userId
  → Set SecurityContextHolder with authenticated principal
  → Downstream controllers call SecurityContextHolder.getContext().getAuthentication()
```

---
