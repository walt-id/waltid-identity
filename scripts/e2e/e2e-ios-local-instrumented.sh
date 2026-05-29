#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true

IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
PORT="${PORT:-7500}"
API_URL="${API_URL:-http://localhost:$PORT}"
HOST_ALIAS_DOMAIN="${HOST_ALIAS_DOMAIN:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@walt.id}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123456}"
ORG="${ORGANIZATION:-waltid}"
TENANT="${TENANT:-waltid-tenant01}"
ISSUER_PROFILE="${ISSUER_PROFILE:-issuer2.mdl-profile}"
VERIFIER="${VERIFIER:-verifier2}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
IOSAPP_DIR="$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-ios/iosApp"

ATTESTED=false
for arg in "$@"; do
  case "$arg" in
    --attested) ATTESTED=true ;;
    *) echo "Unknown arg: $arg" >&2; exit 1 ;;
  esac
done

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e/e2e.env or env"
[ -f "$IOSAPP_DIR/iosApp.xcworkspace/contents.xcworkspacedata" ] || err "iosApp workspace not found"

SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"
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

log "CHECK" "Simulator: $SIMULATOR_ID"

curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null \
  || err "Enterprise API auth failed at $API_URL"

curl -sf -o /dev/null -H "ngrok-skip-browser-warning: true" "https://$HOST_ALIAS_DOMAIN" \
  || err "ngrok domain not reachable: https://$HOST_ALIAS_DOMAIN"

PLIST="$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-ios/iosApp/iosApp/Info.plist"
grep -q "NSAppTransportSecurity" "$PLIST" \
  || err "Missing iOS cleartext workaround. Apply: git apply scripts/e2e/patches/local-cleartext-workarounds.patch"

if [ "$ATTESTED" = false ] && [ "$ISSUER_PROFILE" = "issuer2.mdl-profile" ]; then
  log "NOTE" "issuer2.mdl-profile usually enforces attestation; non-attested run may only validate expected attestation rejection"
fi

log "TEST" "Running LocalEnterpriseBackendUITests (attested=$ATTESTED)"

env \
  TEST_RUNNER_HOST_ALIAS_DOMAIN="$HOST_ALIAS_DOMAIN" \
  TEST_RUNNER_E2E_API_BASE_URL="https://$HOST_ALIAS_DOMAIN" \
  TEST_RUNNER_E2E_ADMIN_EMAIL="$ADMIN_EMAIL" \
  TEST_RUNNER_E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  TEST_RUNNER_E2E_ORGANIZATION="$ORG" \
  TEST_RUNNER_E2E_TENANT="$TENANT" \
  TEST_RUNNER_E2E_ISSUER_PROFILE="$ISSUER_PROFILE" \
  TEST_RUNNER_E2E_VERIFIER="$VERIFIER" \
  TEST_RUNNER_E2E_ATTESTED="$ATTESTED" \
  TEST_RUNNER_E2E_ATTESTATION_BASE_URL="http://localhost:$PORT" \
  TEST_RUNNER_E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  xcodebuild \
    -workspace "$IOSAPP_DIR/iosApp.xcworkspace" \
    -scheme iosApp \
    -destination "id=$SIMULATOR_ID" \
    test \
    -only-testing:iosAppUITests/LocalEnterpriseBackendUITests/testReceiveAndPresentAgainstLocalEnterpriseBackend

log "DONE" "iOS local instrumented E2E completed (attested=$ATTESTED)"
