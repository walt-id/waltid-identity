# BUILD
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build

# Kotlin's bundled Node needs libatomic on Debian/Ubuntu (yarn/npm otherwise exit 127).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .

RUN chmod +x ./gradlew \
    && sed -i 's/-Xmx6[gG]/-Xmx3g/g' gradle.properties \
    && ./gradlew --no-daemon \
        :waltid-applications:waltid-wallet-demo-compose:webApp:wasmJsBrowserDistribution \
        -PenableWalletDemoComposeWeb=true \
    && DIST="$(find waltid-applications/waltid-wallet-demo-compose/webApp/build -type d -name productionExecutable | head -n 1)" \
    && test -n "$DIST" \
    && mkdir -p /out \
    && cp -a "$DIST"/. /out/

# RUN
FROM nginx:1.27-alpine

COPY --from=build /out/ /usr/share/nginx/html/
# Webpack productionExecutable omits wasmJsMain/resources; overlay the SPA shell.
COPY waltid-applications/waltid-wallet-demo-compose/webApp/src/wasmJsMain/resources/index.html /usr/share/nginx/html/index.html
COPY waltid-applications/waltid-wallet-demo-compose/webApp/src/wasmJsMain/resources/branding.json /usr/share/nginx/html/branding.json
COPY waltid-applications/waltid-wallet-demo-compose/webApp/nginx.conf /etc/nginx/conf.d/default.conf
COPY waltid-applications/waltid-wallet-demo-compose/webApp/docker-entrypoint.d/98-wallet-branding.sh /docker-entrypoint.d/98-wallet-branding.sh
COPY waltid-applications/waltid-wallet-demo-compose/webApp/docker-entrypoint.d/99-wallet-api2-url.sh /docker-entrypoint.d/99-wallet-api2-url.sh

RUN chmod +x /docker-entrypoint.d/98-wallet-branding.sh /docker-entrypoint.d/99-wallet-api2-url.sh

EXPOSE 80
