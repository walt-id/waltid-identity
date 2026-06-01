#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/demo.env"

TENANT_PATH="${ORG}.${TENANT}"

TOKEN=$(curl -sf "http://localhost:$PORT/auth/account/emailpass" \
  -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Create offer via ngrok so embedded URLs are reachable from emulators/simulators
OFFER_URL=$(curl -sf "https://$NGROK_DOMAIN/v2/$TENANT_PATH.issuer2-noattest.mdl-profile/issuer-service-api/credentials/offers" \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "ngrok-skip-browser-warning: true" \
  -d '{"authMethod": "PRE_AUTHORIZED"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['credentialOffer'])")

echo ""
echo "Credential offer URL:"
echo ""
echo "$OFFER_URL"
echo ""

# Deliver to targets
if [[ "${1:-}" == "--android" || "${1:-}" == "--both" ]] || [ $# -eq 0 ]; then
  if adb devices 2>/dev/null | grep -q "device$"; then
    adb shell am start -a android.intent.action.VIEW -d "'$OFFER_URL'" 2>/dev/null && \
      echo "  → Delivered to Android emulator" || echo "  ! Android delivery failed"
  fi
fi

if [[ "${1:-}" == "--ios" || "${1:-}" == "--both" ]] || [ $# -eq 0 ]; then
  if xcrun simctl list devices booted 2>/dev/null | grep -q "Booted"; then
    xcrun simctl openurl booted "$OFFER_URL" 2>/dev/null && \
      echo "  → Delivered to iOS simulator" || echo "  ! iOS delivery failed"
  fi
fi
