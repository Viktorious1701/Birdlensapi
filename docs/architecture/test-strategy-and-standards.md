# Test Strategy and Standards

## Test Pyramid

```
         [E2E / Contract Tests]         ← minimal; API contract smoke tests only
       [Integration Tests - Testcontainers]   ← primary confidence layer
     [Unit Tests - JUnit + Mockito]            ← fast; business logic isolation
```

## Unit Tests

- Located in `src/test/java/.../unit/`
- Use Mockito to mock all dependencies; test a single class in isolation
- Required for: `JwtService`, `HotspotService` (cache logic), `PostService` (state transitions), `PayOSService` (checksum validation logic), all `@Scheduled` job logic
- Do not use `@SpringBootTest` — these tests should run in milliseconds

## Integration Tests

- Located in `src/test/java/.../integration/`
- Extend a shared `AbstractIntegrationTest` base class that starts Testcontainers for PostgreSQL, Redis, and RabbitMQ using `@Testcontainers` and `@Container`
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` or `MockMvc`
- Required scenarios:
  - Auth registration, duplicate rejection, login, token validation
  - Hotspot spatial query — verify point inside radius included, point outside excluded
  - Redis cache — verify second call does not hit database (use `@SpyBean` on repository)
  - Post creation — verify DB record exists AND message published to RabbitMQ queue
  - PayOS webhook — valid checksum triggers subscription update; invalid checksum returns 400

## Testcontainers Base Class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgis/postgis:16-3.4");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }
}
```

---
