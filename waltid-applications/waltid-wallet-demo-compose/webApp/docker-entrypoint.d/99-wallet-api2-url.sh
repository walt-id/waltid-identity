#!/bin/sh
set -e
if [ -f /usr/share/nginx/html/index.html ]; then
    if [ -n "${WALLET_API2_PUBLIC_URL:-}" ]; then
        sed -i "s|__WALLET_API2_BASE_URL__|${WALLET_API2_PUBLIC_URL}|g" /usr/share/nginx/html/index.html
    fi
    if [ -n "${WALLET_API_FLAVOR:-}" ]; then
        sed -i "s|__WALLET_API_FLAVOR__|${WALLET_API_FLAVOR}|g" /usr/share/nginx/html/index.html
    fi
fi
