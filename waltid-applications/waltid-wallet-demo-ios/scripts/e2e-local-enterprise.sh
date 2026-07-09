#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true
source "$SCRIPT_DIR/../../mobile-e2e-fixtures/local-enterprise-fixtures.sh"

IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../../.." && pwd)}"
IOSAPP_DIR="$(cd "$SCRIPT_DIR/../iosApp" && pwd)"
PORT="${PORT:-7500}"
API_URL="${API_URL:-http://localhost:$PORT}"
HOST_ALIAS_DOMAIN="${HOST_ALIAS_DOMAIN:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@walt.id}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123456}"
ORG="${ORGANIZATION:-waltid}"
TENANT="${TENANT:-waltid-tenant01}"
TENANT_PATH="${ORG}.${TENANT}"
ISSUER_PROFILE="${ISSUER_PROFILE:-}"
VERIFIER="${VERIFIER:-verifier2-mobile}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"

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

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e.env or env"
require_e2e_command curl
require_e2e_command python3

TOKEN="$(get_enterprise_admin_token "$API_URL" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"

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
[ -d "$IOSAPP_DIR/iosApp.xcodeproj" ] || err "iosApp project not found"

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

PLIST_DEBUG="$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-ios/iosApp/iosApp/Info-Debug.plist"
grep -q "NSAppTransportSecurity" "$PLIST_DEBUG" \
  || err "Missing NSAppTransportSecurity in Info-Debug.plist (needed for local-enterprise E2E over HTTP)"

log "TEST" "Running LocalEnterpriseBackendUITests (attested=$ATTESTED)"

env \
  E2E_HOST_ALIAS_DOMAIN="$HOST_ALIAS_DOMAIN" \
  E2E_API_BASE_URL="https://$HOST_ALIAS_DOMAIN" \
  E2E_ADMIN_EMAIL="$ADMIN_EMAIL" \
  E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  E2E_ORGANIZATION="$ORG" \
  E2E_TENANT="$TENANT" \
  E2E_ISSUER_PROFILE="$ISSUER_PROFILE" \
  E2E_VERIFIER="$VERIFIER" \
  E2E_ATTESTED="$ATTESTED" \
  E2E_LOCAL_ENTERPRISE=true \
  E2E_ATTESTATION_BASE_URL="http://localhost:$PORT" \
  E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  TEST_RUNNER_E2E_HOST_ALIAS_DOMAIN="$HOST_ALIAS_DOMAIN" \
  TEST_RUNNER_E2E_API_BASE_URL="https://$HOST_ALIAS_DOMAIN" \
  TEST_RUNNER_E2E_ADMIN_EMAIL="$ADMIN_EMAIL" \
  TEST_RUNNER_E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  TEST_RUNNER_E2E_ORGANIZATION="$ORG" \
  TEST_RUNNER_E2E_TENANT="$TENANT" \
  TEST_RUNNER_E2E_ISSUER_PROFILE="$ISSUER_PROFILE" \
  TEST_RUNNER_E2E_VERIFIER="$VERIFIER" \
  TEST_RUNNER_E2E_ATTESTED="$ATTESTED" \
  TEST_RUNNER_E2E_LOCAL_ENTERPRISE=true \
  TEST_RUNNER_E2E_ATTESTATION_BASE_URL="http://localhost:$PORT" \
  TEST_RUNNER_E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  xcodebuild \
    -project "$IOSAPP_DIR/iosApp.xcodeproj" \
    -scheme iosApp \
    -destination "id=$SIMULATOR_ID" \
    test \
    -only-testing:iosAppUITests/LocalEnterpriseBackendE2ETests

log "DONE" "iOS local UI E2E completed (attested=$ATTESTED)"
