# BUILD
FROM node:20-slim AS buildstage
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

COPY waltid-applications/waltid-web-portal/. /build

WORKDIR /build
RUN pnpm install

RUN pnpm build

# RUN
FROM docker.io/node:20-alpine

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

COPY --from=buildstage --chown=appuser:appgroup /build/public ./app/public
COPY --from=buildstage --chown=appuser:appgroup /build/.next/standalone ./app
COPY --from=buildstage --chown=appuser:appgroup /build/.next/static ./app/.next/static

WORKDIR /app

USER appuser
EXPOSE 7102
CMD ["node", "server.js"]
