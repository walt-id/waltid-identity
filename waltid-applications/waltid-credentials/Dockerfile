FROM node:22-alpine AS buildstage
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm config set fetch-retries 5 \
    && npm config set fetch-retry-mintimeout 20000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm ci --legacy-peer-deps --no-audit --no-fund
COPY ./ /build

RUN npm run build
# RUN
FROM node:22-alpine

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

COPY --from=buildstage --chown=appuser:appgroup /build/.output/ /app
WORKDIR /app

USER appuser
EXPOSE 3000
ENTRYPOINT ["node", "server/index.mjs"]
