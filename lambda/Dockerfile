# syntax=docker/dockerfile:1
#
# Build context: repo root
#   docker build -f lambda/Dockerfile -t orgasm-lambda:latest .
#
# For Graviton (arm64) — match template.yaml Architectures: [arm64]:
#   docker buildx build --platform linux/arm64 \
#       -f lambda/Dockerfile -t orgasm-lambda:latest .
#
# Push to ECR:
#   aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
#   docker tag orgasm-lambda:latest <account>.dkr.ecr.<region>.amazonaws.com/orgasm-lambda:latest
#   docker push <account>.dkr.ecr.<region>.amazonaws.com/orgasm-lambda:latest

# ── Stage 1: GraalVM native build ────────────────────────────────────────────
FROM ghcr.io/graalvm/graalvm-community:25 AS build

RUN microdnf install -y findutils && microdnf clean all

WORKDIR /workspace

# Copy Maven wrapper + root POM first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Maven needs every module's pom.xml present to resolve the root reactor (the root POM's
# <modules> list), even though `-pl lambda -am` below only ever BUILDS lambda's actual
# dependency chain (backend-dynamo → lambda — lambda does NOT depend on sdk/sdk-models/
# backend-core, see CLAUDE.md "DynamoDB persistence for Lambda"). Only backend-dynamo/ and
# lambda/ get their src/ copied below, so the other modules' compilation is never triggered.
COPY sdk-models/pom.xml     sdk-models/pom.xml
COPY sdk/pom.xml            sdk/pom.xml
COPY sdk-java8/pom.xml      sdk-java8/pom.xml
COPY sdk-java11/pom.xml     sdk-java11/pom.xml
COPY sdk-typescript/pom.xml sdk-typescript/pom.xml
COPY backend-core/pom.xml   backend-core/pom.xml
COPY backend-dynamo/pom.xml backend-dynamo/pom.xml
COPY backend/pom.xml        backend/pom.xml
COPY lambda/pom.xml         lambda/pom.xml
COPY ui/pom.xml             ui/pom.xml

# Warm up the Maven cache — layer is re-used until any POM changes
RUN ./mvnw dependency:resolve -pl lambda -am -DskipTests -Pnative -q 2>&1 | tail -3 || true

# Copy sources
COPY backend-dynamo/src/ backend-dynamo/src/
COPY lambda/src/         lambda/src/

# Compile + Spring AOT + GraalVM native image → target/bootstrap
RUN ./mvnw package -pl lambda -am -DskipTests -Pnative -q

# ── Stage 2: Lambda custom runtime ───────────────────────────────────────────
FROM public.ecr.aws/lambda/provided:al2023

COPY --from=build /workspace/lambda/target/bootstrap /var/task/bootstrap
RUN chmod 755 /var/task/bootstrap

# Default handler; override per-function with ImageConfig.Command in template.yaml
CMD ["com.orgasm.lambda.HelloHandler::handleRequest"]
ENTRYPOINT ["/var/task/bootstrap"]
