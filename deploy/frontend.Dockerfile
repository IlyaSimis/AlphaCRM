# syntax=docker/dockerfile:1.7
# Сборка frontend и раздача статики через nginx, который же проксирует /api на backend.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY index.html vite.config.ts tsconfig.json tsconfig.app.json tsconfig.node.json .oxlintrc.json ./
COPY public public
COPY src src
# API на том же домене, что и интерфейс: CORS не нужен.
ENV VITE_API_URL=/api/v1
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null || exit 1
