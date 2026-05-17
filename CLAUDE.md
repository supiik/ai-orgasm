# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

Four independent sub-projects sharing a root directory:

| Directory | Type | Artifact |
|-----------|------|----------|
| `sdk/` | Java 25 library | `orgasm-sdk` JAR – consumed by `backend` and `lambda` |
| `backend/` | Spring Boot 4 app | Fat JAR, serves REST on `:8080` |
| `lambda/` | AWS Serverless (SAM) | Fat JAR via shade plugin, deployed through `template.yaml` |
| `ui/` | Vue 3 + Vite 6 SPA | Built to `ui/dist/`, Node 22 |

`sdk` is always built first; the parent POM declares modules in dependency order: `sdk → backend → lambda`.

## Java build commands

All commands run from the repo root unless noted.

```bash
# Build everything (sdk → backend → lambda)
mvn install

# Build a single module without running tests
mvn install -pl sdk -am -DskipTests
mvn install -pl backend -am -DskipTests
mvn install -pl lambda -am -DskipTests

# Run all Java tests
mvn test

# Run tests in a specific module
mvn test -pl sdk
mvn test -pl backend
mvn test -pl lambda

# Run a single test class
mvn test -pl backend -Dtest=HealthControllerTest

# Run the backend
mvn spring-boot:run -pl backend

# Package Lambda fat JAR for deployment
mvn package -pl lambda -am

# Run integration tests (requires Docker — Testcontainers spins up MariaDB containers)
mvn verify -pl backend

# Run integration tests only, skipping unit tests
mvn failsafe:integration-test failsafe:verify -pl backend -DskipTests
```

No preview features are used; `--enable-preview` is absent from the build.

