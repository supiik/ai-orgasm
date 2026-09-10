# syntax=docker/dockerfile:1
#
# Build context: repo root. Run `./mvnw -pl lambda -am -DskipTests package` first to produce
# lambda/target/orgasm-lambda-*.jar (the default, non-native profile's shaded fat jar) — this
# Dockerfile only packages it, it does not run Maven itself.
#
#   ./mvnw -pl lambda -am -DskipTests package
#   docker build -f lambda/Dockerfile -t orgasm-lambda:latest .
#
# One image is shared by every Lambda function; each function overrides CMD at the
# AWS::Lambda::Function level (see ui/amplify/backend.ts) to select its handler class — the
# CMD below is only the default used if the image is run without an override.
#
# For Graviton (arm64) — matches ui/amplify/backend.ts's Architecture.ARM_64:
#   docker buildx build --platform linux/arm64 -f lambda/Dockerfile -t orgasm-lambda:latest .
#
# Push to ECR (repo name/region must match LAMBDA_ECR_REPO in amplify.yml):
#   aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.<region>.amazonaws.com
#   docker tag orgasm-lambda:latest <account>.dkr.ecr.<region>.amazonaws.com/orgasm-lambda-<env>:<tag>
#   docker push <account>.dkr.ecr.<region>.amazonaws.com/orgasm-lambda-<env>:<tag>

FROM public.ecr.aws/lambda/java:25

# A single shaded fat jar in lib/ is enough — the base image's runtime bootstrap adds every
# *.jar under ${LAMBDA_TASK_ROOT}/lib to the classpath automatically. Build context is the
# repo root (see the docker build command above), so this path is repo-root-relative.
COPY lambda/target/orgasm-lambda-*.jar ${LAMBDA_TASK_ROOT}/lib/

CMD ["com.orgasm.lambda.HelloHandler::handleRequest"]
