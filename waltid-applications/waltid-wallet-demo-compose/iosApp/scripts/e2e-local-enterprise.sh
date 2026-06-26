#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true

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
VERIFIER="${VERIFIER:-verifier2}"
ATTESTER_PATH="${ATTESTER_PATH:-$TENANT_PATH.client-attester}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"
IOS_SIMULATOR_ARCHS="${IOS_SIMULATOR_ARCHS:-arm64}"
RESULT_BUNDLE_PATH="${RESULT_BUNDLE_PATH:-$IOSAPP_DIR/build/compose-local-enterprise-e2e.xcresult}"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-$IOSAPP_DIR/build/xcode-derived-compose-local-enterprise}"
SKIP_IOS_APP_SETUP="${SKIP_IOS_APP_SETUP:-false}"

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

if [ -z "$IOS_API_URL" ]; then
  IOS_API_URL="https://$HOST_ALIAS_DOMAIN"
fi

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

get_token() {
  curl -sf "$API_URL/auth/account/emailpass" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])"
}

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e.env or env"
[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"
[ -f "$IOSAPP_DIR/iosApp.xcworkspace/contents.xcworkspacedata" ] || err "iosApp workspace not found"

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

log "CHECK" "Verifying Compose iOS + backend prerequisites (simulator=$SIMULATOR_ID)"
curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null \
  || err "Enterprise API auth failed at $API_URL"
curl -sf -o /dev/null "$API_URL/health" 2>/dev/null || true
curl -sf -o /dev/null -H "ngrok-skip-browser-warning: true" "https://$HOST_ALIAS_DOMAIN" \
  || err "ngrok domain not reachable: https://$HOST_ALIAS_DOMAIN"

PLIST_DEBUG="$IOSAPP_DIR/iosApp/Info-Debug.plist"
grep -q "NSAppTransportSecurity" "$PLIST_DEBUG" \
  || err "Missing NSAppTransportSecurity in Info-Debug.plist (needed for local-enterprise E2E over HTTP)"

if [ "$ATTESTED" = false ] && [ "$ISSUER_PROFILE" = "issuer2-noattest.mdl-profile" ]; then
  log "SETUP" "Ensuring non-attested issuer exists (issuer2-noattest)"
  TOKEN="$(get_token)"
  HOST_HDR="${ORG}.enterprise.localhost"
  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.issuer2-noattest/resource-api/services/create" \
    -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
    -d "{\"type\":\"issuer2\",\"_id\":\"$TENANT_PATH.issuer2-noattest\",\"tokenKeyId\":\"$TENANT_PATH.kms.issuer-signing-key\",\"kms\":\"$TENANT_PATH.kms\",\"credentialConfigurations\":{\"org.iso.18013.5.1.mDL\":{\"format\":\"mso_mdoc\",\"doctype\":\"org.iso.18013.5.1.mDL\",\"scope\":\"org.iso.18013.5.1.mDL\",\"credential_signing_alg_values_supported\":[-7,-9],\"cryptographic_binding_methods_supported\":[\"cose_key\"],\"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[\"ES256\"]}}}}}" \
    > /dev/null 2>&1 || true
  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-doc-signer-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $TOKEN" -H "Host: $HOST_HDR" > /tmp/doc_cert.json
  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-iaca-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $TOKEN" -H "Host: $HOST_HDR" > /tmp/iaca_cert.json
  python3 - <<PY
import json
with open('/tmp/doc_cert.json') as f:
    doc_pem = json.load(f)['data']['pem']
with open('/tmp/iaca_cert.json') as f:
    iaca_pem = json.load(f)['data']['pem']
payload = {
    'name': 'mdl-profile',
    'credentialConfigurationId': 'org.iso.18013.5.1.mDL',
    'issuerKeyId': '$TENANT_PATH.kms.issuer-signing-key',
    'x5Chain': [
        {'type': 'pem-encoded-x509-certificate-descriptor', 'pemEncodedCertificate': doc_pem},
        {'type': 'pem-encoded-x509-certificate-descriptor', 'pemEncodedCertificate': iaca_pem},
    ],
    'credentialData': {
        'org.iso.18013.5.1': {
            'family_name': 'Doe',
            'given_name': 'John',
            'birth_date': '1990-01-01',
            'issue_date': '2024-01-01',
            'expiry_date': '2029-01-01',
            'issuing_country': 'US',
            'issuing_authority': 'Test DMV',
            'document_number': 'DL123456789',
            'un_distinguishing_sign': 'USA',
        }
    }
}
with open('/tmp/mdl-profile-payload.json', 'w') as f:
    json.dump(payload, f)
PY
  curl -sf "http://localhost:$PORT/v2/$TENANT_PATH.issuer2-noattest.mdl-profile/issuer-service-api/credentials/profiles" \
    -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Host: $HOST_HDR" \
    -d @/tmp/mdl-profile-payload.json > /dev/null 2>&1 || true
  log "SETUP" "issuer2-noattest ready"
fi

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
  E2E_ATTESTATION_BASE_URL="${E2E_ATTESTATION_BASE_URL:-http://localhost:$PORT}" \
  E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
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
