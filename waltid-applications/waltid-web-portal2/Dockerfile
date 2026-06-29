# BUILD
FROM node:20-slim AS buildstage
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable && corepack prepare pnpm@10.11.1 --activate

COPY waltid-applications/waltid-web-portal2/. /build

WORKDIR /build
RUN pnpm install

RUN pnpm build

# RUN
FROM docker.io/node:20-alpine

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

COPY --from=buildstage --chown=appuser:appgroup /build/.output /app/.output

WORKDIR /app

USER appuser
EXPOSE 7102
CMD ["node", "./.output/server/index.mjs"]
