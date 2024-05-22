FROM docker:latest

# Install necessary dependencies for Docker Compose
RUN apk add --no-cache \
    curl \
    && pip3 install --no-cache-dir docker-compose

# Copy your docker-compose.yml file into the image
COPY docker-compose.yml /app/docker-compose.yml
