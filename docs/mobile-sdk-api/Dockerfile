FROM nginx:1.27-alpine

RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

COPY nginx.conf /etc/nginx/nginx.conf
COPY html/ /usr/share/nginx/html/

# Allow the non-root user to run nginx (pid file, cache, listen on 8080).
RUN chown -R appuser:appgroup /usr/share/nginx/html \
    && touch /run/nginx.pid \
    && chown -R appuser:appgroup /run/nginx.pid /var/cache/nginx

USER appuser
EXPOSE 8080
ENTRYPOINT ["nginx", "-g", "daemon off;"]