**Test separation:** Unit tests (`*Test.java`) run via Surefire on `mvn test`. Integration tests (`*IT.java`) run via Failsafe on `mvn verify`. PiTest excludes `*IT` classes from mutation analysis. The Spring Boot Maven Plugin uses `classifier: exec` so Failsafe can load classes from the plain JAR (the fat JAR's `BOOT-INF/classes/` layout is not on the test classpath).

## Mutation testing (PIT)

PIT is bound to the `verify` phase in the parent POM alongside JaCoCo — it runs automatically on `mvn verify`.

```bash
# Full verify: compiles, tests, JaCoCo coverage + PiTest mutation (all modules)
mvn verify

# Skip mutation testing for a faster feedback loop
mvn verify -Dpitest.skip=true

# Daily quality gate: coverage + mutation with 80% line-coverage enforcement
# Fails the build if any module drops below 80% line coverage
mvn verify -Pdaily

# Run mutation coverage in isolation for a single module
mvn pitest:mutationCoverage -pl sdk

# Reports land at target/pit-reports/index.html (timestamped dirs disabled)
```

## API clients

Four generated client SDKs are built from a single canonical spec at `api-spec/backend-api.yaml`. All use `openapi-generator-maven-plugin` (version in root `pom.xml` as `openapi-generator.version`). Generated sources land in each module's `target/generated-sources/openapi/`.

| Module | Artifact | Target | HTTP library | Package prefix |
|--------|----------|--------|--------------|----------------|
| `sdk/` | `orgasm-sdk` | Java 25 | `java.net.http.HttpClient` (native) | `com.orgasm.sdk.client` |
| `sdk-java11/` | `orgasm-sdk-java11` | Java 11+ | `java.net.http.HttpClient` (native) | `com.orgasm.sdk.java11` |
| `sdk-java8/` | `orgasm-sdk-java8` | Java 8+ | OkHttp 4 + Gson | `com.orgasm.sdk.java8` |
| `sdk-typescript/` | npm `@orgasm/backend-client` | TypeScript/ESM | Axios | `api/`, `model/` |

**Usage (Java 25 / Java 11):**
```java
ApiClient client = new ApiClient();
client.updateBaseUri("http://localhost:8080");

PlaylistsApi playlists = new PlaylistsApi(client);
PlaylistPage page = playlists.findAllPlaylists(0, 20, "id");
Playlist created = playlists.createPlaylist(new Playlist().name("My list"));
```

**Usage (Java 8):**
```java
ApiClient client = new ApiClient().setBasePath("http://localhost:8080");

PlaylistsApi playlists = new PlaylistsApi(client);
PlaylistPage page = playlists.findAllPlaylists(0, 20, "id");
```

**Usage (TypeScript):**
```bash
# Generate the TypeScript client
mvn generate-sources -pl sdk-typescript

# The generated package is in sdk-typescript/target/typescript-client/
cd sdk-typescript/target/typescript-client
npm install && npm run build
```

**Updating the spec:** The canonical spec is `api-spec/backend-api.yaml`. Refresh it after backend API changes, then rebuild all clients:

```bash
# With backend running on :8080
curl -s http://localhost:8080/v3/api-docs.yaml > api-spec/backend-api.yaml

# Rebuild all SDK modules
mvn install -pl sdk,sdk-java8,sdk-java11,sdk-typescript -am -DskipTests
```

**Compiler targets for client modules:** `sdk-java8` compiles with `--release 8` (OkHttp, no preview features); `sdk-java11` compiles with `--release 11` (native HttpClient, no preview features); `sdk/` (Java 25) and `sdk-typescript/` use the parent defaults. PiTest is skipped for all three generated-code modules.

## Local development (backend)

### Environment setup

Docker Compose credentials are loaded from `backend/.env` (gitignored). Copy the template on first checkout:

```bash
cp backend/.env.example backend/.env
```

### Running from VS Code

The `.vscode/launch.json` configuration sets `cwd` to `backend/` (so Docker Compose finds `compose.yml`), activates the `dev` profile, and loads `backend/.env`:

```json
{
  "cwd": "${workspaceFolder}/backend",
  "vmArgs": "-Dspring.profiles.active=dev",
  "envFile": "${workspaceFolder}/backend/.env"
}
```

The `dev` profile (`application-dev.yml`) sets `lifecycle-management: start-only` so Docker Compose containers keep running between app restarts — data in named volumes is preserved across restarts.

### API explorer

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the backend is running.

## UI commands

```bash
cd ui

npm install          # first time
npm run dev          # Vite dev server on :5173, proxies /api → :8080
npm run build        # type-check + production build → ui/dist/
npm run type-check   # vue-tsc only, no emit
npm run lint         # ESLint
npm run test:unit    # Vitest
```

Vite proxies `/api/*` to `http://localhost:8080`, so the backend must be running for API calls to work in dev mode.

## Lambda local testing

Requires [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html).

```bash
# Build fat JAR first
mvn package -pl lambda -am -DskipTests

# Start local API Gateway
cd lambda && sam local start-api

# Invoke a single function directly
sam local invoke HelloFunction --event events/hello.json
```

## Architecture decisions

### SDK as the shared contract
`sdk` contains only pure Java (no Spring, no Lambda SDK). Both `backend` and `lambda` depend on it. Put shared models, interfaces, and utilities here. Never add framework-specific code to `sdk`.

### Spring Boot BOM imported, not inherited
The root `pom.xml` imports `spring-boot-dependencies` as a BOM inside `<dependencyManagement>`. This lets `lambda` avoid pulling Spring Boot transitive dependencies while still benefiting from version alignment for Jackson/SLF4J etc.

### Lambda packaging
`lambda` uses `maven-shade-plugin` to produce a single fat JAR. The handler class is referenced directly in `template.yaml` (`Handler: com.orgasm.lambda.HelloHandler::handleRequest`). New Lambda functions follow the same pattern: implement `RequestHandler<IN, OUT>`, add a new `AWS::Serverless::Function` resource in `template.yaml`.

### CORS
`WebConfig` allows `http://localhost:5173` (Vite dev server) for all `/api/**` routes. For production, update the allowed origins in `backend/src/main/resources/application.yml` or override via environment variable.

### Multi-datasource JPA (backend)
The backend uses two physically separate MariaDB databases, each with its own `DataSource`, `EntityManagerFactory`, and `JpaTransactionManager`:

| Bean qualifier | Database | Port | Purpose |
|----------------|----------|------|---------|
| `appDataSource` / `appEntityManagerFactory` | `orgasm` | 3306 | Application data |
| `billingDataSource` / `billingEntityManagerFactory` | `orgasm_billing` | 3307 | Billing data |

**Package layout for entities and repositories:**
- `com.orgasm.backend.domain.app` — `@Entity` classes for the app database
- `com.orgasm.backend.domain.billing` — `@Entity` classes for the billing database
- `com.orgasm.backend.repository.app` — Spring Data repositories (use `appTransactionManager`)
- `com.orgasm.backend.repository.billing` — Spring Data repositories (use `billingTransactionManager`)

**Base entity:** All entities should extend `com.orgasm.backend.domain.AuditableEntity` (`@MappedSuperclass`), which provides:
- `version` — optimistic locking via `@Version`
- `createdAt` — set once on insert via `@CreatedDate`
- `updatedAt` — updated on every save via `@LastModifiedDate`

**Flyway:** Schema migrations are managed per-database:
- `classpath:db/migration/app/` — app database migrations
- `classpath:db/migration/billing/` — billing database migrations

`DataSourceAutoConfiguration` and `FlywayAutoConfiguration` are excluded from Spring Boot auto-config — all datasource and migration setup is manual. `HibernateJpaAutoConfiguration` is intentionally kept active so it provides the shared `EntityManagerFactoryBuilder`.

**Flyway → Hibernate ordering:** `AppJpaConfig` and `BillingJpaConfig` declare Flyway as an optional parameter so Spring enforces Flyway runs before `EntityManagerFactory` initializes (which triggers `ddl-auto: validate`). Never use `@DependsOn` for this — it fails when Flyway beans are absent (e.g. test profile with `datasource.flyway.enabled=false`).

```java
@Bean @Primary
LocalContainerEntityManagerFactoryBean appEntityManagerFactory(
        @Qualifier("appDataSource") DataSource dataSource,
        EntityManagerFactoryBuilder builder,
        @Autowired(required = false) @Qualifier("appFlyway") Flyway appFlyway) { ... }
```

**Flyway conditional:** `FlywayConfig` is guarded by `@ConditionalOnProperty(name = "datasource.flyway.enabled", havingValue = "true", matchIfMissing = true)`. Set `datasource.flyway.enabled=false` in test profiles that use H2 or Testcontainers with pre-created schemas.

**Repositories must always declare their transaction manager explicitly:**
```java
@Transactional("appTransactionManager")      // for app repos
@Transactional("billingTransactionManager")  // for billing repos
```

### Request/Response DTOs and MapStruct

The API layer uses dedicated DTOs — never domain entities directly:

| Class | Role |
|-------|------|
| `CreatePlaylistRequest` | POST body — no `id`, no audit fields |
| `UpdatePlaylistRequest` | PUT body — no `id`, no audit fields |
| `PlaylistResponse` | All GET/POST/PUT responses — includes `id` and audit fields |

Mapping between entity and DTOs is handled by `PlaylistMapper` (MapStruct, `componentModel = "spring"`). The mapper is injected into `PlaylistService`; controllers never touch entities.

**Annotation processor order** (critical with Lombok + MapStruct): Lombok must run before MapStruct so it generates the getters/setters that MapStruct reads. The root POM's `<pluginManagement>` puts Lombok first; `backend/pom.xml` appends the binding artifact + MapStruct processor with `combine.children="append"`:

```xml
<annotationProcessorPaths combine.children="append">
    <path>lombok-mapstruct-binding</path>   <!-- ordering constraint -->
    <path>mapstruct-processor</path>         <!-- after Lombok -->
</annotationProcessorPaths>
```

`unmappedTargetPolicy = ReportingPolicy.IGNORE` on the mapper suppresses warnings for JPA-managed audit fields (`version`, `createdAt`, `updatedAt`, `deletedAt`) which have no setters and are intentionally skipped.

### Integration tests (Testcontainers)

Backend integration tests (`*IT.java`) start real `mariadb:12.2.2` containers via Testcontainers and run Flyway migrations against them. Use `@DynamicPropertySource` to override datasource URLs, credentials, driver class, and enable Flyway:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Transactional("appTransactionManager")   // auto-rollback per test
class SomeRepositoryIT {
    @Container
    static MariaDBContainer<?> appDb = new MariaDBContainer<>("mariadb:12.2.2")
            .withDatabaseName("orgasm").withUsername("orgasm").withPassword("orgasm");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.app.url", appDb::getJdbcUrl);
        registry.add("datasource.app.username", appDb::getUsername);
        registry.add("datasource.app.password", appDb::getPassword);
        registry.add("datasource.app.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("datasource.flyway.enabled", () -> "true");
        // also override billing datasource if the app context requires it
    }
}
```

Always override `driver-class-name` — the test profile sets it to `org.h2.Driver` which conflicts with MariaDB JDBC URLs.

## Key version pins

| Technology | Version |
|------------|---------|
| Java | 25 |
| Spring Boot | 4.0.6 |
| AWS SDK v2 | 2.31.0 |
| Node | 22 LTS (enforced via `engines` in `package.json`) |
| Vue | 3.5.x |
| Vite | 6.x |
| MapStruct | 1.6.3 |
| Lambda runtime | `java21` (Graviton arm64) |

All Java dependency versions are managed centrally in the root `pom.xml` `<properties>` block. Update versions there, not in individual module POMs.
