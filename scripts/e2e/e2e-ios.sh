#!/usr/bin/env bash
#
# E2E test for iOS wallet demo: receive + present credential flows.
#
# Prerequisites:
#   - iOS Simulator booted
#   - Enterprise stack running (see waltid-enterprise-quickstart repo)
#   - ngrok tunnel active pointing to port 7500
#   - App installed on simulator (or use --build)
#
# Usage:
#   ./e2e-ios.sh              # Run both flows
#   ./e2e-ios.sh --build      # Rebuild framework + app, install before testing
#   ./e2e-ios.sh --receive    # Only run receive flow
#   ./e2e-ios.sh --present    # Only run present flow (credential must be in memory)
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
BUNDLE_ID="${IOS_BUNDLE_ID:-waltid.iosApp}"
ATTESTER_PATH="${ATTESTER_PATH:-$TENANT_PATH.client-attester}"

# Auto-detect booted simulator if not specified
if [ -z "${IOS_SIMULATOR_ID:-}" ]; then
  SIMULATOR_ID=$(xcrun simctl list devices booted -j | python3 -c "
import sys, json
data = json.load(sys.stdin)
for runtime, devices in data.get('devices', {}).items():
    for d in devices:
        if d.get('state') == 'Booted':
            print(d['udid'])
            sys.exit(0)
sys.exit(1)
" 2>/dev/null) || { echo "ERROR: No booted iOS simulator found. Boot one with: xcrun simctl boot <device-id>"; exit 1; }
else
  SIMULATOR_ID="$IOS_SIMULATOR_ID"
fi

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
log() { echo "  [$1] $2"; }
err() { echo "  [FAIL] $1" >&2; exit 1; }

get_token() {
  curl -sf "$API_URL/auth/account/emailpass" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])"
}

get_simulator_data_dir() {
  xcrun simctl getenv "$SIMULATOR_ID" SIMULATOR_SHARED_RESOURCES_DIRECTORY 2>/dev/null \
    || echo "$HOME/Library/Developer/CoreSimulator/Devices/$SIMULATOR_ID/data"
}

# --- Preflight ---
log "CHECK" "Simulator: $SIMULATOR_ID"
xcrun simctl list devices booted 2>/dev/null | grep -q "$SIMULATOR_ID" || err "Simulator $SIMULATOR_ID is not booted"
curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null \
  || err "Enterprise API not reachable or auth failed at $API_URL"
curl -sf -o /dev/null "$NGROK_URL" -H "ngrok-skip-browser-warning: true" 2>/dev/null \
  || err "ngrok tunnel not reachable at $NGROK_URL"
log "CHECK" "All checks passed."

# --- Build (optional) ---
if [ "$DO_BUILD" = true ]; then
  [ -n "$IDENTITY_DIR" ] || err "IDENTITY_DIR not set and could not be auto-detected. Set it to the waltid-identity repo root."
  [ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"

  log "BUILD" "Building iOS shared framework..."
  "$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" \
    :waltid-applications:waltid-wallet-demo-ios:shared:linkPodDebugFrameworkIosSimulatorArm64 \
    --no-configuration-cache -PenableIosBuild=true -q
  "$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" \
    :waltid-applications:waltid-wallet-demo-ios:shared:syncFramework \
    -Pkotlin.native.cocoapods.platform=iphonesimulator \
    -Pkotlin.native.cocoapods.archs=arm64 \
    -Pkotlin.native.cocoapods.configuration=Debug \
    --no-configuration-cache -PenableIosBuild=true -q

  log "BUILD" "Building iOS app..."
  IOSAPP_DIR="$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-ios/iosApp"
  OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES xcodebuild \
    -workspace "$IOSAPP_DIR/iosApp.xcworkspace" \
    -scheme iosApp \
    -sdk iphonesimulator \
    -destination "id=$SIMULATOR_ID" \
    -configuration Debug \
    build -quiet

  # Find the built app in DerivedData
  APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -path "*/Build/Products/Debug-iphonesimulator/iosApp.app" -newer "$IOSAPP_DIR/iosApp.xcworkspace" 2>/dev/null | head -1)
  [ -n "$APP_PATH" ] || err "Could not find built iosApp.app in DerivedData"

  log "BUILD" "Installing app from: $APP_PATH"
  xcrun simctl install "$SIMULATOR_ID" "$APP_PATH"
fi

# --- Ensure URL schemes are pre-approved (no confirmation dialog) ---
SIM_DATA_DIR=$(get_simulator_data_dir)
PLIST="$SIM_DATA_DIR/Library/Preferences/com.apple.launchservices.schemeapproval.plist"
for scheme in openid-credential-offer openid4vp; do
  if ! plutil -p "$PLIST" 2>/dev/null | grep -q "$scheme"; then
    /usr/libexec/PlistBuddy -c "Add :com.apple.CoreSimulator.CoreSimulatorBridge-->$scheme string $BUNDLE_ID" "$PLIST" 2>/dev/null || true
  fi
done

# --- Inject attestation config via UserDefaults ---
log "ATTEST" "Getting auth token for attestation config..."
ATTEST_TOKEN=$(get_token)
log "ATTEST" "Injecting attestation UserDefaults..."
xcrun simctl spawn "$SIMULATOR_ID" defaults write "$BUNDLE_ID" ATTESTATION_BASE_URL "http://localhost:${PORT:-7500}"
xcrun simctl spawn "$SIMULATOR_ID" defaults write "$BUNDLE_ID" ATTESTATION_ATTESTER_PATH "$ATTESTER_PATH"
xcrun simctl spawn "$SIMULATOR_ID" defaults write "$BUNDLE_ID" ATTESTATION_BEARER_TOKEN "$ATTEST_TOKEN"
xcrun simctl spawn "$SIMULATOR_ID" defaults write "$BUNDLE_ID" ATTESTATION_HOST_HEADER "${ORG}.enterprise.localhost"

