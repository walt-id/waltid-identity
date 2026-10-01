# Debian/glibc, not Alpine: Node + npm + better-sqlite3 deadlock under QEMU on musl.
FROM node:22-slim AS buildstage
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm config set fetch-retries 5 \
    && npm config set fetch-retry-mintimeout 20000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm ci --legacy-peer-deps --no-audit --no-fund
COPY ./ /build

RUN npm run build

FROM node:22-slim

RUN groupadd --system --gid 10001 appgroup && \
    useradd --system --uid 10001 --gid appgroup --no-create-home --home-dir /app --shell /usr/sbin/nologin appuser

COPY --from=buildstage --chown=appuser:appgroup /build/.output/ /app
WORKDIR /app

USER appuser
EXPOSE 3000
ENTRYPOINT ["node", "server/index.mjs"]
