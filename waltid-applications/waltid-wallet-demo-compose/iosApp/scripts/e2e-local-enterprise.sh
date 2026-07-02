#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true
source "$SCRIPT_DIR/../../../mobile-e2e-fixtures/local-enterprise-fixtures.sh"

IOSAPP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
PORT="${PORT:-7500}"
API_URL="${API_URL:-http://localhost:$PORT}"
IOS_API_URL="${IOS_API_URL:-}"
HOST_ALIAS_DOMAIN="${HOST_ALIAS_DOMAIN:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@walt.id}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123456}"
ORG="${ORGANIZATION:-waltid}"
TENANT="${TENANT:-waltid-tenant01}"
TENANT_PATH="${ORG}.${TENANT}"
ISSUER_PROFILE="${ISSUER_PROFILE:-}"
VERIFIER="${VERIFIER:-verifier2-mobile}"
ATTESTER_PATH="${ATTESTER_PATH:-$TENANT_PATH.client-attester}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"
IOS_SIMULATOR_ARCHS="${IOS_SIMULATOR_ARCHS:-arm64}"
RESULT_BUNDLE_PATH="${RESULT_BUNDLE_PATH:-$IOSAPP_DIR/build/compose-local-enterprise-e2e.xcresult}"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-$IOSAPP_DIR/build/xcode-derived-compose-local-enterprise}"
SKIP_IOS_APP_SETUP="${SKIP_IOS_APP_SETUP:-false}"

ATTESTED=false
PREPARE_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --attested) ATTESTED=true ;;
    --prepare-only) PREPARE_ONLY=true ;;
    *) echo "Unknown arg: $arg" >&2; exit 1 ;;
  esac
done

if [ -z "$ISSUER_PROFILE" ]; then
  if [ "$ATTESTED" = true ]; then
    ISSUER_PROFILE="issuer2.mdl-profile"
  else
    ISSUER_PROFILE="issuer2-noattest.mdl-profile"
  fi
fi

if [ -z "$IOS_API_URL" ]; then
  IOS_API_URL="https://$HOST_ALIAS_DOMAIN"
fi

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }
get_token() { get_enterprise_admin_token "$API_URL" "$ADMIN_EMAIL" "$ADMIN_PASSWORD"; }

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e.env or env"
require_e2e_command curl
require_e2e_command python3

