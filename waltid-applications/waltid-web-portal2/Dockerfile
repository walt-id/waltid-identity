# BUILD
FROM node:20-slim AS buildstage

COPY waltid-applications/waltid-web-portal2/. /build

WORKDIR /build
RUN npm ci

RUN npm run build

# RUN
FROM docker.io/node:20-alpine

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

COPY --from=buildstage --chown=appuser:appgroup /build/.output /app/.output

WORKDIR /app

USER appuser
EXPOSE 7105
CMD ["node", "./.output/server/index.mjs"]
