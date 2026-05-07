#!/usr/bin/env bash

set -euo pipefail

# functions
ping()
{
    local service="$1"
    local port="$2"
    local livez_url="http://localhost:${port}/livez"
    local max_attempts=60
    local attempt=1

    echo "Waiting for ${service}-api on ${livez_url}"

    until nc -z localhost "$port" >/dev/null 2>&1; do
        if [[ "$attempt" -ge "$max_attempts" ]]; then
            echo "${service}-api did not open port ${port} in time"
            return 1
        fi
        printf '.'
        sleep 2
        ((attempt++))
    done

    attempt=1
    until response="$(curl -fsS "$livez_url" 2>/dev/null)" \
        && printf '%s' "$response" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"Healthy"'; do
        if [[ "$attempt" -ge "$max_attempts" ]]; then
            echo "${service}-api did not become healthy in time"
            curl -sS "$livez_url" || true
            return 1
        fi
        printf '.'
        sleep 2
        ((attempt++))
    done

    echo "${service}-api is healthy"
}

# main
ping wallet 7001
ping issuer 7002
ping verifier 7003
