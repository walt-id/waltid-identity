#!/usr/bin/env bash
#
# E2E test for Android wallet demo: receive + present credential flows.
#
# Prerequisites:
#   - Android emulator running (or physical device connected via adb)
#   - Enterprise stack running (see waltid-enterprise-quickstart repo)
#   - ngrok tunnel active pointing to port 7500
#   - App installed on device (or use --build)
#   - Ngrok workarounds patch applied to waltid-identity (see patches/)
#
# Usage:
#   ./e2e-android.sh              # Run both flows
#   ./e2e-android.sh --build      # Rebuild APK + install before testing
#   ./e2e-android.sh --receive    # Only run receive flow
#   ./e2e-android.sh --present    # Only run present flow (credential must be in memory)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true

# --- Configuration (all overridable via environment) ---
IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../.." 2>/dev/null && pwd || echo "")}"
NGROK_DOMAIN="${HOST_ALIAS_DOMAIN:?HOST_ALIAS_DOMAIN must be set in e2e.env or environment}"
API_URL="http://localhost:${PORT:-7500}"
NGROK_URL="https://$NGROK_DOMAIN"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@walt.id}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123456}"
ORG="${ORGANIZATION:-waltid}"
TENANT_PATH="${ORG}.${TENANT:-waltid-tenant01}"
ISSUER_PROFILE="${ISSUER_PROFILE:-issuer2.mdl-profile}"
VERIFIER="${VERIFIER:-verifier2}"
PACKAGE="${ANDROID_PACKAGE:-id.walt.walletdemo}"
UI_POLL_TIMEOUT="${UI_POLL_TIMEOUT:-45}"
ATTESTER_PATH="${ATTESTER_PATH:-$TENANT_PATH.client-attester}"

# --- Parse args ---
DO_BUILD=false
DO_RECEIVE=true
DO_PRESENT=true

for arg in "$@"; do
  case $arg in
    --build) DO_BUILD=true ;;
    --receive) DO_PRESENT=false ;;
    --present) DO_RECEIVE=false ;;
    *) echo "Unknown arg: $arg"; exit 1 ;;
  esac
done

# --- Helpers ---
log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

get_token() {
  curl -sf "$API_URL/auth/account/emailpass" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])"
}

get_ui_status() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c "
import sys, re
xml = sys.stdin.read()
patterns = [
    'Wallet ready', 'Receiving credential', 'Received .* credential',
    'Receive failed', 'Presenting credential', 'Presentation sent',
    'Presentation finished', 'Present failed', 'Bootstrap failed',
    'Starting wallet', 'Error',
]
texts = re.findall(r'text=\"([^\"]+)\"', xml)
for t in texts:
    for p in patterns:
        if re.match(p, t, re.IGNORECASE):
            print(t)
            sys.exit(0)
print('UNKNOWN')
" 2>/dev/null || echo "DUMP_FAILED"
}

