# Build on the runner's native arch. The Vite dist is platform-independent, and
# Node/npm under QEMU on Alpine/ARM64 deadlocks in GitHub Actions.
FROM --platform=$BUILDPLATFORM node:20-slim AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.31.9-trixie

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN sed -i 's#^pid .*#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && chown -R 101:0 /var/cache/nginx /etc/nginx/conf.d

USER 101

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
