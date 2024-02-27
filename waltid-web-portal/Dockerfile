# BUILD
FROM docker.io/gplane/pnpm:8.6 as buildstage

COPY waltid-web-portal/. /build

WORKDIR /build
RUN pnpm install

RUN pnpm build

# RUN
FROM docker.io/node:20-alpine
COPY --from=buildstage /build/public ./app/public
COPY --from=buildstage /build/.next/standalone ./app
COPY --from=buildstage /build/.next/static ./app/.next/static

WORKDIR /app

EXPOSE 7102
CMD ["node", "server.js"]

