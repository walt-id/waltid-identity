FROM docker.io/gplane/pnpm as buildstage
COPY ./package.json /build/
WORKDIR /build
RUN pnpm install
COPY ./ /build
RUN pnpm run build
# RUN
FROM docker.io/node:20-alpine
COPY --from=buildstage /build/.output/ /app
WORKDIR /app
EXPOSE 3000
ENTRYPOINT node server/index.mjs