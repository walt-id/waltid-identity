# Build on the runner's native arch. The Vite dist is platform-independent, and
# Node/npm under QEMU on Alpine/ARM64 deadlocks in GitHub Actions.
FROM --platform=$BUILDPLATFORM node:20-slim AS builder

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

# 1.27-alpine matches the other frontend images. alpine3.23 + QEMU is a known hang.
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
