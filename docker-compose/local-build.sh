#!/bin/bash
#
# Local Docker build script for walt.id Identity services
#
# Uses Gradle Jib by default for fast builds (2-3 min vs 15-20 min with Dockerfiles).
# Jib reuses your local Gradle cache, avoiding redundant dependency downloads.
#
# Usage:
#   ./local-build.sh                    # Build with Jib + start services (default profile: identity)
#   ./local-build.sh --profile all      # Build with Jib + start all services
#   ./local-build.sh build              # Only build images (no docker compose up)
#   ./local-build.sh up                 # Skip build, just start services
#   ./local-build.sh --dockerfile       # Use Dockerfile builds instead of Jib
#   ./local-build.sh clean              # Remove local images and clean Gradle
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Default values
PROFILE="identity"
USE_JIB=true
COMMAND="build-and-up"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --profile)
            PROFILE="$2"
            shift 2
            ;;
        --dockerfile)
            USE_JIB=false
            shift
            ;;
        build)
            COMMAND="build"
            shift
            ;;
        up)
            COMMAND="up"
            shift
            ;;
        clean)
            COMMAND="clean"
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS] [COMMAND]"
            echo ""
            echo "Commands:"
            echo "  (default)     Build images and start services"
            echo "  build         Only build images"
            echo "  up            Only start services (skip build)"
            echo "  clean         Remove local images and clean Gradle"
            echo ""
            echo "Options:"
            echo "  --profile <name>    Docker Compose profile (default: identity)"
            echo "                      Available: services, apps, identity, all"
            echo "  --dockerfile        Use Dockerfile builds instead of Jib"
            echo "  -h, --help          Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                          # Build with Jib + start identity profile"
            echo "  $0 --profile all            # Build + start all services"
            echo "  $0 build                    # Only build images"
            echo "  $0 --dockerfile             # Use Dockerfile builds"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# Build images using Jib
build_with_jib() {
    echo "Building images with Gradle Jib (fast, uses local cache)..."
    cd "$PROJECT_ROOT"

    # Build all service images to local Docker daemon
    ./gradlew \
        :waltid-services:waltid-issuer-api:jibDockerBuild \
        :waltid-services:waltid-verifier-api:jibDockerBuild \
        :waltid-services:waltid-verifier-api2:jibDockerBuild \
        :waltid-services:waltid-wallet-api:jibDockerBuild

    echo ""
    echo "Jib build complete. Images created:"
    docker images | grep -E "^waltid/(issuer-api|verifier-api|verifier-api2|wallet-api)" | head -8
}

# Build images using Dockerfiles (fallback)
build_with_dockerfile() {
    echo "Building images with Dockerfiles (slower, no shared cache)..."
    cd "$PROJECT_ROOT"

    # Build each service using its Dockerfile
    for service in issuer-api verifier-api verifier-api2 wallet-api; do
        echo ""
        echo "Building waltid-${service}..."
        docker build \
            -t "waltid/${service}:latest" \
            -f "waltid-services/waltid-${service}/Dockerfile" \
            .
    done

    echo ""
    echo "Dockerfile build complete. Images created:"
    docker images | grep -E "^waltid/(issuer-api|verifier-api|verifier-api2|wallet-api)" | head -8
}

# Start services with docker compose
start_services() {
    echo "Starting services with profile: $PROFILE"
    cd "$SCRIPT_DIR"

    docker compose \
        -f docker-compose.yaml \
        -f docker-compose.local.yaml \
        --profile "$PROFILE" \
        up "$@"
}

# Clean local images
clean() {
    echo "Cleaning local images..."
    docker rmi waltid/issuer-api:latest 2>/dev/null || true
    docker rmi waltid/verifier-api:latest 2>/dev/null || true
    docker rmi waltid/verifier-api2:latest 2>/dev/null || true
    docker rmi waltid/wallet-api:latest 2>/dev/null || true

    echo "Cleaning Gradle build cache..."
    cd "$PROJECT_ROOT"
    ./gradlew clean

    echo "Clean complete."
}

# Main execution
case $COMMAND in
    build)
        if [ "$USE_JIB" = true ]; then
            build_with_jib
        else
            build_with_dockerfile
        fi
        ;;
    up)
        start_services
        ;;
    clean)
        clean
        ;;
    build-and-up)
        if [ "$USE_JIB" = true ]; then
            build_with_jib
        else
            build_with_dockerfile
        fi
        echo ""
        start_services
        ;;
esac
