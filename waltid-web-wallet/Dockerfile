# BUILD
FROM docker.io/gplane/pnpm:8 as buildstage

COPY waltid-web-wallet/web /build

WORKDIR /build
RUN pnpm install

RUN pnpm build

# RUN
FROM docker.io/node:alpine
COPY --from=buildstage /build/.output/ /app

WORKDIR /app

EXPOSE 7100
ENTRYPOINT node server/index.mjs

