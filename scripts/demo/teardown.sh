#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/demo.env"

echo "Stopping enterprise stack..."
docker rm -f waltid-enterprise-ui 2>/dev/null || true
docker compose -f "$QUICKSTART_DIR/docker-compose.yml" down

echo ""
echo "Stack stopped. Volumes preserved (use 'docker compose down -v' to wipe data)."
