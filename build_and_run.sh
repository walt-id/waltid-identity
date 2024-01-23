#!/bin/bash

# Define your Dockerfile directories and corresponding service names
DOCKERFILE_DIRS=("waltid-wallet-api" "waltid-web-wallet" "waltid-issuer-api" "waltid-verifier-api" "waltid-web-portal")
SERVICES=("wallet-api" "waltid-web-wallet" "issuer-api" "verifier-api" "waltid-web-portal")

# Build Docker images
for ((i=0; i<${#DOCKERFILE_DIRS[@]}; i++)); do
  DOCKERFILE_PATH="${DOCKERFILE_DIRS[i]}/Dockerfile"
  SERVICE_NAME="${SERVICES[i]}"

  echo "Building image for ${SERVICE_NAME} using ${DOCKERFILE_PATH}"

  # Build Docker image from the root directory
  docker build -t "waltid/${SERVICE_NAME}" -f "${DOCKERFILE_PATH}" .
done
# Change directory to where docker-compose.yml is located
COMPOSE_DIR="docker-compose"
cd "${COMPOSE_DIR}" || exit 1


# Run Docker Compose
docker-compose up --build

# Clean up: Uncomment the following line if you want to remove the built images after running Docker Compose
# docker-compose down --rmi local
