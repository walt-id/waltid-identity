FROM node AS base

WORKDIR /app

# install dependencies & build
FROM base AS prerelease

COPY waltid-applications/waltid-web-wallet/apps/waltid-dev-wallet/package.json ./apps/waltid-dev-wallet/
RUN cd apps/waltid-dev-wallet && npm install

COPY waltid-applications/waltid-web-wallet .
RUN cd apps/waltid-dev-wallet && npm run build

# copy build output into final image
FROM node:alpine AS release
ENV NODE_ENV=production

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

WORKDIR /app
COPY --from=prerelease --chown=appuser:appgroup /app/apps/waltid-dev-wallet/.output/ .

USER appuser
EXPOSE 7104/tcp
ENV PORT=7104
ENTRYPOINT [ "node", "server/index.mjs" ]