# --- Launch app ---
log "APP" "Launching $BUNDLE_ID..."
xcrun simctl terminate "$SIMULATOR_ID" "$BUNDLE_ID" 2>/dev/null || true
sleep 2
xcrun simctl launch "$SIMULATOR_ID" "$BUNDLE_ID"
log "APP" "Waiting for bootstrap..."
sleep 8

# Verify app is running
APP_ALIVE=false
for i in 1 2 3; do
  LAUNCHCTL_OUT=$(xcrun simctl spawn "$SIMULATOR_ID" launchctl list 2>&1 || true)
  if echo "$LAUNCHCTL_OUT" | grep -q "$BUNDLE_ID"; then
    APP_ALIVE=true
    break
  fi
  sleep 2
done
[ "$APP_ALIVE" = true ] || err "App crashed during bootstrap"
log "APP" "App bootstrapped"

# --- Receive ---
if [ "$DO_RECEIVE" = true ]; then
  log "RECEIVE" "Creating credential offer..."
  TOKEN=$(get_token)
  OFFER=$(curl -sf -H "ngrok-skip-browser-warning: true" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    "$NGROK_URL/v2/$TENANT_PATH.$ISSUER_PROFILE/issuer-service-api/credentials/offers" \
    -X POST -d '{"authMethod": "PRE_AUTHORIZED"}' \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['credentialOffer'])")
  [ -n "$OFFER" ] || err "Failed to get credential offer URL"

  log "RECEIVE" "Sending deep link..."
  xcrun simctl openurl "$SIMULATOR_ID" "$OFFER"

  log "RECEIVE" "Waiting for credential receive..."
  sleep 10

  LAUNCHCTL_OUT=$(xcrun simctl spawn "$SIMULATOR_ID" launchctl list 2>&1 || true)
  if ! echo "$LAUNCHCTL_OUT" | grep -q "$BUNDLE_ID"; then
    err "App crashed during receive"
  fi

  xcrun simctl io "$SIMULATOR_ID" screenshot /tmp/ios-e2e-receive-result.png 2>/dev/null
  log "RECEIVE" "Credential received (screenshot: /tmp/ios-e2e-receive-result.png)"
fi

# --- Present ---
if [ "$DO_PRESENT" = true ]; then
  log "PRESENT" "Creating verification session..."
  TOKEN=$(get_token)
  RESP=$(curl -sf -H "Host: ${ORG}.enterprise.localhost" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    "$API_URL/v1/$TENANT_PATH.$VERIFIER/verifier2-service-api/verification-session/create" \
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
        },
        "policies": { "vc_policies": [{"policy": "signature"}] }
      }
    }')

  SESSION_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['sessionId'])")
  REQUEST_URL=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['bootstrapAuthorizationRequestUrl'])")
  [ -n "$REQUEST_URL" ] || err "Failed to get verification request URL"

  log "PRESENT" "Session: $SESSION_ID"
  log "PRESENT" "Sending presentation deep link (not restarting app — credentials are in-memory)..."
  xcrun simctl openurl "$SIMULATOR_ID" "$REQUEST_URL"

  log "PRESENT" "Waiting for presentation..."
  sleep 12

  LAUNCHCTL_OUT=$(xcrun simctl spawn "$SIMULATOR_ID" launchctl list 2>&1 || true)
  if ! echo "$LAUNCHCTL_OUT" | grep -q "$BUNDLE_ID"; then
    err "App crashed during presentation"
  fi

  # Check verifier session status
  TOKEN=$(get_token)
  SESSION_STATUS=$(curl -sf -H "Host: ${ORG}.enterprise.localhost" \
    -H "Authorization: Bearer $TOKEN" \
    "$API_URL/v1/$TENANT_PATH.$VERIFIER.$SESSION_ID/verifier2-service-api/verification-session/info" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['session']['status'])")

  if [ "$SESSION_STATUS" = "SUCCESSFUL" ]; then
    log "PRESENT" "Verifier confirmed: $SESSION_STATUS"
  elif [ "$SESSION_STATUS" = "PENDING" ]; then
    echo "  Session still PENDING, waiting 5 more seconds..."
    sleep 5
    TOKEN=$(get_token)
    SESSION_STATUS=$(curl -sf -H "Host: ${ORG}.enterprise.localhost" \
      -H "Authorization: Bearer $TOKEN" \
      "$API_URL/v1/$TENANT_PATH.$VERIFIER.$SESSION_ID/verifier2-service-api/verification-session/info" \
      | python3 -c "import sys,json; print(json.load(sys.stdin)['session']['status'])")
    [ "$SESSION_STATUS" = "SUCCESSFUL" ] || err "Verifier status: $SESSION_STATUS (expected SUCCESSFUL)"
    log "PRESENT" "Verifier confirmed: $SESSION_STATUS"
  else
    err "Verifier status: $SESSION_STATUS (expected SUCCESSFUL)"
  fi

  xcrun simctl io "$SIMULATOR_ID" screenshot /tmp/ios-e2e-present-result.png 2>/dev/null
  log "PRESENT" "Presentation verified (screenshot: /tmp/ios-e2e-present-result.png)"
fi

echo ""
echo "=== iOS E2E TEST PASSED ==="