find_button() {
  local button_text="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c "
import sys, re
xml = sys.stdin.read()
text = '$button_text'
# Find all text nodes matching the button text
text_matches = list(re.finditer(r'text=\"' + re.escape(text) + r'\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml))
# Find all clickable views
clickable = list(re.finditer(r'clickable=\"true\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml))
# Also check reversed order (bounds before clickable)
clickable += list(re.finditer(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"[^>]*clickable=\"true\"', xml))
# For each text match, find the clickable parent that contains it
for tm in text_matches:
    tx1, ty1, tx2, ty2 = int(tm.group(1)), int(tm.group(2)), int(tm.group(3)), int(tm.group(4))
    for cm in clickable:
        cx1, cy1, cx2, cy2 = int(cm.group(1)), int(cm.group(2)), int(cm.group(3)), int(cm.group(4))
        if cx1 <= tx1 and cy1 <= ty1 and cx2 >= tx2 and cy2 >= ty2:
            x = (cx1 + cx2) // 2
            y = (cy1 + cy2) // 2
            print(f'{x} {y}')
            sys.exit(0)
# Fallback: use second text match if no clickable parent found
if len(text_matches) >= 2:
    m = text_matches[1]
elif text_matches:
    m = text_matches[0]
else:
    print('')
    sys.exit(0)
x = (int(m.group(1)) + int(m.group(3))) // 2
y = (int(m.group(2)) + int(m.group(4))) // 2
print(f'{x} {y}')
" 2>/dev/null || echo ""
}

wait_for_ui_result() {
  local busy_pattern="$1"
  local timeout="${2:-$UI_POLL_TIMEOUT}"
  local end=$((SECONDS + timeout))
  local status=""
  local last_status=""

  while [ $SECONDS -lt $end ]; do
    status=$(get_ui_status)
    if [ "$status" != "$last_status" ]; then
      echo "  UI status: $status"
      last_status="$status"
    fi
    # Keep polling if status matches the busy pattern, is UNKNOWN, or is Wallet ready
    if echo "$status" | grep -qi "$busy_pattern"; then
      sleep 2
      continue
    fi
    if [ "$status" = "UNKNOWN" ] || [ "$status" = "Wallet ready" ] || [ "$status" = "DUMP_FAILED" ]; then
      sleep 2
      continue
    fi
    echo "$status"
    return 0
  done
  echo "TIMEOUT"
  return 1
}

# --- Preflight ---
log "CHECK" "Verifying prerequisites..."
adb devices | grep -q "device$" || err "No Android device/emulator connected"
curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null \
  || err "Enterprise API not reachable or auth failed at $API_URL"
curl -sf -o /dev/null -w "" "$NGROK_URL" -H "ngrok-skip-browser-warning: true" 2>/dev/null \
  || err "ngrok tunnel not reachable at $NGROK_URL"
echo "  All checks passed."

# --- Build ---
if [ "$DO_BUILD" = true ]; then
  [ -n "$IDENTITY_DIR" ] || err "IDENTITY_DIR not set and could not be auto-detected. Set it to the waltid-identity repo root."
  [ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"

  log "BUILD" "Getting auth token for attestation config..."
  BUILD_TOKEN=$(get_token)

  log "BUILD" "Building Android APK..."
  "$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" \
    :waltid-applications:waltid-wallet-demo-android:assembleDebug \
    --no-configuration-cache -q \
    -Pattestation.baseUrl="http://10.0.2.2:${PORT:-7500}" \
    -Pattestation.attesterPath="$ATTESTER_PATH" \
    -Pattestation.bearerToken="$BUILD_TOKEN" \
    -Pattestation.hostHeader="${ORG}.enterprise.localhost"

  APK_PATH="$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-android/build/outputs/apk/debug/waltid-wallet-demo-android-debug.apk"
  [ -f "$APK_PATH" ] || err "APK not found at $APK_PATH"

  log "INSTALL" "Installing APK..."
  adb install -r "$APK_PATH"
fi

# --- Get auth token ---
log "AUTH" "Getting admin token..."
TOKEN=$(get_token)
echo "  Token obtained."

# --- Receive flow ---
if [ "$DO_RECEIVE" = true ]; then
  log "RECEIVE" "Creating credential offer via ngrok..."
  OFFER_RESPONSE=$(curl -sf \
    -H "ngrok-skip-browser-warning: true" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    "$NGROK_URL/v2/$TENANT_PATH.$ISSUER_PROFILE/issuer-service-api/credentials/offers" \
    -X POST -d '{"authMethod": "PRE_AUTHORIZED"}')

  OFFER_URL=$(echo "$OFFER_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['credentialOffer'])")
  [ -n "$OFFER_URL" ] || err "Failed to get credential offer URL"
  echo "  Offer: ${OFFER_URL:0:100}..."

  log "RECEIVE" "Launching app with credential offer deep link..."
  adb shell am force-stop "$PACKAGE" 2>/dev/null || true
  sleep 1
  adb shell am start -a android.intent.action.VIEW -d "'$OFFER_URL'" "$PACKAGE" 2>/dev/null
  sleep 3

  log "RECEIVE" "Tapping Receive button..."
  COORDS=$(find_button "Receive")
  [ -n "$COORDS" ] || err "Could not find Receive button in UI"
  RX=$(echo "$COORDS" | cut -d' ' -f1)
  RY=$(echo "$COORDS" | cut -d' ' -f2)
  echo "  Button at ($RX, $RY)"
  adb shell input tap "$RX" "$RY"

  log "RECEIVE" "Waiting for receive to complete (${UI_POLL_TIMEOUT}s timeout)..."
  sleep 2
  FINAL_STATUS=$(wait_for_ui_result "Receiving" "$UI_POLL_TIMEOUT") || true

  if echo "$FINAL_STATUS" | grep -qi "Received.*credential"; then
    log "RECEIVE" "Credential received successfully!"
  elif echo "$FINAL_STATUS" | grep -qi "fail\|error"; then
    log "RECEIVE" "Credential receive FAILED: $FINAL_STATUS"
    adb logcat -d -t 100 2>/dev/null | grep -iE "walt|wallet|ktor|credential|exception|Error|http" | grep -v "FeatureFlags\|artd\|NullBinder" | tail -30
    [ "$DO_PRESENT" = true ] && err "Receive failed, cannot continue to present flow"
  elif [ "$FINAL_STATUS" = "TIMEOUT" ]; then
    log "RECEIVE" "Timeout waiting for receive."
    adb logcat -d -t 100 2>/dev/null | grep -iE "walt|wallet|ktor|credential|exception|Error|http" | grep -v "FeatureFlags\|artd\|NullBinder" | tail -30
  else
    log "RECEIVE" "Unexpected status: $FINAL_STATUS"
  fi
fi

# --- Present flow ---
if [ "$DO_PRESENT" = true ]; then
  log "PRESENT" "Creating verification session..."
  TOKEN=$(get_token)

  SESSION_RESPONSE=$(curl -sf \
    -H "ngrok-skip-browser-warning: true" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    "$NGROK_URL/v1/$TENANT_PATH.$VERIFIER/verifier2-service-api/verification-session/create" \
    -X POST -d '{
      "flow_type": "cross_device",
      "core_flow": {
        "dcql_query": {
          "credentials": [{
            "id": "my_mdl",
            "format": "mso_mdoc",
            "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
            "claims": [
              { "path": ["org.iso.18013.5.1", "family_name"] },
              { "path": ["org.iso.18013.5.1", "given_name"] }
            ]
          }]
        }
      }
    }')

  SESSION_ID=$(echo "$SESSION_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])")
  REQUEST_URL=$(echo "$SESSION_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['bootstrapAuthorizationRequestUrl'])")
  [ -n "$REQUEST_URL" ] || err "Failed to get verification request URL"
  echo "  Session: $SESSION_ID"
  echo "  Request URL: ${REQUEST_URL:0:100}..."

  log "PRESENT" "Sending presentation request deep link (not restarting app — credentials are in-memory)..."
  adb shell am start -a android.intent.action.VIEW -d "'$REQUEST_URL'" "$PACKAGE" 2>/dev/null
  sleep 3

  log "PRESENT" "Tapping Present button..."
  COORDS=$(find_button "Present")
  [ -n "$COORDS" ] || err "Could not find Present button in UI"
  PX=$(echo "$COORDS" | cut -d' ' -f1)
  PY=$(echo "$COORDS" | cut -d' ' -f2)
  echo "  Button at ($PX, $PY)"
  adb shell input tap "$PX" "$PY"

  log "PRESENT" "Waiting for presentation to complete (${UI_POLL_TIMEOUT}s timeout)..."
  sleep 2
  FINAL_STATUS=$(wait_for_ui_result "Presenting" "$UI_POLL_TIMEOUT") || true

  if echo "$FINAL_STATUS" | grep -qi "Presentation sent\|Presentation finished"; then
    log "PRESENT" "App reports presentation complete: $FINAL_STATUS"
  elif echo "$FINAL_STATUS" | grep -qi "fail\|error"; then
    log "PRESENT" "App reports presentation FAILED: $FINAL_STATUS"
    adb logcat -d -t 100 2>/dev/null | grep -iE "walt|wallet|ktor|present|verif|exception|Error|http" | grep -v "FeatureFlags\|artd\|NullBinder" | tail -30
  fi

  log "PRESENT" "Checking verifier session status..."
  sleep 3
  TOKEN=$(get_token)

  SESSION_STATUS=$(curl -sf \
    -H "Host: ${ORG}.enterprise.localhost" \
    -H "Authorization: Bearer $TOKEN" \
    "$API_URL/v1/$TENANT_PATH.$VERIFIER.$SESSION_ID/verifier2-service-api/verification-session/info" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('session',{}).get('status','UNKNOWN'))")

  if [ "$SESSION_STATUS" = "SUCCESSFUL" ]; then
    log "PRESENT" "Verification session SUCCESSFUL!"
  elif [ "$SESSION_STATUS" = "PENDING" ]; then
    echo "  Session still PENDING, waiting 5 more seconds..."
    sleep 5
    TOKEN=$(get_token)
    SESSION_STATUS=$(curl -sf \
      -H "Host: ${ORG}.enterprise.localhost" \
      -H "Authorization: Bearer $TOKEN" \
      "$API_URL/v1/$TENANT_PATH.$VERIFIER.$SESSION_ID/verifier2-service-api/verification-session/info" \
      | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('session',{}).get('status','UNKNOWN'))")
    if [ "$SESSION_STATUS" = "SUCCESSFUL" ]; then
      log "PRESENT" "Verification session SUCCESSFUL!"
    else
      err "Verification session status: $SESSION_STATUS (expected SUCCESSFUL)"
    fi
  else
    err "Verification session status: $SESSION_STATUS (expected SUCCESSFUL)"
  fi
fi

echo ""
echo "=== ANDROID E2E TEST PASSED ==="
