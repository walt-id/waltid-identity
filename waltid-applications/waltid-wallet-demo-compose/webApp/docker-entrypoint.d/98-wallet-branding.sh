#!/bin/sh
set -e

json_escape() {
    printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

branding_set=0
for var in \
    WALLET_BRAND_APP_TITLE \
    WALLET_BRAND_PRIMARY \
    WALLET_BRAND_ON_PRIMARY \
    WALLET_BRAND_SECONDARY \
    WALLET_BRAND_ON_SECONDARY \
    WALLET_BRAND_PRIMARY_CONTAINER \
    WALLET_BRAND_ON_PRIMARY_CONTAINER
do
    eval "value=\${$var:-}"
    if [ -n "$value" ]; then
        branding_set=1
        break
    fi
done

if [ "$branding_set" -eq 0 ]; then
    exit 0
fi

APP_TITLE="${WALLET_BRAND_APP_TITLE:-walt.id Wallet}"
PRIMARY="${WALLET_BRAND_PRIMARY:-#0573F0}"
ON_PRIMARY="${WALLET_BRAND_ON_PRIMARY:-#FFFFFF}"
SECONDARY="${WALLET_BRAND_SECONDARY:-#ADC6FF}"
ON_SECONDARY="${WALLET_BRAND_ON_SECONDARY:-#002E69}"
PRIMARY_CONTAINER="${WALLET_BRAND_PRIMARY_CONTAINER:-#D8E2FF}"
ON_PRIMARY_CONTAINER="${WALLET_BRAND_ON_PRIMARY_CONTAINER:-#002E69}"

cat > /usr/share/nginx/html/branding.json <<EOF
{
  "appTitle": "$(json_escape "$APP_TITLE")",
  "primary": "$(json_escape "$PRIMARY")",
  "onPrimary": "$(json_escape "$ON_PRIMARY")",
  "secondary": "$(json_escape "$SECONDARY")",
  "onSecondary": "$(json_escape "$ON_SECONDARY")",
  "primaryContainer": "$(json_escape "$PRIMARY_CONTAINER")",
  "onPrimaryContainer": "$(json_escape "$ON_PRIMARY_CONTAINER")"
}
EOF
