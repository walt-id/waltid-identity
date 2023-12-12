# BUILD
FROM docker.io/gplane/pnpm:8.6 as buildstage

COPY . /build

WORKDIR /build
RUN pnpm install

RUN pnpm build

# RUN
FROM docker.io/node:20-alpine
COPY --from=buildstage /build/package.json ./app/package.json
COPY --from=buildstage /build/node_modules ./app/node_modules
COPY --from=buildstage /build/.next ./app/.next
COPY --from=buildstage /build/public ./app/public

WORKDIR /app

EXPOSE 3000
CMD ["npm", "start"]

