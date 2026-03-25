# Security

## Authentication and Authorization

- Passwords hashed with BCrypt, strength factor 12
- JWT access tokens expire in 15 minutes; refresh tokens in 7 days
- JWT secret must be at least 256 bits; loaded from environment variable, never from `application.yml` in source control
- All endpoints require a valid JWT except: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/webhooks/payos`, `GET /api/v1/health`
- Premium-only endpoints (`GET /api/v1/hotspots/{locId}/visiting-times`) validate `subscription_type` in the service layer and throw `AccessDeniedException` for standard users

## Input Validation

- All request bodies validated via `@Valid` and Bean Validation; validation errors return `400 Bad Request`
- Path variables and query params validated with `@Validated` + constraint annotations at the controller level
- No raw SQL concatenation; all queries use JPA JPQL or `JdbcTemplate` parameterized queries (no SQL injection risk)

## Webhook Security

- PayOS webhook endpoint verifies HMAC-SHA256 checksum of the raw request body against the configured `PAYOS_CHECKSUM_KEY`
- Verification must happen before any business logic is executed
- On checksum mismatch, return `400 Bad Request` immediately

## Secrets Management

- All secrets in environment variables; `.env` files are gitignored
- `.env.example` file committed to repository with placeholder values only
- No credentials committed to version control under any circumstance

## CORS

- CORS configured in `SecurityConfig` to allow only the Android app's registered origins (not `*`) in production
- During local development, CORS permits all origins for convenience

---

*End of architecture document. This file is intended to be sharded by BMAD tooling using `##` headings as section boundaries.*
