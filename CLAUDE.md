# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

Five independent sub-projects sharing a root directory:

| Directory | Type | Artifact |
|-----------|------|----------|
| `sdk/` | Java 25 library | `orgasm-sdk` JAR – consumed by `backend` and `lambda` |
| `backend-core/` | Spring Boot 4 library | `orgasm-backend-core` JAR – service layer, JPA, Flyway, datasource config; shared by `backend` and `lambda` |
| `backend/` | Spring Boot 4 app | Fat JAR, serves REST on `:8080`; thin web layer only |
| `lambda/` | AWS Serverless (SAM) | Fat JAR via shade plugin, deployed through `template.yaml` |
| `ui/` | Vue 3 + Vite 6 SPA | Built to `ui/dist/`, Node 22 |

The parent POM declares modules in dependency order: `sdk → backend-core → backend → lambda → ui`.

## Build commands

A Maven wrapper (`mvnw` / `mvnw.cmd`) is committed at the repo root — use it instead of a system `mvn` to guarantee the pinned Maven version. All commands run from the repo root unless noted.

```bash
# Build everything (sdk → backend → lambda → ui)
./mvnw install

# Build a single module without running tests
./mvnw install -pl sdk -am -DskipTests
./mvnw install -pl backend-core -am -DskipTests
./mvnw install -pl backend -am -DskipTests
./mvnw install -pl lambda -am -DskipTests
./mvnw install -pl ui -am -DskipTests   # skips npm build + Playwright

# Run all Java tests
./mvnw test

# Run tests in a specific module
./mvnw test -pl sdk
./mvnw test -pl backend-core
./mvnw test -pl backend
./mvnw test -pl lambda

# Run a single test class
./mvnw test -pl backend -Dtest=HealthControllerTest

# Run the backend
./mvnw spring-boot:run -pl backend

# Package Lambda fat JAR for deployment
./mvnw package -pl lambda -am

# Run integration tests (requires Docker — Testcontainers spins up MariaDB containers)
./mvnw verify -pl backend

# Run integration tests only, skipping unit tests
./mvnw failsafe:integration-test failsafe:verify -pl backend -DskipTests

# Build and test the UI only (npm build + Playwright e2e)
./mvnw verify -pl ui
```

No preview features are used; `--enable-preview` is absent from the build.

