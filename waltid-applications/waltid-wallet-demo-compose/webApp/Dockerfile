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
    && DIST="waltid-applications/waltid-wallet-demo-compose/webApp/build/dist/wasmJs/productionExecutable" \
    && test -d "$DIST" \
    && mkdir -p /out \
    && cp -a "$DIST"/. /out/ \
    && if [ ! -d /out/composeResources ]; then \
        RESOURCES="waltid-applications/waltid-wallet-demo-compose/sharedUI/build/processedResources/wasmJs/main/composeResources"; \
        test -d "$RESOURCES"; \
        cp -a "$RESOURCES" /out/composeResources; \
    fi \
    && test -d /out/composeResources \
    && ENTRY="$(sed -n 's/.*<script src="\([^"]*\)".*/\1/p' /out/index.html | head -n1)" \
    && test -n "$ENTRY" \
    && test -f "/out/$ENTRY" \
    && grep -q "__WALLET_API2_BASE_URL__" /out/index.html

# RUN
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY --from=build /out/ /usr/share/nginx/html/
COPY waltid-applications/waltid-wallet-demo-compose/webApp/nginx.conf /etc/nginx/conf.d/default.conf
COPY waltid-applications/waltid-wallet-demo-compose/webApp/docker-entrypoint.d/98-wallet-branding.sh /docker-entrypoint.d/98-wallet-branding.sh
COPY waltid-applications/waltid-wallet-demo-compose/webApp/docker-entrypoint.d/99-wallet-api2-url.sh /docker-entrypoint.d/99-wallet-api2-url.sh

USER root
RUN chmod +x /docker-entrypoint.d/98-wallet-branding.sh /docker-entrypoint.d/99-wallet-api2-url.sh \
    && chown -R 10001:0 /usr/share/nginx/html /var/cache/nginx /etc/nginx

USER 10001

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
