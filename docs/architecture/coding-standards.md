# Coding Standards

## General Java Rules

- Target Java 21; use `record` for DTOs and event payloads, `sealed interface` for discriminated union types if needed
- No `var` for non-obvious types; use explicit types for method parameters and return types
- Prefer constructor injection over field injection (`@Autowired` on fields is forbidden)
- `@Transactional` belongs on the service layer, never on the controller or repository
- Never return `null` from a service method; use `Optional<T>` or throw a typed exception
- All `@RestController` methods must declare `@RequestMapping` with explicit HTTP method and path

## Naming Conventions

| Artifact | Convention | Example |
|---|---|---|
| Packages | `com.birdlens.api.{domain}` | `com.birdlens.api.post` |
| Classes | `PascalCase` | `PostCreationService` |
| Methods | `camelCase`, verb-first | `findNearbyHotspots`, `publishPostCreatedEvent` |
| Constants | `UPPER_SNAKE_CASE` | `MAX_FEED_PAGE_SIZE` |
| DB tables | `snake_case`, plural | `post_reactions` |
| DB columns | `snake_case` | `species_code` |
| RabbitMQ queues | `kebab-case` | `image-processing-queue` |
| Cache keys | `domain:param1:param2` | `hotspots:10.78:106.70:10` |
| DTO records | Suffix `Request` or `Response` | `CreatePostRequest`, `PostResponse` |

## API Design Rules

- All endpoints versioned under `/api/v1/`
- Use standard HTTP verbs: `GET` (read), `POST` (create), `PUT` (replace), `PATCH` (partial update), `DELETE`
- Return `201 Created` with the created resource body on successful POST
- Pagination via `?page=0&size=20` using Spring Data `Pageable`; response wrapped in `Page<T>`
- Validation on all request bodies using `@Valid` + Bean Validation annotations (`@NotNull`, `@Email`, `@Size`, etc.)
- Never expose `password_hash`, internal IDs of other users, or raw exception stack traces in responses

## Flyway Migration Rules

- Never modify an existing migration file; always create a new version
- Migration scripts are idempotent where possible (use `IF NOT EXISTS`, `ON CONFLICT DO NOTHING`)
- Each migration has a single responsibility (one table or one index batch per file)
- Migration filenames: `V{n}__{description_in_snake_case}.sql`

---
