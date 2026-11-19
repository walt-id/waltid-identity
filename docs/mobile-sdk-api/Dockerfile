FROM nginx:1.31.9-trixie

RUN groupadd -g 10001 -r appgroup && \
    useradd -u 10001 -r -g appgroup -M -s /usr/sbin/nologin appuser

COPY nginx.conf /etc/nginx/nginx.conf
# Kotlin (Dokka) docs, served on sdk.docs.walt.id.
COPY html/ /usr/share/nginx/html/
# Swift WalletSDK (DocC) docs, served on ios-sdk.docs.walt.id.
COPY ios-html/ /usr/share/nginx/ios-html/

# Allow the non-root user to run nginx (pid file, cache, listen on 8080).
RUN chown -R appuser:appgroup /usr/share/nginx/html /usr/share/nginx/ios-html \
    && touch /run/nginx.pid \
    && chown -R appuser:appgroup /run/nginx.pid /var/cache/nginx

USER appuser
EXPOSE 8080
ENTRYPOINT ["nginx", "-g", "daemon off;"]
