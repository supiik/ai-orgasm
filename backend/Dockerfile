# syntax=docker/dockerfile:1
#
# Build context: repo root
#   docker build -f backend/Dockerfile -t orgasm-backend:latest .
#
# Runtime configuration (env vars at startup — all have sensible defaults
# that match application.yml; override for real deployments):
#   DATASOURCE_APP_URL        JDBC URL for the app database
#   DATASOURCE_APP_USERNAME   DB username
#   DATASOURCE_APP_PASSWORD   DB password
#   DATASOURCE_BILLING_URL / _USERNAME / _PASSWORD  (billing DB)
#   SPRING_DATA_REDIS_HOST    Redis host (default: localhost)

# ── Stage 1: GraalVM native build ────────────────────────────────────────────
FROM ghcr.io/graalvm/graalvm-community:25 AS build

RUN microdnf install -y findutils && microdnf clean all

WORKDIR /workspace

# Copy Maven wrapper + root POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy only the module POMs in the backend dependency chain (layer cache)
COPY sdk-models/pom.xml  sdk-models/pom.xml
COPY sdk/pom.xml          sdk/pom.xml
COPY backend-core/pom.xml backend-core/pom.xml
COPY backend/pom.xml      backend/pom.xml

# Warm up the Maven dependency cache
RUN ./mvnw dependency:resolve -pl backend -am -DskipTests -Pnative -q 2>&1 | tail -3 || true

# Copy sources
COPY sdk-models/src/    sdk-models/src/
COPY sdk/src/            sdk/src/
COPY backend-core/src/   backend-core/src/
COPY backend/src/        backend/src/

# Compile + Spring AOT + GraalVM native image → target/orgasm-backend
RUN ./mvnw package -pl backend -am -DskipTests -Pnative -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# ubuntu:24.04 provides the glibc/libstdc++ that the OL9-built executable needs.
FROM ubuntu:24.04

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/orgasm-backend /app/orgasm-backend
RUN chmod 755 /app/orgasm-backend

EXPOSE 8080

ENTRYPOINT ["/app/orgasm-backend"]
