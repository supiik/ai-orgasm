# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout

Four independent sub-projects sharing a root directory:

| Directory | Type | Artifact |
|-----------|------|----------|
| `sdk/` | Java 21 library | `orgasm-sdk` JAR – consumed by `backend` and `lambda` |
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
```

Preview features are enabled compiler-wide (`--enable-preview`); the Surefire argLine passes the same flag so tests compile and run cleanly.

## Mutation testing (PIT)

PIT is bound to the `verify` phase in the parent POM alongside JaCoCo — it runs automatically on `mvn verify`.

```bash
# Full verify: compiles, tests, JaCoCo coverage + PiTest mutation (all modules)
mvn verify

# Skip mutation testing for a faster feedback loop
mvn verify -Dpitest.skip=true

# Run mutation coverage in isolation for a single module
mvn pitest:mutationCoverage -pl sdk

# Reports land at target/pit-reports/index.html (timestamped dirs disabled)
```

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

## Key version pins

| Technology | Version |
|------------|---------|
| Java | 21 LTS |
| Spring Boot | 4.0.6 |
| AWS SDK v2 | 2.31.0 |
| Node | 22 LTS (enforced via `engines` in `package.json`) |
| Vue | 3.5.x |
| Vite | 6.x |
| Lambda runtime | `java21` (Graviton arm64) |

All Java dependency versions are managed centrally in the root `pom.xml` `<properties>` block. Update versions there, not in individual module POMs.
