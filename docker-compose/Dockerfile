FROM docker:latest

# Install necessary dependencies for Docker Compose
RUN apk add --no-cache \
    curl \
    && pip3 install --no-cache-dir docker-compose

# Create non-root user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

RUN mkdir -p /app && chown -R appuser:appgroup /app

# Copy your docker-compose.yml file into the image
COPY --chown=appuser:appgroup docker-compose.yml /app/docker-compose.yml

WORKDIR /app
USER appuser
