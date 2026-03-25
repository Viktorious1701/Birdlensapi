# External APIs

## eBird API v2

**Base URL:** `https://api.ebird.org/v2/`  
**Auth:** Request header `x-ebirdapitoken: {API_KEY}` (configured via `app.ebird.api-key` property)

| Endpoint | Used by | Schedule |
|---|---|---|
| `GET /ref/taxonomy/ebird?fmt=json` | `TaxonomyIngestionJob` | Weekly (or on startup if table empty) |
| `GET /ref/hotspot/geo?lat={lat}&lng={lng}&dist={km}&fmt=json` | `HotspotIngestionJob` | Every 6 hours, per configured region list |

**Resilience4j configuration (applied to WebClient):**
```yaml
resilience4j:
  retry:
    instances:
      ebirdClient:
        max-attempts: 3
        wait-duration: 2s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
```

**eBird Upsert pattern:** Both ingestion jobs use `JdbcTemplate.batchUpdate()` with an `INSERT ... ON CONFLICT (species_code) DO UPDATE` statement. This avoids N+1 save calls for potentially 17,000+ taxonomy records.

## PayOS

**Base URL:** Configured via `app.payos.base-url` property  
**Auth:** API key in header; HMAC-SHA256 checksum validation on all webhook payloads

**Webhook flow:**
1. `POST /api/v1/webhooks/payos` receives event payload
2. `PayOSService.verifyChecksum(payload, signature)` validates integrity using the configured `app.payos.checksum-key`
3. If `status == "PAID"`, look up `orderCode` in Redis (set during payment link creation with TTL 24h), retrieve `userId`, update user subscription fields, publish `SubscriptionActivatedEvent`
4. Always return `200 OK` to PayOS regardless of business logic outcome (prevents PayOS retry storms)

---