[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"
[ -f "$IOSAPP_DIR/iosApp.xcworkspace/contents.xcworkspacedata" ] || err "iosApp workspace not found"

log "CHECK" "Verifying local Enterprise backend prerequisites"
TOKEN="$(get_token)"
curl -sf -o /dev/null "$API_URL/health" 2>/dev/null || true
check_ngrok_domain "$HOST_ALIAS_DOMAIN"

if [ "$PREPARE_ONLY" = true ]; then
  if [ "$ATTESTED" = false ] && [ "$ISSUER_PROFILE" = "issuer2-noattest.mdl-profile" ]; then
    ensure_non_attested_issuer2 "$TOKEN" "${ORG}.enterprise.localhost"
  fi
  ensure_mobile_verifier2 "$TOKEN" "https://$HOST_ALIAS_DOMAIN" "$VERIFIER" "${ORG}.enterprise.localhost"
else
  log "CHECK" "Resource creation disabled; validating existing issuer/verifier resources only"
fi

preflight_local_enterprise_resources "$API_URL" "https://$HOST_ALIAS_DOMAIN" "$TOKEN" "$ISSUER_PROFILE" "$VERIFIER" "${ORG}.enterprise.localhost"

if [ "$PREPARE_ONLY" = true ]; then
  log "DONE" "Local Enterprise mobile resources are ready (attested=$ATTESTED)"
  exit 0
fi

require_e2e_command xcrun
require_e2e_command xcodebuild

if [ -z "$SIMULATOR_ID" ]; then
  SIMULATOR_ID="$(xcrun simctl list devices booted -j | python3 -c '
import json,sys
j=json.load(sys.stdin)
for _,arr in j.get("devices", {}).items():
  for d in arr:
    if d.get("state") == "Booted":
      print(d["udid"])
      raise SystemExit(0)
raise SystemExit(1)
' 2>/dev/null)" || err "No booted iOS simulator found"
fi

if [ "$SKIP_IOS_APP_SETUP" != "true" ]; then
  log "BUILD" "Syncing Compose iOS framework and CocoaPods"
  (
    cd "$IDENTITY_DIR"
    PLATFORM_NAME=iphonesimulator \
    SDK_NAME=iphonesimulator \
    ARCHS="$IOS_SIMULATOR_ARCHS" \
    CONFIGURATION=Debug \
      ./gradlew :waltid-applications:waltid-wallet-demo-compose:sharedUI:syncFramework \
        -Pkotlin.native.cocoapods.platform=iphonesimulator \
        -Pkotlin.native.cocoapods.archs="$IOS_SIMULATOR_ARCHS" \
        -Pkotlin.native.cocoapods.configuration=Debug
  )
  (cd "$IOSAPP_DIR" && pod install)
fi

log "CHECK" "Verifying Compose iOS prerequisites (simulator=$SIMULATOR_ID)"

PLIST_DEBUG="$IOSAPP_DIR/iosApp/Info-Debug.plist"
grep -q "NSAppTransportSecurity" "$PLIST_DEBUG" \
  || err "Missing NSAppTransportSecurity in Info-Debug.plist (needed for local-enterprise E2E over HTTP)"

log "TEST" "Running Compose iOS LocalEnterpriseBackendE2ETests (attested=$ATTESTED)"
rm -rf "$RESULT_BUNDLE_PATH"
mkdir -p "$(dirname "$RESULT_BUNDLE_PATH")" "$DERIVED_DATA_PATH"

env \
  E2E_HOST_ALIAS_DOMAIN="$HOST_ALIAS_DOMAIN" \
  E2E_API_BASE_URL="$IOS_API_URL" \
  E2E_ADMIN_EMAIL="$ADMIN_EMAIL" \
  E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  E2E_ORGANIZATION="$ORG" \
  E2E_TENANT="$TENANT" \
  E2E_ISSUER_PROFILE="$ISSUER_PROFILE" \
  E2E_VERIFIER="$VERIFIER" \
  E2E_ATTESTED="$ATTESTED" \
  E2E_LOCAL_ENTERPRISE=true \
  E2E_ATTESTATION_BASE_URL="${E2E_ATTESTATION_BASE_URL:-http://localhost:$PORT}" \
  E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  TEST_RUNNER_E2E_HOST_ALIAS_DOMAIN="$HOST_ALIAS_DOMAIN" \
  TEST_RUNNER_E2E_API_BASE_URL="$IOS_API_URL" \
  TEST_RUNNER_E2E_ADMIN_EMAIL="$ADMIN_EMAIL" \
  TEST_RUNNER_E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  TEST_RUNNER_E2E_ORGANIZATION="$ORG" \
  TEST_RUNNER_E2E_TENANT="$TENANT" \
  TEST_RUNNER_E2E_ISSUER_PROFILE="$ISSUER_PROFILE" \
  TEST_RUNNER_E2E_VERIFIER="$VERIFIER" \
  TEST_RUNNER_E2E_ATTESTED="$ATTESTED" \
  TEST_RUNNER_E2E_LOCAL_ENTERPRISE=true \
  TEST_RUNNER_E2E_ATTESTATION_BASE_URL="${E2E_ATTESTATION_BASE_URL:-http://localhost:$PORT}" \
  TEST_RUNNER_E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  ATTESTATION_BASE_URL="${E2E_ATTESTATION_BASE_URL:-http://localhost:$PORT}" \
  ATTESTATION_ATTESTER_PATH="$ATTESTER_PATH" \
  xcodebuild \
    -workspace "$IOSAPP_DIR/iosApp.xcworkspace" \
    -scheme iosApp \
    -destination "id=$SIMULATOR_ID" \
    -resultBundlePath "$RESULT_BUNDLE_PATH" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    test \
    -only-testing:iosAppUITests/LocalEnterpriseBackendE2ETests \
    OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES

log "DONE" "Compose iOS local UI E2E completed (attested=$ATTESTED)"
