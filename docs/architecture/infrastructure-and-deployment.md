# Infrastructure and Deployment

## Docker Compose (Local Development)

The single `docker-compose.yml` at the repository root starts all services. Run with:

```bash
docker-compose up -d         # Start all services
docker-compose up api        # Start only API (Postgres, Redis, RabbitMQ, MinIO must be running)
```

**Service startup order** (enforced via `depends_on` + health checks):
1. `postgres` (with PostGIS)
2. `redis`, `rabbitmq`, `minio`
3. `api` (waits for postgres healthy)
4. `worker` (waits for api healthy — ensures Flyway migrations have run)

**MinIO bucket setup:** A `minio-init` one-shot service runs `mc` to create the `birdlens-media` bucket on first startup.

## Application Profiles

| Profile | Activated by | Runs |
|---|---|---|
| `api` | Default, or `--spring.profiles.active=api` | Web server, scheduled jobs, event publishers |
| `worker` | `--spring.profiles.active=worker` | RabbitMQ consumers only; no web server |
| `local` | Combined with above in local dev | Local DB/Redis/MinIO connection strings |
| `test` | Activated by Testcontainers test base class | Spins up real containers; no external dependencies |

## Dockerfile (Multi-Stage)

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The worker container runs the same image with `SPRING_PROFILES_ACTIVE=worker,local` passed as an environment variable in `docker-compose.yml`.

## Environment Variables

All sensitive configuration is externalized. Never hardcode secrets. Required variables:

```