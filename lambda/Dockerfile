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

# Copy only the module POMs in the lambda dependency chain (sdk-models→sdk→backend-core→lambda)
COPY sdk-models/pom.xml  sdk-models/pom.xml
COPY sdk/pom.xml          sdk/pom.xml
COPY backend-core/pom.xml backend-core/pom.xml
COPY lambda/pom.xml       lambda/pom.xml

# Warm up the Maven cache — layer is re-used until any POM changes
RUN ./mvnw dependency:resolve -pl lambda -am -DskipTests -Pnative -q 2>&1 | tail -3 || true

# Copy sources
COPY sdk-models/src/    sdk-models/src/
COPY sdk/src/            sdk/src/
COPY backend-core/src/   backend-core/src/
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
