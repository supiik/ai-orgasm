# syntax=docker/dockerfile:1
#
# Build context: ui/ directory
#   docker build -t orgasm-ui:latest ui/
#
# Runtime configuration (set at container startup):
#   BACKEND_URL  URL of the backend service (default: http://backend:8080)
#
# Example with Docker Compose:
#   services:
#     ui:
#       image: orgasm-ui:latest
#       environment:
#         BACKEND_URL: http://backend:8080

# ── Stage 1: Vite build ───────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app

# Install dependencies first (layer cached until package files change)
COPY package*.json ./
RUN npm ci

# Copy sources and build — fails on TypeScript errors
COPY . .
RUN npm run build

# ── Stage 2: Nginx serve ──────────────────────────────────────────────────────
FROM nginx:alpine-slim

# Place the config as a template; the nginx image's entrypoint runs envsubst
# on files in /etc/nginx/templates/ before starting nginx.
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template

EXPOSE 80

# BACKEND_URL is substituted into the nginx config at container startup
ENV BACKEND_URL=http://backend:8080
