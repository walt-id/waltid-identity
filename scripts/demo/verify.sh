#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/demo.env"

TENANT_PATH="${ORG}.${TENANT}"

TOKEN=$(curl -sf "http://localhost:$PORT/auth/account/emailpass" \
  -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Create verification session via ngrok, with url_config override so request_uri points to ngrok
RESPONSE=$(curl -sf "https://$NGROK_DOMAIN/v1/$TENANT_PATH.verifier2/verifier2-service-api/verification-session/create" \
  -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "ngrok-skip-browser-warning: true" \
  -d "{
    \"flow_type\": \"cross_device\",
    \"url_config\": {
      \"url_host\": \"openid4vp://authorize\",
      \"url_prefix\": \"https://$NGROK_DOMAIN/v1/$TENANT_PATH.verifier2/verifier2-service-api\"
    },
    \"core_flow\": {
      \"dcql_query\": {
        \"credentials\": [{
          \"id\": \"my_mdl\",
          \"format\": \"mso_mdoc\",
          \"meta\": {\"doctype_value\": \"org.iso.18013.5.1.mDL\"},
          \"claims\": [
            {\"path\": [\"org.iso.18013.5.1\", \"family_name\"]},
            {\"path\": [\"org.iso.18013.5.1\", \"given_name\"]},
            {\"path\": [\"org.iso.18013.5.1\", \"birth_date\"]}
          ]
        }]
      },
      \"policies\": {
        \"vc_policies\": [{\"policy\": \"signature\"}]
      }
    }
  }")

SESSION_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])")
REQUEST_URL=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['bootstrapAuthorizationRequestUrl'])")

# Save session ID for status.sh
echo "$SESSION_ID" > "$SCRIPT_DIR/.last-session-id"

echo ""
echo "Verification session: $SESSION_ID"
echo ""
echo "Authorization request URL:"
echo ""
echo "$REQUEST_URL"
echo ""

# Deliver to targets
if [[ "${1:-}" == "--android" || "${1:-}" == "--both" ]] || [ $# -eq 0 ]; then
  if adb devices 2>/dev/null | grep -q "device$"; then
    adb shell am start -a android.intent.action.VIEW -d "'$REQUEST_URL'" 2>/dev/null && \
      echo "  → Delivered to Android emulator" || echo "  ! Android delivery failed"
  fi
fi

if [[ "${1:-}" == "--ios" || "${1:-}" == "--both" ]] || [ $# -eq 0 ]; then
  if xcrun simctl list devices booted 2>/dev/null | grep -q "Booted"; then
    xcrun simctl openurl booted "$REQUEST_URL" 2>/dev/null && \
      echo "  → Delivered to iOS simulator" || echo "  ! iOS delivery failed"
  fi
fi

echo ""
echo "Check result: ./status.sh"
