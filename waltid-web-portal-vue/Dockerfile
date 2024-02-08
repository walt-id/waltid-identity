# BUILD
FROM docker.io/oven/bun:1 as buildstage

COPY waltid-web-portal-vue /build

WORKDIR /build
RUN bun install

RUN bun run build

# RUN
FROM docker.io/node:alpine
COPY --from=buildstage /build/.output/ /app

WORKDIR /app

EXPOSE 7102
ENTRYPOINT node server/index.mjs

