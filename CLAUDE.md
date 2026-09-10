# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

Six independent sub-projects sharing a root directory:

| Directory | Type | Artifact |
|-----------|------|----------|
| `sdk/` | Java 25 library | `orgasm-sdk` JAR – consumed by `backend` |
| `backend-core/` | Spring Boot 4 library | `orgasm-backend-core` JAR – MariaDB/JPA service layer, Flyway, datasource config; used by `backend` |
| `backend-dynamo/` | Spring Boot 4 library | `orgasm-backend-dynamo` JAR – DynamoDB-backed service layer for Playlist; used by `lambda` in place of `backend-core` |
| `backend/` | Spring Boot 4 app | Fat JAR, serves REST on `:8080`; thin web layer only |
| `lambda/` | AWS Serverless (Amplify Gen 2) | Fat JAR via shade plugin, packaged as a container image, deployed via `ui/amplify/backend.ts` (AWS CDK, run through `ampx pipeline-deploy`/`ampx sandbox`); DynamoDB-backed (see [Architecture decisions](#dynamodb-persistence-for-lambda)) |
| `ui/` | Vue 3 + Vite 6 SPA | Built to `ui/dist/`, Node 22 |

The parent POM declares modules in dependency order: `sdk → backend-core → backend-dynamo → backend → lambda → ui`. `backend-dynamo` has no dependency on `backend-core`/`sdk` — it's a fully independent module (see architecture decision below for why).

## Build commands

A Maven wrapper (`mvnw` / `mvnw.cmd`) is committed at the repo root — use it instead of a system `mvn` to guarantee the pinned Maven version. All commands run from the repo root unless noted.

```bash
# Build everything (sdk → backend-core → backend-dynamo → backend → lambda → ui)
./mvnw install

# Build a single module without running tests
./mvnw install -pl sdk -am -DskipTests
./mvnw install -pl backend-core -am -DskipTests
./mvnw install -pl backend-dynamo -am -DskipTests
./mvnw install -pl backend -am -DskipTests
./mvnw install -pl lambda -am -DskipTests
./mvnw install -pl ui -am -DskipTests   # skips npm build + Playwright

# Run all Java tests
./mvnw test

# Run tests in a specific module
./mvnw test -pl sdk
./mvnw test -pl backend-core
./mvnw test -pl backend-dynamo
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

# Run backend-dynamo integration tests (requires Docker — Testcontainers spins up DynamoDB Local)
./mvnw verify -pl backend-dynamo

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
for m in sdk-models sdk sdk-java8 sdk-java11 backend-core backend-dynamo backend lambda; do
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
| `backend-dynamo` | n/a | Gate ≥ 80% (default, hand-written code); DynamoDB-backed Playlist service used by `lambda` |
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

## Native image builds

Both `backend` and `lambda` support GraalVM native image via the `native` Maven profile.
Requires GraalVM JDK 25 (`ghcr.io/graalvm/graalvm-community:25`) — in Docker or locally via `sdk use graalvm-community-25`.

### Backend

```bash
# Build native executable to backend/target/orgasm-backend
./mvnw package -pl backend -am -DskipTests -Pnative

# Or let Spring Boot build an OCI image (requires Docker)
./mvnw spring-boot:build-image -pl backend -Pnative
```

**AOT / database during native build:** `spring-boot:process-aot` starts the Spring context to pre-compute bean factories. `backend` uses the `aot` Spring profile (`application-aot.yml`) which substitutes H2 and disables Flyway so no MariaDB is needed at build time. At runtime, the normal `application.yml` (MariaDB) takes effect.

### Lambda

`lambda` deploys as a plain **JVM** container image (not GraalVM native-image — see "Cognito auth
for the Lambda API" for why that path is parked). The `native` Maven profile is still present in
`lambda/pom.xml` for a future revisit, but is not part of the current build/deploy path; skip
straight to the JVM commands below unless specifically reviving native-image.

```bash
# Build the fat jar (default, non-native profile)
./mvnw package -pl lambda -am -DskipTests

# Build the Lambda container image (build context = repo root; base image
# public.ecr.aws/lambda/java:25 already includes the Lambda runtime interface client)
docker build -f lambda/Dockerfile -t orgasm-lambda:latest .

# For Graviton arm64 (matches ui/amplify/backend.ts's Architecture.ARM_64):
docker buildx build --platform linux/arm64 -f lambda/Dockerfile -t orgasm-lambda:latest .
```

Deployment (pushing this image to ECR + provisioning the 34 Lambda functions/DynamoDB
tables/Cognito User Pool that reference it) happens via `ui/amplify/backend.ts` — see
"Amplify Gen 2 backend" below. There is no separate manual deploy step for `lambda` in normal
operation; `amplify.yml`'s `backend` phase does the image build/push + `ampx pipeline-deploy`
automatically on every push to a branch connected to Amplify Hosting.

**Adding new handlers:** create the handler class (extends `BaseHandler<T>`, same pattern as any
existing one), then add one entry to the `fnSpecs` array in `ui/amplify/functions.ts` (name,
handler class, tables it needs, HTTP methods, CORS headers) — `ui/amplify/backend.ts`'s loop
picks it up automatically, no per-function boilerplate to hand-write.

## Lambda local testing

Two levels, depending on what you're checking:

**Single function, no AWS resources** — run the built image directly via the AWS base image's
built-in [Lambda Runtime Interface Emulator](https://docs.aws.amazon.com/lambda/latest/dg/images-test.html),
overriding `CMD` to pick a handler:

```bash
./mvnw package -pl lambda -am -DskipTests
docker build -f lambda/Dockerfile -t orgasm-lambda:latest .
docker run -d -p 9000:8080 --name orgasm-lambda-test \
  -e AWS_REGION=us-east-1 -e AWS_ACCESS_KEY_ID=local -e AWS_SECRET_ACCESS_KEY=local \
  -e DYNAMODB_TABLE_PLAYLISTS=orgasm-playlists-local \
  -e COGNITO_USER_POOL_ID=us-east-1_local -e COGNITO_REGION=us-east-1 -e COGNITO_CLIENT_ID=local \
  orgasm-lambda:latest com.orgasm.lambda.HelloHandler::handleRequest

curl -XPOST "http://localhost:9000/2015-03-31/functions/function/invocations" \
  -d '{"requestContext":{"http":{"method":"GET"}},"rawPath":"/hello","headers":{}}'
```

Confirms the image boots (Spring context, Jackson, Cognito JWKS client construction) and routes
to the right handler; DynamoDB calls will fail without a real/local table behind
`DYNAMODB_TABLE_PLAYLISTS` (point `DYNAMODB_ENDPOINT_OVERRIDE` at a `docker run -p 8000:8000
amazon/dynamodb-local:2.5.4` instance and pre-create the tables — copy each table's key
schema/GSIs from `ui/amplify/tables.ts` — to exercise that path too).

**Full backend, real AWS resources** — `npx ampx sandbox` (run from `ui/`) deploys a real,
isolated, per-developer AWS stack (all 9 tables, the Cognito User Pool, all 34 functions) — not
an emulator. Requires the image already pushed once (`ampx sandbox` doesn't build/push it for
you): `docker build`/`docker tag`/`docker push` to an ECR repo named `orgasm-lambda-sandbox`
(matching `ui/amplify/backend.ts`'s fallback `envName`) before the first `ampx sandbox` run.

## Architecture decisions

### SDK as the shared contract
`sdk` contains only pure Java (no Spring, no Lambda SDK). `backend` depends on it (via `sdk-typescript`/other SDK modules downstream). Put shared models, interfaces, and utilities here. Never add framework-specific code to `sdk`. Note: `lambda` does **not** depend on `sdk` or `backend-core` — see "DynamoDB persistence for Lambda" below.

### backend-core: shared service layer (MariaDB path)
`backend-core` is a plain Spring library JAR (no Tomcat, no web) containing:
- JPA entities, Spring Data repositories, MapStruct mappers, and service classes
- `DataSourceConfig`, `AppJpaConfig`, `BillingJpaConfig`, `AuditConfig`, `FlywayConfig`
- Flyway migration scripts (`classpath:db/migration/app/` and `classpath:db/migration/billing/`)

`backend` (thin REST layer) depends on `backend-core` for its MariaDB-backed persistence. `lambda` does **not** — see the next section.

**Lambda JaCoCo gate:** Set to `0` (override in `lambda/pom.xml`) because `LambdaApplication` and `SpringContextHolder` are deployment infrastructure that cannot be unit-tested without a live database.

### DynamoDB persistence for Lambda
`lambda` depends on `backend-dynamo` — a separate library module, not `backend-core` — for its persistence, and now covers nearly the full REST API surface (Playlist/Song/Contributor CRUD, Organization/Registration, and the full Nomination → Guessing → Publish → Rankings → Ratings workflow):

- **Why:** MariaDB (RDS-class, always-on compute) is expensive to keep running for a low-traffic serverless deployment. DynamoDB (`PAY_PER_REQUEST` billing) has no idle cost. `backend-core`/MariaDB stays the persistence for the traditional `backend` app (`:8080`) and local dev; `lambda`, when deployed to AWS, uses DynamoDB instead.
- **Why a separate module instead of an interface/profile switch inside `backend-core`:** `backend-core`'s persistence is deeply Hibernate-specific — Hibernate-managed multi-tenancy (`@TenantId` + `CurrentTenantIdentifierResolver`), `@SQLRestriction` soft-delete filtering, `JOIN FETCH` queries, bulk `@Modifying` JPQL, and `Pageable`/`Page`-based pagination — none of which have a DynamoDB equivalent at the ORM layer. `backend-dynamo` reimplements this business logic independently against DynamoDB's model rather than forcing a shared abstraction onto two fundamentally different persistence technologies.
- **Deliberately excluded, not yet built:**
  - **Publish-time email notifications** (`GuessingResultNotifier`) and the two `@Scheduled` reminder jobs (`PlaylistReminderScheduler`, `GuessingReminderScheduler`) — neither is REST-reachable in the real backend either; both would need SES/EventBridge infra to port.
  - **Migrating the other ~28 business endpoints' UI calls from `backend` to `lambda`.** The UI has zero wiring to `lambda` today — it only ever talks to `backend` (Keycloak-protected, via nginx's `/api/` proxy). Whether `lambda` stays a permanent second backend or eventually replaces `backend` for the AWS deployment is a separate, larger decision than adding auth was.
- **Table design:** one DynamoDB table per entity type, `PAY_PER_REQUEST`. Partition key = `<tenantId>#<ENTITY_TYPE>` (e.g. `1#PLAYLIST`), sort key = the item's internal id (same app-generated `IdGenerator` scheme as `backend-core`, duplicated into `backend-dynamo` since it's backend-agnostic) — except `Organization`, which is **not** tenant-partitioned (it's the tenant directory itself; partition key is its own `id`). A `Query` against the tenant partition key lists one tenant's items cheaply — never a table-wide `Scan` (the one deliberate exception is `Organization.findAll()`, a `Scan` — that table is small and global, matching the real app's own unpaged `findAll()`). Soft deletes use a `deletedAt` attribute, filtered explicitly in each read (no Hibernate-style automatic filter exists in DynamoDB); `Guess` is the one entity that's hard-deleted instead, matching its JPA source (no `@SQLRestriction` there either).
- **Secondary access patterns need a GSI.** `Nomination`, `Guess`, `GuessSubmission`, `SongRating`, and `PlaylistRanking` each declare one or two Global Secondary Indexes (partition key `playlistId`, sometimes with a second GSI e.g. `Nomination`'s `bySong`) replacing what was a JPA derived query or `@Modifying` bulk update — see each entity's `XItem`/`XDynamoRepository` doc comments for exactly which JPA method each GSI replaces. Every table+index bean lives in that entity's own `XDynamoConfig` (e.g. `playlist/PlaylistDynamoConfig`), not the shared `com.orgasm.dynamo.config.DynamoDbConfig` — keeps that file from becoming a merge-conflict magnet as entities are added. A `DynamoDbIndex<T>` constructor field must be named exactly after its `@Bean` method when an entity has more than one GSI of the same item type (Spring disambiguates same-type beans by name).
- **No JOIN FETCH equivalent — lookup-at-read instead of denormalization.** Where a response needs a related entity's name (e.g. `PlaylistResponse.leadContributorName`, `RankingResponse.contributorName`), the mapper/service does an extra `GetItem` by id at read time rather than storing the name redundantly on every row — simpler than keeping denormalized copies in sync on every update, at the cost of an extra read per item. See `OrgasmService.toPlaylistResponse`/`getRankings` for the pattern.
- **Bulk operations become loops.** DynamoDB has no bulk-update/bulk-delete primitive. `declinePendingByPlaylistId` (JPQL bulk `UPDATE`) and the guess/rating "delete all, then recreate" full-replace flows (JPQL bulk `DELETE`) are both a `Query` followed by a loop of individual `UpdateItem`/`DeleteItem` calls — same non-atomic-across-the-whole-operation behavior the JPA bulk queries already had (no surrounding transaction there either).
- **Multi-tenancy:** `DynamoTenantContext` (a `ThreadLocal<Long>`, mirroring `backend-core`'s `TenantContext`) defaults to tenant `1` when unset.
- **Optimistic locking:** entities that are ever updated in place (all except `Guess`, which is create/hard-delete only) carry `@DynamoDbVersionAttribute version`. The Enhanced Client's `VersionedRecordExtension` must be registered explicitly on the `DynamoDbEnhancedClient` bean — it is not on by default. Use `table.updateItem(item)`, not `table.putItem(item)`, when you need the post-write item (with version populated) back — `putItem` returns `void` and never mutates the Java object passed to it. For a **partial** update (only some attributes), load-mutate-`updateItem`-the-whole-object instead of an `ignoreNulls(true)` partial item — a partial item has no `version`, which the extension reads as "doesn't exist yet" and fails its conditional check against an item that already exists (hit this exact bug in `NominationDynamoRepository.declinePendingByPlaylistId` during development).
- **Local dev / IT tests:** `app.dynamodb.endpoint-override` points the client at DynamoDB Local (Docker, for manual Lambda-image testing — see "Lambda local testing" — or a Testcontainers `GenericContainer` for `backend-dynamo`'s IT tests) instead of the real AWS endpoint; unset in the deployed Lambda so the SDK uses the default region/credential chain (the Lambda execution role).

### Cognito auth for the Lambda API

`lambda` gained real authentication via AWS Cognito — **additive to, not a replacement for**, `backend`'s Keycloak-based auth, which is untouched. The rationale mirrors DynamoDB-over-MariaDB: Cognito is managed and pay-per-use, avoiding a second self-hosted identity provider for the AWS deployment path.

- **Why hand-rolled JWT validation instead of a native authorizer:** Lambda Function URLs only support `AuthType: NONE` or `AuthType: AWS_IAM` — there's no built-in Cognito/JWT authorizer option (that requires fronting with API Gateway, which this deployment deliberately doesn't use — see "Function URLs have no route templating" above). `BaseHandler` validates the bearer token itself via `com.orgasm.lambda.auth.CognitoJwtValidator` (Nimbus JOSE+JWT: `JWKSourceBuilder` for cached JWKS fetch/retry, `DefaultJWTProcessor` + `JWSVerificationKeySelector` for RS256 signature verification, `DefaultJWTClaimsVerifier` for issuer/audience/expiry/`token_use` checks).
- **ID token, not access token.** Only the ID token reliably carries the `email` claim the link flow needs, and using one token type everywhere keeps `CognitoJwtValidator` a single code path — a deliberate deviation from strict OAuth semantics, documented on the class itself. `token_use` in the claims is the discriminator between Cognito's ID and access tokens (both are structurally similar RS256 JWTs signed by the same JWKS).
- **Three `AuthMode`s**, declared per-handler by overriding `BaseHandler.authMode()` (`com.orgasm.lambda.auth.AuthMode`): `PUBLIC` (no token check — `ListOrganizationsHandler`, `RegisterContributorHandler`, `HelloHandler`), `AUTHENTICATED` (valid token required, no Contributor lookup — only `LinkContributorHandler`, called right after Cognito sign-up when no Contributor may exist yet), and `AUTHENTICATED_WITH_CONTRIBUTOR` (valid token **and** a linked Contributor — the default; every other handler). For the default mode, `BaseHandler` resolves the Contributor via `ContributorDynamoRepository.findByCognitoSub` and sets `DynamoTenantContext` from its `tenantId` before `execute()` runs, clearing it unconditionally in `handleRequest`'s `finally` (Lambda execution environments can be reused across invocations on the same thread, so this now matters — it didn't before, when nothing outside `RegistrationService` ever called `DynamoTenantContext.set`). `UnauthorizedException` → 401 (missing/invalid/expired token); `ContributorNotLinkedException` → 403 (valid token, no linked Contributor yet — re-authenticating won't fix this, the client needs to call the link endpoint).
- **Sign-up is separate from linking.** A user creates their Cognito account directly against Cognito (not through `lambda` — e.g. via the UI's Amplify `<authenticator>` component), then calls `POST` on `LinkContributorFunction` (mode `AUTHENTICATED`) with a chosen organization slug + name. `LinkContributorService` (`backend-dynamo/.../registration/`, mirrors `RegistrationService`) attaches to an existing Contributor by email match within that org if one exists (setting `cognitoSub` on it — full load-mutate-`updateItem`, per the version-attribute gotcha below, never a partial update), or creates a new one. `GetCurrentContributorFunction` is the `/me` equivalent explicitly excluded from the earlier no-auth pass — there's now a caller identity to resolve it from.
- **`ContributorItem.cognitoSub`** is a sparse GSI (`byCognitoSub`, mirrors `Organization`'s `bySlug`) — Contributors without a linked Cognito identity simply don't appear in it. `ContributorDynamoRepository.findByEmail` is a plain in-memory filter over the tenant's already-fetched `Query`, not a new GSI — same "lookup-at-read" scale reasoning as everywhere else in this module.
- **Test seam:** `BaseHandler`'s 2-arg test constructor (`ObjectMapper`, `Validator`) leaves `cognitoJwtValidator`/`contributorRepository` null, which makes `authenticate()` a no-op regardless of a handler's `authMode()` — this is why all ~30 pre-existing handler tests kept passing unmodified when the default `authMode()` became `AUTHENTICATED_WITH_CONTRIBUTOR`. Auth/tenant-resolution itself is tested once, in `BaseHandlerAuthTest`, via a 4-arg constructor (`+CognitoJwtValidator, +ContributorDynamoRepository`) that handlers needing the real auth path (or their tests) can use instead.
- **UI side is a standalone proof, not wired into the app shell.** `ui/src/amplify.ts` configures the Amplify client (`VITE_COGNITO_USER_POOL_ID`/`VITE_COGNITO_CLIENT_ID`, same build-time-env convention as `VITE_KC_URL` in `keycloak.ts`); `ui/src/lambdaApi.ts` is a minimal fetch client for just the two new endpoints (`VITE_LAMBDA_LINK_URL`/`VITE_LAMBDA_ME_URL`), attaching the Cognito ID token via `fetchAuthSession()`; `ui/src/components/CognitoLoginOverlay.vue` wraps `@aws-amplify/ui-vue`'s `<authenticator>` (sign-up/sign-in/confirm-code) and, once authenticated (watched via the `useAuthenticator()` composable, not a slot-scoped watcher), calls `getCurrentContributor()` — on no-linked-Contributor it shows an org-picker "complete your profile" form calling `linkContributor()`. Deliberately **not** added to `App.vue`'s existing mock/Keycloak overlay switch, and `api.ts`/`stores/auth.ts` are untouched — the UI has no other wiring to `lambda` today (everything else still talks to `backend` via nginx's proxy), so this is a working building block, not a live third auth mode yet.
- **GraalVM native-image is parked, not removed.** An earlier attempt got the build through Spring AOT successfully (Nimbus/`CognitoJwtValidator` confirmed native-image-compatible) but failed at final linking on a pre-existing, Cognito-unrelated conflict: Netty (pulled in transitively via the AWS SDK's `netty-nio-client`/`s3` dependencies) captures a static Logback `Logger`/`LoggerContext` into the build-time image heap, which GraalVM rejects, and the GraalVM Reachability Metadata Repository's bundled config for `logback-classic`/Netty kept reasserting run-time init even after `--initialize-at-build-time=ch.qos.logback`. Rather than keep chasing that, the Amplify Gen 2 migration (see below) deploys a plain **JVM** container image instead — the `native` Maven profile and its `buildArgs` fix stay in `lambda/pom.xml` for a future revisit, but are not part of the current build/deploy path.
- **The JVM fat-jar path had its own latent packaging bugs, only surfaced by actually running the built image** (via the Lambda Runtime Interface Emulator — see "Lambda local testing" — this fat jar/container combination had never been executed standalone before this migration; `mvn test`'s Spring Test context bootstrapping doesn't hit either bug):
  - `maven-shade-plugin` had no merge transformers for `META-INF/spring.factories`/`META-INF/spring/*.imports`. Multiple dependency jars each ship one of these; naive shading keeps only the last jar's copy instead of merging them, silently dropping Spring Boot auto-configuration (the app booted, but `application.yml` was never read — every `@Value` resolved to its literal, unresolved `${...}` placeholder text). Fixed with `ServicesResourceTransformer` + two `AppendingTransformer`s in `lambda/pom.xml`'s shade execution.
  - Spring Boot 4 split Jackson auto-configuration into its own `spring-boot-jackson` module, which configures a **Jackson 3.x** (`tools.jackson.databind.ObjectMapper`) bean — a different type from the **Jackson 2.x** (`com.fasterxml.jackson.databind.ObjectMapper`) type every handler in this module actually uses. `ctx.getBean(ObjectMapper.class)` in `BaseHandler` found no matching bean. Fixed by defining an explicit `@Bean ObjectMapper` in `com.orgasm.lambda.config.JacksonConfig` (`new ObjectMapper().findAndRegisterModules()`, same construction every test file already used) rather than depending on Spring Boot's Jackson-generation auto-config — `LambdaApplication`'s `scanBasePackages` had to grow to include `com.orgasm.lambda.config`.

**Lambda Spring bootstrap pattern** (unchanged shape, now resolves `com.orgasm.dynamo.*` services instead of `backend-core`'s):
```java
// LambdaApplication scans "com.orgasm.dynamo" (not "com.orgasm.backend")
// SpringContextHolder initializes once on cold start (static block)
// Each handler's no-arg constructor pulls its service bean(s) from context
// Package-private constructor takes mocks for unit tests (never triggers context)
public GetPlaylistHandler() {
    var ctx = SpringContextHolder.get();
    this.playlistService = ctx.getBean(PlaylistService.class);
}
```

**Function URLs have no route templating.** Unlike API Gateway, a Lambda Function URL doesn't parse `{id}`-style path variables — each endpoint is its own function with its own dedicated Function URL, and `{id}` segments are pulled out of `event.getRawPath()` manually via `BaseHandler.pathSegment(event, fromEnd)` (`fromEnd = 0` is the last path segment, `1` the second-to-last, etc. — e.g. `/api/v1/playlists/{id}/open` reads the id via `pathSegment(event, 1)`). `BaseHandler.pageable(event)` builds a `Pageable` from `page`/`size` query-string params (defaulting to unpaged), and `queryParam(event, key)` reads a single optional query param (e.g. the `name` filter on list endpoints). `BaseHandler`'s error mapping also maps `IllegalStateException` (workflow state-transition conflicts — wrong playlist status, non-lead-contributor actions, etc.) to `409 Conflict`.

**`Page`/`PageImpl` isn't directly Jackson-serializable outside Spring MVC.** A list handler that returns `Page<T>` straight from `execute()` and lets `BaseHandler.respond()` call `mapper.writeValueAsString(page)` on it fails at runtime: `PageImpl` exposes a `pageable` property, and Jackson tries to serialize it, which for the default `Pageable.unpaged()` calls `Unpaged.getOffset()` — a method that throws `UnsupportedOperationException` by design. (Spring MVC normally avoids this via `spring-data-commons`'s auto-registered `PageModule`, which isn't present in `lambda`'s plain `ObjectMapper`.) Every list handler (`ListPlaylistsHandler`, `ListSongsHandler`, `ListContributorsHandler`, `ListNominationsHandler`, `ListPlaylistsByContributorHandler`) instead returns `Map<String, Object>` built by `BaseHandler.pageBody(Page<?>)`, shaped to match the OpenAPI spec's `content`/`totalElements`/`totalPages`/`number`/`size` page schemas (e.g. `PlaylistPage`). Follow this pattern for any future paginated handler rather than returning `Page<T>` directly.

### Spring Boot BOM imported, not inherited
The root `pom.xml` imports `spring-boot-dependencies` as a BOM inside `<dependencyManagement>`. This lets `lambda` avoid pulling Spring Boot transitive dependencies while still benefiting from version alignment for Jackson/SLF4J etc.

### Lambda packaging
`lambda` uses `maven-shade-plugin` (with merge transformers for Spring's factories/imports files — see "Cognito auth for the Lambda API") to produce a single fat JAR, packaged into a container image (`lambda/Dockerfile`) that every Lambda function shares — see "Amplify Gen 2 backend" for how a function picks its handler class from that one image. New Lambda functions implement `RequestHandler<IN, OUT>` (via `BaseHandler<T>`, same pattern as every existing handler) and add one entry to `ui/amplify/functions.ts`'s `fnSpecs` array — no per-function infra block to hand-write.

### Amplify Gen 2 backend
`ui/amplify/` is a AWS Amplify Gen 2 backend-as-code project (`@aws-amplify/backend`, deployed via the `ampx` CLI) — it, not AWS SAM, is what provisions Cognito, the 9 DynamoDB tables, and all 34 Lambda functions. It lives inside `ui/` (not at the repo root) because Amplify Hosting auto-detects a Gen 2 backend by finding `amplify/` next to the app's own `package.json`, and this repo's actual frontend root is `ui/`.

- **`ui/amplify/auth/resource.ts`** — `defineAuth({ loginWith: { email: true } })`, the Cognito User Pool backing the auth described above.
- **`ui/amplify/tables.ts`** — the 9 DynamoDB tables as raw CDK `Table` L2 constructs (key schema + GSIs), **not** Gen 2's opinionated `defineData`/AppSync model — this schema is hand-rolled pk/sk + GSIs, unrelated to GraphQL.
- **`ui/amplify/functions.ts`** — the `fnSpecs` array: one entry per Lambda function (name, handler class, tables it needs, HTTP methods, CORS headers), replacing what would otherwise be 34 hand-written CDK blocks with data.
- **`ui/amplify/backend.ts`** — `defineBackend({ auth })`, then a custom CDK stack (`backend.createStack('LambdaApi')`) building the tables, looping `fnSpecs` to construct each `lambda.Function` + its per-table IAM grants (`table.grantReadWriteData(fn)`, the L2 equivalent of SAM's old `DynamoDBCrudPolicy`) + its Function URL, and `backend.addOutput({ custom: { functionUrls } })` to surface the Function URLs into the generated `ui/amplify_outputs.json`.
- **One pushed image, many functions, different `CMD`.** `lambda.DockerImageCode.fromImageAsset()` with a different `cmd` per function would trigger a separate `docker build` per function. Instead, the image is built and pushed to ECR **once** by `amplify.yml`'s `backend` phase, and every function references that already-pushed image via `lambda.Code.fromEcrImage(repo, { tagOrDigest, cmd: [handlerClass] })` — no rebuild per function. The ECR repo itself is imported (`ecr.Repository.fromRepositoryName`), never CDK-owned (`new ecr.Repository`), since a CDK-owned repo conflicts with externally-pushed images at drift/destroy time; `amplify.yml` creates it idempotently (`aws ecr create-repository ... || true`) before pushing.
- **`ui/amplify_outputs.json`** is Gen 2's generated client config (Cognito User Pool/Client id + the `custom.functionUrls` map) — committed with placeholder values so local type-checking/builds work before any real deploy, then overwritten by every `ampx sandbox`/`ampx pipeline-deploy`. Never hand-edit it. `ui/src/amplify.ts` and `ui/src/lambdaApi.ts` read from it directly (`Amplify.configure(outputs)`, `outputs.custom.functionUrls[...]`) instead of `VITE_*` build-time env vars.
- **Deployment is now automatic.** `amplify.yml` has a `backend` phase (build the jar, build+push the image, `npx ampx pipeline-deploy --branch $AWS_BRANCH --app-id $AWS_APP_ID`) ahead of the existing `frontend` phase — Amplify Hosting runs both on every push to a connected branch. Requires the Amplify Console's Build image switched to a Docker-capable one (e.g. `public.ecr.aws/codebuild/amazonlinux-x86_64-standard:5.0`); the default Amplify build image has no Docker daemon.
- **SAM is gone.** `lambda/template.yaml` and the GraalVM stage of `lambda/Dockerfile` were removed — one infra-as-code path (Gen 2/CDK), not two describing the same resources.

### API versioning

The backend uses Spring Boot 4's built-in API versioning (`ApiVersionConfigurer`), configured in `WebConfig`:

- **Strategy**: path-based — version is the second URL segment (e.g., `/api/v1/playlists`)
- **Parser**: `SemanticApiVersionParser` — strips leading `v`, so `v1` → version `1.0.0`
- **Unversioned paths** (health, actuator, Swagger) pass through because `setVersionRequired(false)` is set and the resolver predicate skips paths whose second segment doesn't match `v\d+`
- **Supported versions** are detected automatically from controller annotations

**Controller pattern:**
```java
@RequestMapping(value = "/api/v1/playlists", version = "1")
```
The path includes the full `/api/v1/` prefix; the `version` attribute tells Spring MVC which version this controller serves and is used for supported-version validation.

**Adding v2:** Add a new controller with `value = "/api/v2/playlists", version = "2"`. Both controllers coexist; requests to `/api/v1/` and `/api/v2/` route independently.

**Tests:** Standalone MockMvc tests must configure a version strategy to match production. Use `VersionTestSupport.pathVersionStrategy()` (in `backend/src/test/java`) and pass it to `.setApiVersionStrategy(...)` on the builder.

**Exception handling:** `GlobalExceptionHandler` maps version errors to HTTP responses:
- `MissingApiVersionException` → 400
- `InvalidApiVersionException` (unsupported version) → 400
- `NotAcceptableApiVersionException` → 406

### Public registration endpoints

Two endpoints are intentionally public (no JWT required):

- `GET /api/v1/organizations` — lists `Organization` entries from the billing DB (for registration form)
- `POST /api/v1/register` — creates a `Contributor` in the app DB for the given organization slug

Both are declared in `SecurityConfig.permitAll()`. `TenantResolverFilter` already passes through non-JWT requests. The `RegistrationController` sets `TenantContext` manually from the resolved organization's id before calling `ContributorService.create()`, and clears it in a `finally` block.

**Organization** (`com.orgasm.billing.domain`) is the billing-database identity entity. `Organization.id` equals the `tenant_id` used throughout the app DB. When the mock login overlay's "Register" tab submits, `POST /api/v1/register` creates the contributor and auto-logs them in.

### CORS
`WebConfig` allows `http://localhost:5173` (Vite dev server) for `/api/**` (all methods). For production, update the allowed origins in `backend/src/main/resources/application.yml` or override via environment variable.

### Multi-datasource JPA (backend)
The backend uses two physically separate MariaDB databases, each with its own `DataSource`, `EntityManagerFactory`, and `JpaTransactionManager`:

| Bean qualifier | Database | Port | Purpose |
|----------------|----------|------|---------|
| `appDataSource` / `appEntityManagerFactory` | `orgasm` | 3306 | Application data |
| `billingDataSource` / `billingEntityManagerFactory` | `orgasm_billing` | 3307 | Billing data |

**Package layout for entities and repositories:**
- `com.orgasm.billing.domain` — `@Entity` classes for the **billing** database (e.g. `Organization`)
- `com.orgasm.billing.repository` — Spring Data repositories for billing (use `billingTransactionManager`)
- `com.orgasm.backend.*` — entities and repositories for the **app** database

`AppJpaConfig` scans `com.orgasm.backend` broadly for both entities and repositories. `BillingJpaConfig` scans `com.orgasm.billing` exclusively. **Billing code lives in `com.orgasm.billing.*`** (not under `com.orgasm.backend`) to prevent the app `EntityManagerFactory` from picking up billing entities — which would cause `ddl-auto: validate` to fail in production (the app DB has no billing tables).

This means **new app feature packages (e.g. `com.orgasm.backend.song`) are picked up automatically** — no changes to `AppJpaConfig` required when adding a new app entity.

**Billing entities must NOT extend `AuditableEntity`** — the billing `EntityManagerFactory` has no `@EnableJpaAuditing` infrastructure and no `CurrentTenantIdentifierResolver`. Billing entities also must NOT have `@TenantId`.

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
| Lambda runtime | `public.ecr.aws/lambda/java:25` container image (Graviton arm64) |
| Nimbus JOSE+JWT | pinned in root POM as `nimbus-jose-jwt.version` |
| @aws-amplify/backend | ^1.25.0 (`ui/package.json` devDependencies) |
| aws-cdk-lib | ^2.268.0 |

All Java dependency versions are managed centrally in the root `pom.xml` `<properties>` block. Update versions there, not in individual module POMs. `node.version` and `frontend-maven-plugin.version` are also in the root `<properties>` block.
