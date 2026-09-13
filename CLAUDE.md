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
| `lambda/` | AWS Serverless (Java, reference implementation) | Fat JAR via shade plugin; the original DynamoDB/Cognito Lambda implementation. Kept in the repo fully built and tested but **no longer deployed** — superseded by TypeScript functions in `ui/amplify/functions/` (see [Architecture decisions](#typescript-lambda-api-via-amplify-gen-2)) |
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

**Important:** Playlist API tests use `page.evaluate()` (browser-side fetch) rather than Playwright's `request` fixture (Node.js fetch). MSW runs as a Service Worker in the browser, so requests must originate from the browser to be intercepted. Each API-level spec's `beforeEach` calls `waitForMsw(page)` (`e2e/helpers.ts`), which loads `/` and waits for `navigator.serviceWorker.controller` before any fetch is made — don't wait on a specific API response instead (the specs used to wait for a `/api/health` call that `HomeView` no longer makes, and silently timed out for months).

**Name filters:** `NameFilter.vue` debounces 300 ms and emits the value **trimmed** (a trailing space must not become part of the substring match). The list views keep a monotonic `requestSeq` in `fetchPage` and drop any response that isn't for the latest request — otherwise a slow request for `"c"` (Lambda cold start) can land after the fast one for `"creep"` and overwrite it.

### App shell and mobile navigation

`App.vue` owns the layout only; the nav links + user/theme/logout footer live in
`src/components/SidebarNav.vue`, rendered twice: inside the persistent `<aside>` (`hidden md:flex`)
and inside a `Sheet` drawer (`src/components/ui/sheet/`, built on the same radix-vue `Dialog`
primitives as `ui/dialog`) opened from a mobile-only top bar (`md:hidden`) with an `Open menu`
hamburger. Add new nav entries to `SidebarNav.vue` once — never to `App.vue`. The drawer closes on
every route change (`watch(route.fullPath)`) and when the viewport crosses the `md` breakpoint.

The drawer's slide-in is a scoped CSS `@keyframes` on radix's `data-state` attribute — this
project has no `tailwindcss-animate` plugin, so the `animate-in`/`slide-in-*` utilities used in
`DialogContent.vue` are inert (Tailwind v4 ignores unknown classes; they do nothing).

Phone-width shell behaviour is covered by `e2e/mobile.spec.ts` (`test.use({ viewport: 390×844 })`);
desktop specs in `home.spec.ts` keep clicking sidebar links directly, which works because the
closed drawer is not mounted, so there is only one `Songs` link in the DOM at desktop width.

### Stale-deploy recovery (lazy route chunks)

Every route except Home is lazy-loaded and Vite content-hashes the chunks; Amplify Hosting keeps
only the latest deployment. So a tab opened *before* a deploy holds a main bundle whose
`import('./SongsView-<oldhash>.js')` now 404s — the first click on an unvisited route fails with
`Failed to fetch dynamically imported module` and nothing happens until a hard refresh.
`src/staleChunkRecovery.ts` (installed in `main.ts` before `app.use(router)`) handles this the
way Vite/Vue Router document it: `router.onError` + the `vite:preloadError` window event → one
full `window.location.assign(to.fullPath)` to the route the user wanted, which loads the new
`index.html`. A `sessionStorage` timestamp limits it to one reload per 10 s so a genuinely broken
chunk surfaces the error instead of looping. Tests in `staleChunkRecovery.test.ts` (jsdom).

`customHttp.yml` at the repo root (Amplify Hosting custom headers, applied over its defaults)
makes `/assets/*` `immutable, max-age=1y` (safe — hashed names) and keeps everything else,
i.e. `index.html`, on `max-age=0, must-revalidate`. Later patterns win, so the assets rule is
last. This is the belt; the runtime recovery above is what actually fixes an already-open tab.

### Internationalization (vue-i18n)

The UI is localized with **vue-i18n v11** (Composition API only, `legacy: false`) plus
`@intlify/unplugin-vue-i18n`, which pre-compiles the catalogues so the runtime-only build ships.
Setup lives in `src/i18n.ts`; the plugin is registered in `vite.config.ts` and `main.ts`.

- **Catalogues:** `src/locales/<locale>.json` — `en.json` is the reference, `sk.json` mirrors it.
  `src/locales/locales.test.ts` (Vitest) fails if any locale is missing/extra keys or uses
  different `{placeholders}` than `en`. **Adding a language** = add `src/locales/xx.json` with the
  same keys, append `'xx'` to `SUPPORTED_LOCALES` in `src/i18n.ts`, and add its native name under
  `locale.xx` in every catalogue. The vite plugin's `include` is `src/locales/*.json` on purpose —
  a `**` glob would try to parse `locales.test.ts` as a message file.
- **Usage:** `const { t, d } = useI18n()` in `<script setup>`; `t('ns.key')`, plurals via
  `t('songs.count', n)`, params via `t('common.pageOf', { page, total })`. Dates go through the
  named formats `d(date, 'date')` / `d(date, 'dateTime')` (locale-aware; replaces the old
  `toLocaleDateString(undefined, …)`). Enum-like labels are keyed by the API value:
  `t(\`playlistStatus.${status}\`)`, `nominationStatus.*`, `ratingType.*`. `@` and `|` are
  special in messages — write `{'@'}` in placeholders like e-mail examples.
- **Plurals:** English uses vue-i18n's built-in `zero | one | other` rule; Slovak registers a
  4-form rule in `src/i18n.ts` (`zero | one | few (2–4) | many (5+)`), so `sk.json` plural
  messages have four `|`-separated forms.
- **Persistence is a cookie, not localStorage:** `locale` cookie (1 year, `Path=/`,
  `SameSite=Lax`, `Secure` on https) — chosen over the `theme` localStorage pattern so the
  server side (nginx/backend) can read the choice later. Detection order: cookie → first
  supported entry in `navigator.languages` (`sk-SK` → `sk`) → `en`. `setLocale()` also mirrors the
  value onto `<html lang>`. `useLocale()` (`src/composables/useLocale.ts`) exposes a writable
  `locale` computed for the switcher — a `Select` in `SidebarNav.vue` labelled `nav.language`.
- **Amplify Authenticator:** `CognitoLoginOverlay.vue` feeds `translations` from
  `@aws-amplify/ui-vue` into Amplify's `I18n` and follows the active locale; languages Amplify
  doesn't ship (e.g. `sk`) fall back to English inside the widget only.
- **Tests:** `src/i18n.test.ts` (cookie round-trip, detection order, Slovak plurals — needs the
  `jsdom` dev dependency, opted in per-file via `// @vitest-environment jsdom`);
  `e2e/i18n.spec.ts` (sidebar switch, cookie persists across reload, `test.use({ locale:
  'sk-SK' })` detection, cookie-beats-browser-language). Playwright's default locale is `en-US`,
  so all other specs keep asserting English strings.
- `vite.config.ts` now also excludes `e2e/**` from Vitest — the Playwright specs share the
  `*.spec.ts` suffix and were previously collected (and failed) by `npm run test:unit`.

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

### Lambda (Java reference implementation — not part of the deploy path)

**These commands build and run the original Java `lambda` module only.** It is no longer
deployed — the real Lambda API is the TypeScript rewrite in `ui/amplify/functions/` (see
"TypeScript Lambda API via Amplify Gen 2" below). This section is kept because `backend-dynamo`/
`lambda` remain in the repo, fully built and tested, as a reference implementation; use it if
you're working on that Java code specifically, not for anything related to actual deployment.

`lambda` packages as a plain **JVM** container image (not GraalVM native-image — see "Cognito auth
for the Lambda API" for why that path is parked). The `native` Maven profile is still present in
`lambda/pom.xml` for a future revisit, but is not part of the current build path; skip straight to
the JVM commands below unless specifically reviving native-image.

```bash
# Build the fat jar (default, non-native profile)
./mvnw package -pl lambda -am -DskipTests

# Build the Lambda container image (build context = repo root; base image
# public.ecr.aws/lambda/java:25 already includes the Lambda runtime interface client)
docker build -f lambda/Dockerfile -t orgasm-lambda:latest .

# For Graviton arm64:
docker buildx build --platform linux/arm64 -f lambda/Dockerfile -t orgasm-lambda:latest .
```

**Adding new Java handlers** (reference implementation only — for the deployed API, see "Adding a
new endpoint" under "TypeScript Lambda API via Amplify Gen 2"): create the handler class (extends
`BaseHandler<T>`, same pattern as any existing one). It is no longer wired into any deploy loop.

## Lambda local testing (Java reference implementation)

Also reference-only — see the note above. Run the built image directly via the AWS base image's
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
amazon/dynamodb-local:2.5.4` instance and pre-create the tables to exercise that path too).

For the **deployed** TypeScript API, `npx ampx sandbox` (run from `ui/`) deploys a real, isolated,
per-developer AWS stack (all 9 tables, the Cognito User Pool, all 40 functions) — no image to
build/push first, since every function bundles straight from its own `handler.ts`. See
"TypeScript Lambda API via Amplify Gen 2" for how to run its tests against DynamoDB Local instead.

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

**This section describes the Java reference implementation** (`backend-dynamo`/`lambda`), kept
in the repo unmodified but no longer deployed. The deployed Lambda API is a TypeScript port of
this exact design — same table layout, same GSIs, same optimistic-locking scheme — see
"TypeScript Lambda API via Amplify Gen 2" below for what changed (or didn't) in the port.

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

**This section also describes the Java reference implementation** — see the note at the top of
"DynamoDB persistence for Lambda". The deployed TypeScript API ports the same three `AuthMode`s
and ID-token-based verification (via `aws-jwt-verify` instead of hand-rolled Nimbus) — see
"TypeScript Lambda API via Amplify Gen 2".

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

### Lambda packaging (Java reference implementation)
`lambda` uses `maven-shade-plugin` (with merge transformers for Spring's factories/imports files — see "Cognito auth for the Lambda API") to produce a single fat JAR, packaged into a container image (`lambda/Dockerfile`). This image is no longer deployed anywhere — see "TypeScript Lambda API via Amplify Gen 2" for the actual deploy path. New handlers in this Java module implement `RequestHandler<IN, OUT>` (via `BaseHandler<T>`, same pattern as every existing handler), but adding one is not wired into any deploy loop today.

### TypeScript Lambda API via Amplify Gen 2

`ui/amplify/` is the actual deployed Lambda API: 40 AWS Lambda functions written in TypeScript,
using Amplify Gen 2's native `defineFunction` — one `resource.ts` + `handler.ts` per function, no
CDK escape hatch, no container image, no Docker anywhere in the pipeline. It supersedes an earlier
design (and, before that, AWS SAM) that packaged the **Java** `lambda`/`backend-dynamo` modules
into a single JVM container image shared across all 34 functions via a per-function `CMD`
override, deployed through a CDK escape hatch (`lambda.Code.fromEcrImage`). That Java
implementation is described in "DynamoDB persistence for Lambda" and "Cognito auth for the Lambda
API" above — it remains in the repo, fully built and tested, as the spec this port was checked
against, but **is not part of the deploy path**; do not delete it or "clean it up" as dead code.

`ui/amplify/` lives inside `ui/` (not at the repo root) because Amplify Hosting auto-detects a
Gen 2 backend by finding `amplify/` next to the app's own `package.json`, and this repo's actual
frontend root is `ui/`.

- **Why TypeScript, not a Java container image:** Amplify Hosting's `backend` build phase doesn't
  reliably run Docker — the documented workaround (switching the Console's Build image to a
  Docker-capable one, starting `dockerd` manually in `preBuild`) failed in practice
  (`nohup: failed to run command '/usr/local/bin/dockerd-entrypoint.sh': No such file or
  directory`), and `defineFunction` itself has no documented support for a pre-built Java artifact
  — only Node/Python/Go examples, even via its own CDK escape hatch. Rewriting in TypeScript, Gen
  2's native first-class function type, removes Docker from the pipeline entirely instead of
  continuing to chase a Docker-in-CodeBuild fix.
- **Full parity, ported faithfully.** All 34 endpoints — CRUD for Playlist/Song/Contributor/
  Organization, Cognito auth (link + me), and the complete Nomination → Guessing → Publish →
  Rankings → Ratings workflow — were ported field-for-field from the Java source: `OrgasmService.
  java`'s 13 methods (including the ranking tie-break algorithm and rating-type point-set
  validation), each `XItem.java`'s fields, and each handler's exact `pathSegment`/`queryParam`/
  success-status/auth-mode.
- **Directory structure** (all under `ui/amplify/`):
  - `functions/<name>/resource.ts` + `handler.ts` — one pair per endpoint. `resource.ts` is
    `defineFunction({ name, entry: './handler.ts' })`; `handler.ts` is a thin
    `withAuth(mode, status, async (event, ctx) => {...})` wrapper calling into `lib/services/*`.
  - `lib/dynamodb.ts` — `DynamoDBDocumentClient` singleton, respects `DYNAMODB_ENDPOINT_OVERRIDE`
    for local/test use. Constructed at module load time — anything that needs to override the
    endpoint (e.g. a test) must set the env var **before** this module is imported.
  - `lib/idGenerator.ts` — bit-exact port of `IdGenerator.java`, using `bigint` throughout (`<<`,
    `^`, a hand-written `reverse64`). **Must use `bigint`, never `number`**, for every id
    operation — the scheme's 64-bit ops exceed `Number.MAX_SAFE_INTEGER`'s 53 bits. Verified
    against real Java-produced id/format pairs in `idGenerator.test.ts`, not just reasoned about.
  - `lib/auth.ts` — Cognito ID-token verification via `aws-jwt-verify`'s `CognitoJwtVerifier`
    (simpler than the Java Nimbus-based implementation; same "ID token, not access token"
    reasoning as the Java side — only the ID token reliably carries `email`).
  - `lib/http.ts` — the `BaseHandler.java` port: `withAuth(mode, successStatus, fn)` resolves the
    Contributor for `authenticated-with-contributor` mode and maps thrown errors
    (`ValidationError`→400, `UnauthorizedError`→401, `NotLinkedError`→403, `NotFoundError`→404,
    `ConflictError`→409) to status codes, the same table `BaseHandler`'s catch chain used. Also
    has `pathSegment`/`queryParam`/`pageable`/`pageBody`, ported 1:1 — Function URLs still have no
    route templating (unchanged from the Java design, see below).
  - **The acting contributor always comes from `ctx.contributorId`, never from the request body —
    a deliberate deviation from the Java source.** `OrgasmService.java` and its DTOs take the
    actor as a `contributorId`/`reviewerId` field on the request record; every TS workflow service
    takes it as an explicit `actorId: bigint` parameter that only `withAuth` can supply. The Java
    shape is a broken-access-control bug: the "only the lead contributor can review/start
    guessing/publish" checks compared the playlist's lead against that same caller-supplied value,
    so a caller could read `leadContributorId` off `GET /playlists/{id}` and send it back to pass
    them, as well as nominate/guess/rate as any other contributor (both `submitGuesses` and
    `submitRatings` delete the target's existing rows before re-creating them). When porting
    anything else from the Java reference implementation, drop the actor field the same way —
    do not restore it for parity. The frozen Java modules still carry the flaw by design, and so
    does `backend`'s `OrgasmController` (see "Known gaps" below).
  - `lib/repositories/*.ts` — one per entity, mirrors each `XDynamoRepository.java` exactly: same
    pk/sk scheme (`partitionKey(tenantId, ENTITY_TYPE)`), same GSI query patterns, and the same
    load-full-item-then-`PutCommand`-with-`ConditionExpression` optimistic-locking pattern
    (`repositories/base.ts`'s `saveItem`) — deliberately **always writes the full item**, never a
    partial patch, to avoid the exact bug `NominationDynamoRepository.declinePendingByPlaylistId`
    hit in the Java source (a partial write has no `version`, read by the lock check as "doesn't
    exist yet").
  - `lib/services/*.ts` — one per entity plus `orgasm.ts` (the big one, ~450 lines — all 13
    `OrgasmService.java` methods).
  - **No `DynamoTenantContext`-equivalent.** Handlers pass `tenantId: number` as an explicit
    parameter through every service/repository call instead of an implicit thread-local — more
    idiomatic for Node than replicating Java's `ThreadLocal` pattern.
  - **Deadlines are editable after the fact, but gated.** `open-playlist` / `start-guessing`
    stamp `deadline` / `guessingDeadline`; `update-playlist` additionally accepts either field
    (added 2026-09-13 so a lead can extend a deadline that has passed) and
    `services/playlist.ts`'s `updatePlaylist` enforces lead-only + phase-matched (`deadline`
    only while `OPEN`, `guessingDeadline` only while `GUESSING`) → 409 otherwise. The Spring
    `backend` mirrors the fields on its `UpdatePlaylistRequest` (MapStruct null-ignore, no
    gating — same body-actor caveat as the rest of `OrgasmController`), and the Edit dialog in
    `PlaylistDetailView.vue` shows the matching date input only when the API would accept it.
- **`backend.ts`** — imports all 40 `*Fn` resources, passes them into `defineBackend({ auth,
  helloFn, createPlaylistFn, ... })`, builds the 9 DynamoDB tables (`tables.ts`'s `createTables` —
  unchanged from the earlier design: raw CDK `dynamodb.Table` L2 constructs, **not** Gen 2's
  `defineData`/AppSync GraphQL model; this schema is hand-rolled pk/sk + GSIs, unrelated to
  GraphQL and predates it), then loops `functions.ts`'s `fnSpecs` array to grant each function's
  underlying Lambda (`backend.<name>Fn.resources.lambda`) **read** on every table in its `tables`
  list and read+write only on the subset in `writes` (least privilege — `list-*`/`get-*` functions
  hold no write grant at all), add its Function URL (`AuthType.NONE`), and inject the table names
  + Cognito User Pool/Client ids as environment variables (via a CDK-concrete-`Function` cast,
  since `FunctionResources.lambda`'s `IFunction` type doesn't expose `addEnvironment`).
  Functions flagged `publiclyReachable` (only `hello` — `list-organizations` needed a token
  since 2026-09-13, see "Administration") additionally get a reserved-concurrency cap, set on the underlying
  `CfnFunction` — the one thing bounding how much Lambda an unauthenticated flood can burn, since
  Function URLs can't sit behind WAF or an API Gateway throttle. Three deploy-time env vars tune
  this, all read in `backend.ts` and all safe to leave unset: `PUBLIC_FN_RESERVED_CONCURRENCY`
  (default `5`; `0` disables — AWS needs 100 unreserved in the account, so a low account limit
  fails the deploy), `ALLOWED_ORIGINS` (comma-separated CORS origins; default `*` because the
  Amplify Hosting origin is branch-dependent — pin it for production in the Console build env),
  and `ID_GENERATOR_SECRET` (16 hex chars keying `lib/idGenerator.ts`'s id scrambling; default
  all-zero, which makes the external id a plain reversible transform of the DB id).
- **`functions.ts`**'s `fnSpecs` array (name/tables/writes/methods/corsHeaders/publiclyReachable/
  userPoolActions) lost its Java-specific `handlerClass` field in the rewrite — every function now
  has its own `resource.ts`/`handler.ts` instead of sharing one image + `CMD` override. Note
  `tables` must include `contributors` for every `authenticated-with-contributor` function even
  when its body never reads it — `withAuth`'s `findContributorByCognitoSub` does.
  `userPoolActions` lists Cognito actions (`cognito-idp:ListUsers`) to grant on the User Pool —
  only `admin-add-org-contributor` uses it.
- **`register-contributor` is not deployed.** The Java `POST /api/v1/register` port exists as
  `lib/services/registration.ts`'s `register()` (kept, with tests, for parity) but has no
  `functions/` entry since the 2026-09-11 security review: it was an unauthenticated, unlimited
  DynamoDB write that nothing in the UI called — Cognito sign-up goes through `link-contributor`,
  which has a verified identity and the `allowedDomain` gate. So the deployed count is **40**
  functions: the Java reference implementation's 34, minus this one, plus the six `admin-*`
  functions below and `search-songs` (none of which have a Java counterpart).
- **Song catalogue search (`search-songs`, `lib/songSearch/`).** Added 2026-09-13 so the two
  "create a song" forms (the Songs page dialog and the nominate dialog in `PlaylistDetailView`)
  can pre-fill artist/title/album/year from a public database instead of being typed by hand.
  Everything outside `lib/songSearch/` only ever sees the `SongSearchProvider` interface
  (`provider.ts`: `search(query, limit) → SongSearchHit[]`); `index.ts` is a registry keyed by
  the `SONG_SEARCH_PROVIDER` env var (default `musicbrainz`), so **swapping the catalogue is one
  new file implementing the interface plus one registry entry** — `services/songSearch.ts`
  (query validation, limit cap of 25) and the handler don't change. The one implementation is
  `musicbrainz.ts` (MusicBrainz: open data, no API key). Things learned against the live API
  that the class comments also record: (1) MusicBrainz **requires a descriptive `User-Agent`**
  (`SONG_SEARCH_USER_AGENT`, default names this repo) and throttles at ~1 req/s per IP with
  503s — which is why the lookup is a Lambda (browsers can't set `User-Agent`) with
  `reservedConcurrency: 2` in `functions.ts` (a new optional `FnSpec` field; still switched off
  together with the public caps by `PUBLIC_FN_RESERVED_CONCURRENCY=0`), a 15 s
  `timeoutSeconds` in its `resource.ts`, and one `Retry-After`-aware retry; (2) the recording
  index has **no popularity signal** — every live take/bootleg/cover is its own row scoring
  100 — so the provider fetches the 100-row maximum, boosts artist+title splits and
  all-terms-in-title in the Lucene query, keeps `status:official` only, dedupes by artist+title,
  ranks plain titles above `(remix|live|…)` variants and then by release count, and picks the
  earliest plain official album for the `album` field. "artist title" queries land the
  original first; title-only queries usually do now but remain a coin toss among hundreds of
  equally-scored covers when the original isn't in the fetched page — hence the "search by
  artist and title" hint in the UI. `UpstreamError` → **502** is the new `withAuth` mapping
  for a failed/timed-out provider call. UI: `components/SongSearch.vue` (debounced, drops
  stale responses, keyboard-navigable; emits the chosen hit — the parent fills the form and
  the fields stay editable), `api.songs().search(q, limit)` on both clients (`api-backend.ts`
  hits `/api/v1/songs/search`, which the Spring backend does **not** implement — it exists so
  MSW's `mocks/handlers/songs.ts` can serve dev/e2e with a canned catalogue, the same
  arrangement as the admin endpoints), i18n under `songSearch.*`, `e2e/song-search.spec.ts`.
  The edit-song dialog deliberately has no lookup. Tests: `lib/songSearch/*.test.ts` and
  `services/songSearch.test.ts` (fetch mocked; no live MusicBrainz calls in the suite).
- **Administration (`admin` AuthMode, Cognito `admins` group).** Added 2026-09-13 so
  organizations and manual memberships no longer have to be written straight into DynamoDB.
  `auth/resource.ts` declares `groups: ['admins']`; nobody is in it after a deploy — an operator
  adds the first admin by hand (`aws cognito-idp admin-add-user-to-group --user-pool-id <id>
  --username <email> --group-name admins`), and a user can never join it at sign-up. `withAuth`'s
  fourth mode, `'admin'`, checks the signed ID token's `cognito:groups` claim (`lib/auth.ts`
  exposes it as `claims.groups`) and throws `ForbiddenError` → 403 otherwise; it resolves **no**
  Contributor/tenant (admins act cross-tenant, taking the organization id from the path) and logs
  `user.id` = the Cognito `sub` + `user.roles: ['admin']` as the audit handle — the only
  place a `sub` is logged on the Lambda side, mirroring `backend`'s `user.id` convention.
  Removal from the group takes effect when the token expires (≤ 1 h). Five functions, all in
  `lib/services/admin.ts` (tests in `admin.test.ts`): `admin-list-organizations` (includes
  `allowedDomain`, unlike the public shape), `admin-create-organization` (`POST {slug, name,
  allowedDomain?}` — id is `max(id)+1` from a Scan of the small directory table, made race-safe
  by `insertOrganization`'s `attribute_not_exists(id)` condition + retry; slug uniqueness is only
  pre-checked, DynamoDB can't enforce it on the `bySlug` GSI), `admin-update-organization`
  (`PUT /{id}` — rename / set or clear `allowedDomain`; the slug is immutable), `admin-list-org-
  contributors` (`GET /{id}/contributors`, each with `linked: boolean`), and
  `admin-add-org-contributor` (`POST /{id}/contributors {name, email}` — creates the Contributor
  and, if that email already has a Cognito account (`lib/cognito.ts`'s `ListUsers` by email),
  sets `cognitoSub` immediately; otherwise `link-contributor`'s email match attaches it when they
  sign up. The org's `allowedDomain` gate applies here too, or the admin would create a record the
  link step then rejects. 409 if the email is already a live member or the account is linked
  elsewhere.) `list-organizations` became `'authenticated'` in the same change — it only feeds the
  post-sign-in org picker, so anonymous access bought nothing; `CognitoLoginOverlay` now loads it
  after sign-in instead of on mount. UI side: `stores/auth.ts`'s `isAdmin` (Cognito: the same
  claim, read via `loadCognitoSession()`; mock: `isMockAdmin` — only the seeded Thom Yorke;
  Keycloak: never), the `/admin` route + `SidebarNav` link (`AdminView.vue`, i18n under
  `admin.*`), `api.admin()` on both `api-lambda.ts` (Function URLs) and `api-backend.ts`
  (`/api/v1/admin/**` — exists only so MSW's `mocks/handlers/admin.ts` can serve dev/e2e; the
  Spring backend has **no** admin endpoints), and `e2e/admin.spec.ts`. There is deliberately no
  router redirect: a signed-in non-admin opening `/admin` sees AdminView's "not an administrator"
  explanation (the usual cause being a token issued before the group was assigned — re-sign-in
  fixes it); the `v-if`s are cosmetic — the 403 is the gate. One deliberate hole in `App.vue`'s overlay gating: a
  Cognito admin with no linked Contributor may still open `/admin` (the overlay's "Go to
  administration" button), otherwise nobody could create the first organization to link into.
  `main.ts` awaits `authStore.load()` *before* `app.use(router)` so any future guard can read the
  store during the initial navigation.
- **Organization data export (`admin-export-organization`, `lib/services/export.ts`).** Added
  2026-09-13 for the "customer is leaving and wants all their data" case. `GET /{id}/export`,
  `admin` AuthMode only — it is the platform admin (Cognito `admins` group) who hands the data
  over, and the document contains every member's email and every playlist ever run, more than
  any single contributor can see through the regular endpoints. One JSON document with a
  self-describing envelope (`format: orgasm-organization-export`, `formatVersion: 1`,
  `exportedAt`, `organization`, `counts`) and one array per entity: `contributors`, `songs`,
  `playlists`, `nominations`, `guessSubmissions`, `guesses`, `songRatings`,
  `playlistRankings`. Rows use the same prefixed external ids as the REST responses for keys
  and foreign keys (Guess/GuessSubmission/SongRating/PlaylistRanking have no id in any REST
  response, so the export mints `guess-`/`gsub-`/`rate-`/`rank-` with the same encoding);
  `pk`/`sk`/`version`/`tenantId`/`cognitoSub` are dropped; **soft-deleted rows are included**
  with their `deletedAt` (it's "whole history", not the live view); each section is sorted
  oldest-first so two exports diff cleanly. It reads the eight tenant partitions directly via
  new `findAll*ByTenant` repository functions (`nomination`, `guess`, `guessSubmission`,
  `songRating` — the GSI-only repositories didn't have one) fanned out with `Promise.all`,
  and its `resource.ts` sets `timeoutSeconds: 30` since that is O(tenant size). No S3, no
  async job: the Lambda returns the document and the browser saves it. UI: an `Export data`
  button next to `Add member` in `AdminView.vue` (`saveJsonFile` — the same
  Blob + `<a download>` pattern as `GuessingView.vue`'s CSV; file name
  `<slug>-export-<yyyy-mm-dd>.json`), `api.admin().exportOrganization(id)` on both clients,
  MSW's `mocks/handlers/admin.ts` builds the same envelope from the mock stores (org 1 only;
  no `deletedAt` rows exist there), i18n under `admin.export*`, Vitest
  `services/export.test.ts`, Playwright download assertion in `e2e/admin.spec.ts`. Not
  implemented on the Spring/MariaDB `backend` (no admin endpoints there at all — same as the
  rest of Administration).
- **Query strings: encode spaces as `%20`, never `+`.** Function URLs decode `%XX` escapes in
  `queryStringParameters` but pass a literal `+` through unchanged, so `URLSearchParams` (which
  serialises a space as `+`) would deliver `name=smells+like` to the service. `api-lambda.ts`'s
  `pageQuery` therefore hand-encodes with `encodeURIComponent`, and the three `list*` services
  `trim()` the filter defensively. (The Spring backend decodes `+` as a space, which is why the
  bug only showed on the deployed API.)
- **`amplify.yml`'s `backend` phase is now just** `npm install && npx ampx pipeline-deploy
  --branch $AWS_BRANCH --app-id $AWS_APP_ID` — no Maven, no Docker, no ECR, no custom Amplify
  Console Build image required. This is the actual fix for the Docker build failure that started
  this rewrite.
- **`ui/amplify_outputs.json`** — unchanged from the earlier design: Gen 2's generated client
  config (Cognito User Pool/Client id + `custom.functionUrls` map), committed with placeholder
  values, overwritten by every `ampx sandbox`/`ampx pipeline-deploy`. Never hand-edit it.
  `ui/src/amplify.ts` and `ui/src/lambdaApi.ts` read from it directly instead of `VITE_*`
  build-time env vars.
- **Adding a new endpoint:** add a `functions/<name>/` pair (copy an existing simple one, e.g.
  `hello/`), add its service function to the relevant `lib/services/*.ts`, add one `fnSpecs` entry
  in `functions.ts`, then wire the new `*Fn` import into `backend.ts`'s `defineBackend({...})` and
  `fnResources` map — no other per-function infra to hand-write.
- **Testing:** Vitest (`ui/amplify/lib/**/*.test.ts`, run via `npm run test:unit` from `ui/`).
  `idGenerator.test.ts` and `services/orgasm.test.ts` (25 cases, repository layer mocked via
  `vi.mock`, one per `OrgasmServiceTest.java` validation rule — state-transition rules, the
  ranking tie-break, rating validation) run by default. `services/orgasm.it.test.ts` ports
  `OrgasmServiceIT.java`'s full-workflow happy path against **real DynamoDB Local** — it uses
  dynamic `import()` inside `beforeAll` (after setting `DYNAMODB_ENDPOINT_OVERRIDE` + table-name
  env vars) rather than static imports, since `lib/dynamodb.ts` constructs its client singleton at
  module load time and a static import would be hoisted ahead of the env vars being set. Gated
  behind `RUN_DYNAMO_IT=true` (skipped by default — this repo has no Testcontainers-for-Node
  equivalent wired up, so the container isn't started automatically); see the test file's own
  header comment for the `docker run` + table-creation steps it expects before running it.
- **Read-path cost model, and the memo helper.** `queryAllPages` (`repositories/base.ts`) reads a
  whole partition/GSI before the service slices it in memory, so `size` bounds only the response
  body (capped at `MAX_PAGE_SIZE = 100` in `lib/http.ts`), not the read cost — every `list-*`
  is O(tenant size) RCUs. Real DynamoDB cursor paging would fix that but can't produce the
  `totalElements`/`totalPages` the OpenAPI page contract promises, so it's a deliberate trade-off
  at this scale, not an oversight. What *was* fixed: the per-row "lookup-at-read" hydration
  (`leadContributorName`, ranking names, etc.) was N+1 `GetItem`s; `lib/memo.ts`'s `memoize`
  collapses that to one per distinct id within a request. Use `toPlaylistResponses` (batch) rather
  than mapping `toPlaylistResponse` over a list, and wrap any new per-row lookup in `memoize` —
  keep it request-scoped, never module-level (Lambda reuses execution environments; a
  module-level cache would serve stale rows across invocations).
- **Known gaps (from the 2026-09-11 security review), still open.** No billing alarm/budget is
  defined in the stack — reserved concurrency bounds the burn rate but nothing alerts on spend.
  The body-supplied-actor flaw fixed in the TS API is still present in `backend`'s
  `OrgasmController` (mitigated only by Keycloak + the nginx proxy) and, by design, in the frozen
  Java reference modules.

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

**Domain-restricted registration:** Because both endpoints above are unauthenticated and `organizationSlug` is a free-form field, anyone who can see the org list could otherwise self-register into *any* organization. `Organization.allowedDomain` (nullable, `@JsonIgnore`d — never returned by `GET /api/v1/organizations`) closes this per-org, opt-in: when set, `RegistrationController.assertEmailAllowed` rejects registration with 400 unless the submitted email's domain matches (case-insensitive); a null/blank `allowedDomain` means unrestricted, so existing organizations are unaffected until someone sets the column. `IllegalArgumentException` → 400 is a new `GlobalExceptionHandler` mapping added for this. On the Spring/MariaDB path there is no admin endpoint to set `allowedDomain` — like `Organization` creation itself, it's set directly in the database (e.g. via a migration or manual `UPDATE`). The deployed Lambda API has one: see "Administration" under "TypeScript Lambda API via Amplify Gen 2".

The same gate is mirrored in the deployed TypeScript Cognito path (`ui/amplify/lib/services/registration.ts`'s `assertEmailAllowed`, used by both `register()` and `linkContributor()`) against `OrganizationItem.allowedDomain`, using the Cognito-verified email for `linkContributor`. **Not** ported to the frozen Java reference implementation (`backend-dynamo`'s `RegistrationService`/`LinkContributorService`) — that module tracks the original 34-endpoint parity snapshot the TS port was checked against, not every subsequent app-level feature.

### Structured logging and correlation ids

Both APIs emit one JSON object per line in **Elastic Common Schema** (ECS) shape, with the same
field names, so `backend` and the Lambda functions land in one index with one set of
dashboards/alerts and can be shipped as-is by any OTel/Fluent Bit/Datadog forwarder. Field names
are dotted-ECS and emitted nested (`http.request.id` → `{"http":{"request":{"id":..}}}`); the
canonical list is `backend/.../logging/LogFields.java` and `ui/amplify/lib/logger.ts`'s `Fields`
— add new keys to both.

| Field | Source |
|-------|--------|
| `trace.id` / `span.id` | Backend: Micrometer Tracing (OTel bridge) — W3C `traceparent` in/out, B3 accepted; a new trace when none is sent. Lambda: `traceparent` header → X-Ray `Root` (flattened to the 32-hex OTel form) → fresh random id (`lib/tracing.ts`). |
| `http.request.id` | Inbound `X-Request-ID` if it matches `[A-Za-z0-9._-]{1,64}` (caller-controlled, so anything else is replaced, not trusted); else a UUID (backend) / the Lambda `awsRequestId`. Echoed back in the `X-Request-ID` response header, exposed via CORS on both sides. |
| `http.request.method`, `url.path`, `http.response.status_code`, `event.duration` (ns), `faas.coldstart` (Lambda) | One `HTTP request completed` line per request from `RequestLoggingFilter` / `withAuth` (actuator paths at DEBUG). |
| `tenant.id`, `user.id`, `user.roles` | Opaque ids only: the `tenant_id` claim + JWT `sub` (backend), the Contributor's tenant + id (Lambda). Lambda `admin`-mode requests have no Contributor, so they log `user.id` = the Cognito `sub` and `user.roles: ["admin"]` instead (no `tenant.id`). Scheduled jobs scope `tenant.id` into the MDC themselves since they run outside the filter. |
| `process.thread.name` / `process.thread.id` | Backend only. Thread id is emitted as a dotted top-level key because Boot's ECS formatter already owns the nested `process` object and `JsonWriter` rejects a second member of that name — Elasticsearch treats both spellings as the same field. It reads `Thread.currentThread()`, which is the logging thread only because the console appender is synchronous; don't put an `AsyncAppender` in front of it. |

**Backend mechanics.** Spring Boot's built-in structured logging (`logging.structured.format.console=ecs`
in `application.yml`; `LOG_FORMAT=` empty switches to the human-readable pattern, which the `dev`
and `test` profiles do). `StructuredLogCustomizer` (registered via `logging.structured.json.customizer`,
must keep its no-arg constructor) adds the thread id, re-emits Micrometer's `traceId`/`spanId` MDC
keys as ECS `trace.id`/`span.id` (the raw keys are dropped via `logging.structured.json.exclude`),
and applies `PiiMasker` to every string value. `RequestLoggingFilter` sits after
`TenantResolverFilter` in the security chain and populates/clears the MDC per request; it uses
the SLF4J fluent API (`log.atInfo().addKeyValue(..)`) for per-line fields — key-value pairs nest
by dotted name exactly like MDC keys do. `StructuredLogCustomizerTest` drives Boot's real
`StructuredLogEncoder` with the production properties and pins the wire format; extend it when
adding fields. Note a `@SpringBootTest` can't verify the format: `LogbackLoggingSystem` skips
re-initialisation once initialised in a JVM, so the first test context wins.

`spring-boot-starter-opentelemetry` is on the classpath for the trace ids; **no exporter is
configured by default** (`management.otlp.metrics.export.enabled=false`; the OTLP trace endpoint
is unset). To ship telemetry set the standard Boot properties via environment, e.g.
`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`, `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED`,
`MANAGEMENT_OTLP_METRICS_EXPORT_URL`; `TRACING_SAMPLING_PROBABILITY` (default `1.0`) only affects
export — every request gets a trace id in the logs regardless. `APP_ENV` → `service.environment`,
`HOSTNAME` → `service.node.name`.

**Lambda mechanics.** `lib/logger.ts` is the MDC equivalent on `AsyncLocalStorage`:
`withAuth` wraps each invocation in `runWithLogContext({trace/request/method/path})`, adds
`tenant.id`/`user.id` via `addLogContext` once the Contributor resolves, and everything logged
inside (services, repositories) inherits it — keep it request-scoped, never module-level
(execution environments are reused). Use `log.info/warn/error(message, fields?)` — never
`console.log`, whose output the Node runtime prefixes; the logger writes straight to stdout and
CloudWatch Logs Insights parses the JSON automatically. `LOG_LEVEL` (default `INFO`) and
`APP_ENV` are injected by `backend.ts`. Expected client errors (4xx) log at WARN with
`error.type`/`error.message` only; anything else logs at ERROR with a masked stack trace.

**No PII in logs.** Log opaque ids, never emails, names, usernames, Cognito `sub`/`email`
claims, client IPs, user agents, request bodies, Authorization headers or query strings (the
list endpoints take a `name` filter — `url.path` is logged, `rawQueryString` deliberately is
not). `PiiMasker` (Java) / `maskPii` (TS) scrub anything that looks like an email from every
string in the output (message, MDC, key-value pairs, exception message, stack trace) as a
safety net — it cannot recognise a bare personal name, so it is not a licence to log
`Contributor` objects. `user.id` is the identity provider's opaque subject / the Contributor id
(the Cognito `sub` only for admin-mode requests); if that is ever deemed too identifying for the
log retention in use, drop it from `RequestLoggingFilter`/`withAuth` and nothing else depends on it.

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
| vue-i18n | ^11.4 (+ `@intlify/unplugin-vue-i18n` ^11.2) |
| Playwright | 1.x |
| MapStruct | 1.6.3 |
| frontend-maven-plugin | 1.15.1 (pinned in root POM as `frontend-maven-plugin.version`) |
| Lambda runtime (Java reference implementation) | `public.ecr.aws/lambda/java:25` container image (Graviton arm64) — unused for deploy, see [Architecture decisions](#typescript-lambda-api-via-amplify-gen-2) |
| Nimbus JOSE+JWT (Java reference implementation) | pinned in root POM as `nimbus-jose-jwt.version` |
| Lambda runtime (deployed, TypeScript) | Node.js 22.x, Amplify Gen 2 `defineFunction` default |
| @aws-amplify/backend | ^1.25.0 (`ui/package.json` devDependencies) |
| aws-cdk-lib | ^2.268.0 |
| @aws-sdk/client-dynamodb, @aws-sdk/lib-dynamodb | ^3.1130.0 (`ui/package.json` dependencies) |
| aws-jwt-verify | ^5.2.1 (`ui/package.json` dependencies) |
| spring-boot-starter-opentelemetry | managed by the Spring Boot BOM (`backend/pom.xml`) — Micrometer Tracing OTel bridge + OTLP exporters, export off by default |

All Java dependency versions are managed centrally in the root `pom.xml` `<properties>` block. Update versions there, not in individual module POMs. `node.version` and `frontend-maven-plugin.version` are also in the root `<properties>` block.
