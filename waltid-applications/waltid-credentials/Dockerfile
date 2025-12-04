FROM docker.io/node:alpine AS buildstage
RUN apk add --update nodejs git
COPY ./package.json /build/
WORKDIR /build
RUN npm install
COPY ./ /build

RUN npm run build
# RUN
FROM docker.io/node:alpine
COPY --from=buildstage /build/.output/ /app
WORKDIR /app
EXPOSE 3000
ENTRYPOINT ["node", "server/index.mjs"]
