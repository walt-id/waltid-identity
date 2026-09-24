#!/bin/sh
set -e
if [ -n "${WALLET_API2_PUBLIC_URL:-}" ] && [ -f /usr/share/nginx/html/index.html ]; then
    sed -i "s|__WALLET_API2_BASE_URL__|${WALLET_API2_PUBLIC_URL}|g" /usr/share/nginx/html/index.html
fi
