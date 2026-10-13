# syntax=docker/dockerfile:1

# Build stage: install the locked dependencies and compile the static bundle.
FROM node:26.11.1-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# Runtime stage: unprivileged nginx (uid 101) serving the bundle on port 8080.
FROM nginxinc/nginx-unprivileged:1.31.6-alpine

# Address of the API, substituted into the nginx template when the container starts.
ENV BACKEND_UPSTREAM=backend:8080 \
    NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/index.html || exit 1
