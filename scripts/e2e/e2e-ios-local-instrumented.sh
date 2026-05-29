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
ISSUER_PROFILE="${ISSUER_PROFILE:-}"
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

if [ -z "$ISSUER_PROFILE" ]; then
  if [ "$ATTESTED" = true ]; then
    ISSUER_PROFILE="issuer2.mdl-profile"
  else
    ISSUER_PROFILE="issuer2-noattest.mdl-profile"
  fi
fi

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

if [ "$ATTESTED" = false ] && [ "$ISSUER_PROFILE" = "issuer2-noattest.mdl-profile" ]; then
  log "SETUP" "Ensuring non-attested issuer exists (issuer2-noattest)"
  TOKEN="$(curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")"
  TENANT_PATH="${ORG}.${TENANT}"
  HOST_HDR="${ORG}.enterprise.localhost"
  # Create issuer2-noattest service (no clientAuthenticationConfig)
  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.issuer2-noattest/resource-api/services/create" \
    -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
    -d "{\"type\":\"issuer2\",\"_id\":\"$TENANT_PATH.issuer2-noattest\",\"tokenKeyId\":\"$TENANT_PATH.kms.issuer-signing-key\",\"kms\":\"$TENANT_PATH.kms\",\"credentialConfigurations\":{\"org.iso.18013.5.1.mDL\":{\"format\":\"mso_mdoc\",\"doctype\":\"org.iso.18013.5.1.mDL\",\"scope\":\"org.iso.18013.5.1.mDL\",\"credential_signing_alg_values_supported\":[-7,-9],\"cryptographic_binding_methods_supported\":[\"cose_key\"],\"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[\"ES256\"]}}}}}" \
    > /dev/null 2>&1 || true
  # Create mdl-profile under issuer2-noattest (reuses existing issuer signing key + certs)
  PROFILE_PAYLOAD="{\"name\":\"mdl-profile\",\"credentialConfigurationId\":\"org.iso.18013.5.1.mDL\",\"issuerKeyId\":\"$TENANT_PATH.kms.issuer-signing-key\",\"credentialData\":{\"org.iso.18013.5.1\":{\"family_name\":\"Doe\",\"given_name\":\"John\",\"birth_date\":\"1990-01-01\",\"issue_date\":\"2024-01-01\",\"expiry_date\":\"2029-01-01\",\"issuing_country\":\"US\",\"issuing_authority\":\"Test DMV\",\"document_number\":\"DL123456789\",\"un_distinguishing_sign\":\"USA\"}}}"
  curl -sf "http://localhost:$PORT/v2/$TENANT_PATH.issuer2-noattest.mdl-profile/issuer-service-api/credentials/profiles" \
    -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
    -d "$PROFILE_PAYLOAD" > /dev/null 2>&1 || true
  log "SETUP" "issuer2-noattest ready"
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
    -only-testing:iosAppUITests/LocalEnterpriseBackendUITests

log "DONE" "iOS local instrumented E2E completed (attested=$ATTESTED)"
