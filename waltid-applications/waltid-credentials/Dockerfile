FROM docker.io/node:alpine AS buildstage
COPY ./package.json /build/
WORKDIR /build
RUN npm install
COPY ./ /build

RUN npm run build
# RUN
FROM docker.io/node:alpine

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

COPY --from=buildstage --chown=appuser:appgroup /build/.output/ /app
WORKDIR /app

USER appuser
EXPOSE 3000
ENTRYPOINT ["node", "server/index.mjs"]
