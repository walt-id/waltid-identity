# use the official Bun image
# see all versions at https://hub.docker.com/r/oven/bun/tags

FROM gplane/pnpm:9.6 AS base
#FROM oven/bun:1 AS base

WORKDIR /app

# install dependencies into temp directory
# this will cache them and speed up future builds
FROM base AS install
RUN mkdir -p /temp/dev
#COPY waltid-applications/waltid-web-wallet/package.json waltid-applications/waltid-web-wallet/bun.lockb /temp/dev/
COPY waltid-applications/waltid-web-wallet/package.json waltid-applications/waltid-web-wallet/pnpm-lock.yaml /temp/dev/
RUN cd /temp/dev && pnpm install --frozen-lockfile
#RUN cd /temp/dev && bun install --frozen-lockfile

# install with --production (exclude devDependencies)
RUN mkdir -p /temp/prod
#COPY waltid-applications/waltid-web-wallet/package.json waltid-applications/waltid-web-wallet/bun.lockb /temp/prod/
COPY waltid-applications/waltid-web-wallet/package.json waltid-applications/waltid-web-wallet/pnpm-lock.yaml /temp/prod/
#RUN cd /temp/prod && bun install --frozen-lockfile --production
RUN cd /temp/prod && pnpm install --frozen-lockfile --production

# copy node_modules from temp directory
# then copy all (non-ignored) project files into the image
FROM base AS prerelease
COPY --from=install /temp/dev/node_modules node_modules
COPY waltid-applications/waltid-web-wallet/ .
RUN ls -la

# [optional] tests & build
ENV NODE_ENV=production
RUN pnpm run build
#RUN bun run build

# copy production dependencies and source code into final image
FROM base AS release
COPY --from=install /temp/prod/node_modules node_modules
COPY --from=prerelease /app/.output/ .
COPY --from=prerelease /app/package.json .

# run the app
RUN chmod -R a+r .
RUN groupadd -r app && useradd -r -g app app # required for pnpm
USER app
EXPOSE 7101/tcp
ENV PORT=7101
ENTRYPOINT [ "node", "server/index.mjs" ]
#ENTRYPOINT [ "bun", "run", "server/index.mjs" ]
