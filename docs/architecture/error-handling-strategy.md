# Error Handling Strategy

## Global Exception Handler

A `@RestControllerAdvice` class (`GlobalExceptionHandler`) maps all exceptions to a consistent JSON response shape:

```json
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Hotspot with id 'L123456' not found"
  },
  "timestamp": "2026-03-24T10:00:00Z"
}
```

| Exception | HTTP Status | Error Code |
|---|---|---|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `ConflictException` | 409 | `CONFLICT` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `AuthenticationException` | 401 | `UNAUTHORIZED` |
| Any uncaught `Exception` | 500 | `INTERNAL_ERROR` (generic message only, no stack trace in response) |

## RabbitMQ Dead Letter Queue

Both worker consumers are configured with a retry policy before rejecting to the DLQ:

```java
// In RabbitMQConfig.java
@Bean
SimpleRabbitListenerContainerFactory containerFactory(...) {
    factory.setDefaultRequeueRejected(false);  // Do not requeue on exception
    // Retry 3 times with 2s backoff before sending to DLQ
    factory.setAdviceChain(RetryInterceptorBuilder.stateless()
        .maxAttempts(3)
        .backOffOptions(2000, 2.0, 10000)
        .build());
}
```

**DLQ monitoring:** Check `rabbitmq_management` UI at `localhost:15672` (guest/guest) during local development to inspect failed messages.

## eBird Ingestion Failures

The ingestion jobs use a try-catch per batch and log failures without halting the overall job. A complete failure of the eBird API results in a log warning but does not affect the API's ability to serve from the existing local dataset (satisfying NFR2).

---