**Test separation:** Unit tests (`*Test.java`) run via Surefire on `mvn test`. Integration tests (`*IT.java`) run via Failsafe on `mvn verify`. PiTest excludes `*IT` classes from mutation analysis. The Spring Boot Maven Plugin uses `classifier: exec` so Failsafe can load classes from the plain JAR (the fat JAR's `BOOT-INF/classes/` layout is not on the test classpath).

## Mutation testing (PIT) and coverage gate

PIT and JaCoCo are both bound to the `verify` phase in the parent POM — they run automatically on `mvn verify`. The JaCoCo `check` execution enforces a **per-module line-coverage gate** of 80%, configurable via the `jacoco.line-coverage-minimum` property. Generated-code modules (`sdk`, `sdk-models`, `sdk-java8`, `sdk-java11`) override this property to `0` in their POMs.

```bash
# Full verify: compiles, tests, JaCoCo coverage + gate + PiTest mutation (all modules)
mvn verify

# Skip mutation testing for a faster feedback loop (coverage gate still enforced)
mvn verify -Dpitest.skip=true

# Bypass the coverage gate for a one-off run (e.g. exploratory work)
mvn verify -Djacoco.line-coverage-minimum=0

# Run mutation coverage in isolation for a single module
mvn pitest:mutationCoverage -pl backend

# Reports: target/site/jacoco/index.html and target/pit-reports/index.html
```

### Reading line coverage from JaCoCo CSV

JaCoCo writes `target/site/jacoco/jacoco.csv` after `mvn verify`. This one-liner prints a per-module summary:

```bash
for m in sdk-models sdk sdk-java8 sdk-java11 backend lambda; do
  csv=$(find $m/target/site/jacoco -name "jacoco.csv" 2>/dev/null | head -1)
  [ -n "$csv" ] && awk -F',' 'NR>1 { miss+=$8; cov+=$9 } END {
    total=miss+cov; pct=(total>0 ? cov/total*100 : 0);
    printf "%-14s  lines: %d/%d (%.0f%%)\n", module, cov, total, pct
  }' module="$m" "$csv"
done
```

Current baseline (modules with `jacoco.line-coverage-minimum=0` are exempt):

| Module | Line coverage | Notes |
|--------|--------------|-------|
| `backend-core` | 100% | Gate ≥ 80%, currently exceeds; holds all service/JPA code |
| `backend` | 100% | Gate ≥ 80%, currently exceeds; thin web layer only |
| `lambda` | n/a | Gate set to 0; `LambdaApplication`+`SpringContextHolder` are untestable infrastructure |
| `sdk` / `sdk-models` / `sdk-java8` / `sdk-java11` | n/a | Generated code; gate set to 0 in module POM |

## API clients

Five modules are built from a single canonical spec at `api-spec/backend-api.yaml`. All use `openapi-generator-maven-plugin` (version in root `pom.xml` as `openapi-generator.version`). Generated sources land in each module's `target/generated-sources/openapi/`.

| Module | Artifact | Target | HTTP library | Package prefix |
|--------|----------|--------|--------------|----------------|
| `sdk-models/` | `orgasm-sdk-models` | Java 11+ | — (models only, shared) | `com.orgasm.sdk.model` |
| `sdk/` | `orgasm-sdk` | Java 25 | `java.net.http.HttpClient` (native) | `com.orgasm.sdk.client` |
| `sdk-java11/` | `orgasm-sdk-java11` | Java 11+ | `java.net.http.HttpClient` (native) | `com.orgasm.sdk.java11` |
| `sdk-java8/` | `orgasm-sdk-java8` | Java 8+ | OkHttp 4 + Gson | `com.orgasm.sdk.java8` |
| `sdk-typescript/` | npm `@orgasm/backend-client` | TypeScript/ESM | Axios | `api/`, `model/` |

**Model consolidation:** `sdk-models` generates one set of Jackson-annotated model classes (`com.orgasm.sdk.model.*`) shared by `sdk/` and `sdk-java11/`. Both modules set `generateModels=false` and point `modelPackage=com.orgasm.sdk.model`; the generator emits API and supporting files only, importing models from `sdk-models.jar`. `sdk-java8` stays independent: its Gson-annotated models (`@SerializedName`) and the `URLEncoder.encode(String, Charset)` Java 10+ API in generated `toUrlQueryString()` helpers make sharing with a `--release 8` target impossible without custom templates.

**Usage (Java 25 / Java 11):**
```java
ApiClient client = new ApiClient();
client.updateBaseUri("http://localhost:8080");

PlaylistsApi playlists = new PlaylistsApi(client);
PlaylistPage page = playlists.findAllPlaylists(0, 20, "id");
PlaylistResponse created = playlists.createPlaylist(
        CreatePlaylistRequest.builder().name("My list").build());
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

**Builder pattern on generated models:** All Java SDK models carry `@lombok.Builder @lombok.AllArgsConstructor` via `additionalModelTypeAnnotations`. The generator's explicit no-args constructor handles Jackson deserialization; Lombok adds an all-args constructor + `builder()` factory on top. Both annotations are required together — `@Builder` alone skips generating the all-args constructor when any constructor already exists.

```java
// fluent setters (existing generated API)
new PlaylistResponse().id(1L).name("My Mix")

// builder (added via Lombok)
PlaylistResponse.builder().id(1L).name("My Mix").build()
```

**Compiler targets for client modules:** `sdk-java8` compiles with `--release 8` (OkHttp, no preview features); `sdk-java11` compiles with `--release 11` (native HttpClient, no preview features); `sdk/` (Java 25) and `sdk-typescript/` use the parent defaults. PiTest is skipped for all three generated-code modules.

## Local development (backend)

### Environment setup

Docker Compose credentials are loaded from `backend/.env` (gitignored). Copy the template on first checkout:

```bash
cp backend/.env.example backend/.env
```

### Running from VS Code

`.vscode/launch.json` defines two launch configurations:

| Name | What it starts |
|------|---------------|
| `Spring Boot-BackendApplication<orgasm-backend>` | Spring Boot with `dev` profile, loads `backend/.env` |
| `UI (mock)` | `npm run dev:mock` — Vite on `:5173` with MSW mocks, no backend needed |

The Spring Boot config sets `cwd` to `backend/` (so Docker Compose finds `compose.yml`) and loads `backend/.env`. The `dev` profile (`application-dev.yml`) sets `lifecycle-management: start-only` so Docker Compose containers keep running between app restarts — data in named volumes is preserved across restarts.

### API explorer

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the backend is running.

## UI commands

```bash
cd ui

npm install            # first time
npm run dev            # Vite dev server on :5173, proxies /api → :8080 (backend required)
npm run dev:mock       # Vite dev server with MSW mocks — no backend needed
npm run build          # type-check + production build → ui/dist/
npm run type-check     # vue-tsc only, no emit
npm run lint           # ESLint
npm run test:unit      # Vitest unit tests
npm run test:e2e       # Playwright e2e tests (headless, starts Vite in mock mode automatically)
npm run test:e2e:ui    # Playwright interactive UI mode (step-through, time-travel debugging)
```

Vite proxies `/api/*` to `http://localhost:8080` in normal dev mode. In mock mode (`dev:mock`) the proxy is disabled and MSW intercepts all `/api/*` requests in the browser via a Service Worker.

### Node version

Node 22 is required (enforced via `engines` in `package.json`). Use [fnm](https://github.com/Schniz/fnm) to manage versions — it's installed at `~/.local/share/fnm` and configured in `~/.zshrc` and `~/.bashrc`. The `ui/.node-version` file makes fnm switch automatically on `cd ui/`.

### MSW mock layer

`src/mocks/handlers/` contains request handlers for every API endpoint. The in-memory store is seeded with 3 playlists on startup. Adding a new endpoint:
1. Add a handler in `src/mocks/handlers/<domain>.ts`
2. Export it from `src/mocks/handlers/index.ts`

### Playwright e2e tests

Tests live in `ui/e2e/`. The `playwright.config.ts` automatically starts Vite in mock mode (`VITE_MOCK=true`) as the web server before running tests — no manual setup needed.

**Important:** Playlist API tests use `page.evaluate()` (browser-side fetch) rather than Playwright's `request` fixture (Node.js fetch). MSW runs as a Service Worker in the browser, so requests must originate from the browser to be intercepted. The `beforeEach` waits for `navigator.serviceWorker.controller` to be set before making any fetch calls.

### shadcn-vue components

shadcn-vue is configured via `ui/components.json`. Add components with:

```bash
cd ui
npx shadcn-vue@latest add button
npx shadcn-vue@latest add dialog
```

Components are scaffolded into `src/components/ui/`. The `cn()` utility (`src/lib/utils.ts`) merges Tailwind classes and is used by all shadcn components.

**Tailwind v4 theme tokens:** CSS custom properties (e.g. `--background`, `--primary`) are declared in `src/assets/index.css` and registered as Tailwind utility classes via `@theme inline`. This is required in Tailwind v4 — without `@theme inline`, classes like `bg-background` or `border-border` are unknown and cause a build error.

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

### backend-core: shared service layer
`backend-core` is a plain Spring library JAR (no Tomcat, no web) containing:
- JPA entities, Spring Data repositories, MapStruct mappers, and service classes
- `DataSourceConfig`, `AppJpaConfig`, `BillingJpaConfig`, `AuditConfig`, `FlywayConfig`
- Flyway migration scripts (`classpath:db/migration/app/` and `classpath:db/migration/billing/`)

Both `backend` (thin REST layer) and `lambda` depend on `backend-core`. Lambda bootstraps a Spring `ApplicationContext` (no web) via `SpringContextHolder` and calls services directly — avoiding HTTP round-trips between Lambda and backend.

**Lambda Spring bootstrap pattern:**
```java
// SpringContextHolder initializes once on cold start (static block)
// HelloHandler.no-arg constructor pulls beans from context
// Package-private constructor takes mocks for unit tests (never triggers context)
public HelloHandler() {
    var ctx = SpringContextHolder.get();
    this.playlistService = ctx.getBean(PlaylistService.class);
    this.mapper = ctx.getBean(ObjectMapper.class);
}
```

**Lambda JaCoCo gate:** Set to `0` (override in `lambda/pom.xml`) because `LambdaApplication` and `SpringContextHolder` are deployment infrastructure that cannot be unit-tested without a live database.

### Spring Boot BOM imported, not inherited
The root `pom.xml` imports `spring-boot-dependencies` as a BOM inside `<dependencyManagement>`. This lets `lambda` avoid pulling Spring Boot transitive dependencies while still benefiting from version alignment for Jackson/SLF4J etc.

### Lambda packaging
`lambda` uses `maven-shade-plugin` to produce a single fat JAR. The handler class is referenced directly in `template.yaml` (`Handler: com.orgasm.lambda.HelloHandler::handleRequest`). New Lambda functions follow the same pattern: implement `RequestHandler<IN, OUT>`, add a new `AWS::Serverless::Function` resource in `template.yaml`.

### API versioning

The backend uses Spring Boot 4's built-in API versioning (`ApiVersionConfigurer`), configured in `WebConfig`:

- **Strategy**: path-based — version is the first URL segment (e.g., `/v1/playlists`)
- **Parser**: `SemanticApiVersionParser` — strips leading `v`, so `v1` → version `1.0.0`
- **Unversioned paths** (health, actuator, Swagger) pass through because `setVersionRequired(false)` is set and the resolver predicate skips paths whose first segment doesn't match `v\d+`
- **Supported versions** are detected automatically from controller annotations

**Controller pattern:**
```java
@RequestMapping(value = "/v1/playlists", version = "1")
```
The path includes the version prefix; the `version` attribute tells Spring MVC which version this controller serves and is used for supported-version validation.

**Adding v2:** Add a new controller with `value = "/v2/playlists", version = "2"`. Both controllers coexist; requests to `/v1/` and `/v2/` route independently.

**Tests:** Standalone MockMvc tests must configure a version strategy to match production. Use `VersionTestSupport.pathVersionStrategy()` (in `backend/src/test/java`) and pass it to `.setApiVersionStrategy(...)` on the builder.

**Exception handling:** `GlobalExceptionHandler` maps version errors to HTTP responses:
- `MissingApiVersionException` → 400
- `InvalidApiVersionException` (unsupported version) → 400
- `NotAcceptableApiVersionException` → 406

### CORS
`WebConfig` allows `http://localhost:5173` (Vite dev server) for `/v1/**` (all methods) and `/api/**` (GET only, for health). For production, update the allowed origins in `backend/src/main/resources/application.yml` or override via environment variable.

### Multi-datasource JPA (backend)
The backend uses two physically separate MariaDB databases, each with its own `DataSource`, `EntityManagerFactory`, and `JpaTransactionManager`:

| Bean qualifier | Database | Port | Purpose |
|----------------|----------|------|---------|
| `appDataSource` / `appEntityManagerFactory` | `orgasm` | 3306 | Application data |
| `billingDataSource` / `billingEntityManagerFactory` | `orgasm_billing` | 3307 | Billing data |

**Package layout for entities and repositories:**
- `com.orgasm.backend.domain.billing` — `@Entity` classes for the **billing** database
- `com.orgasm.backend.repository.billing` — Spring Data repositories for billing (use `billingTransactionManager`)
- Everything else under `com.orgasm.backend` — entities and repositories for the **app** database

`AppJpaConfig` scans `com.orgasm.backend` broadly for both entities and repositories. `BillingJpaConfig` keeps a narrow scan (`com.orgasm.backend.domain.billing` / `com.orgasm.backend.repository.billing`). This means **new feature packages (e.g. `com.orgasm.backend.song`) are picked up automatically** — no changes to `AppJpaConfig` required when adding a new entity.

`BackendApplicationTests.contextLoads()` serves as the safety net: a new entity whose repository is not visible to the app `EntityManagerFactory` will cause the context load test to fail immediately.

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

**Builder pattern:** All three DTO records carry `@Builder` so callers can use either style:
```java
// canonical constructor
new CreatePlaylistRequest("My Mix", "desc")

// builder
CreatePlaylistRequest.builder().name("My Mix").description("desc").build()
```

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
| Node | 22.22.3 (pinned in root POM as `node.version`; `.node-version` for fnm) |
| Vue | 3.5.x |
| Vite | 6.x |
| Tailwind CSS | 4.x (via `@tailwindcss/vite` plugin) |
| shadcn-vue | configured via `ui/components.json` |
| MSW | 2.x |
| Playwright | 1.x |
| MapStruct | 1.6.3 |
| frontend-maven-plugin | 1.15.1 (pinned in root POM as `frontend-maven-plugin.version`) |
| Lambda runtime | `java21` (Graviton arm64) |

All Java dependency versions are managed centrally in the root `pom.xml` `<properties>` block. Update versions there, not in individual module POMs. `node.version` and `frontend-maven-plugin.version` are also in the root `<properties>` block.
