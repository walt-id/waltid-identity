#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true

IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
PORT="${PORT:-7500}"
API_URL="${API_URL:-http://localhost:$PORT}"
ANDROID_API_URL="${ANDROID_API_URL:-}"
ANDROID_API_HOST_HEADER="${ANDROID_API_HOST_HEADER:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@walt.id}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123456}"
ORG="${ORGANIZATION:-waltid}"
TENANT="${TENANT:-waltid-tenant01}"
TENANT_PATH="${ORG}.${TENANT}"
ISSUER_PROFILE="${ISSUER_PROFILE:-}"
VERIFIER="${VERIFIER:-verifier2}"
ATTESTER_PATH="${ATTESTER_PATH:-$TENANT_PATH.client-attester}"
HOST_ALIAS_DOMAIN="${HOST_ALIAS_DOMAIN:-}"
TEST_CLASS="id.walt.walletdemo.LocalEnterpriseBackendInstrumentedTest"

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

get_token() {
  curl -sf "$API_URL/auth/account/emailpass" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])"
}

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e/e2e.env or env"

# Detect emulator vs physical device
if adb devices -l | grep -q "emulator-"; then
  IS_EMULATOR=true
else
  IS_EMULATOR=false
fi

if [ -z "$ANDROID_API_URL" ]; then
  if [ "$IS_EMULATOR" = true ]; then
    ANDROID_API_URL="http://10.0.2.2:$PORT"
  else
    ANDROID_API_URL="https://$HOST_ALIAS_DOMAIN"
  fi
fi
if [ -z "$ANDROID_API_HOST_HEADER" ] && echo "$ANDROID_API_URL" | grep -Eq '10\.0\.2\.2|localhost'; then
  ANDROID_API_HOST_HEADER="${ORG}.enterprise.localhost"
fi
[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"
[ -f "$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-android/src/main/AndroidManifest.xml" ] || err "AndroidManifest.xml not found"

log "CHECK" "Verifying Android + backend prerequisites (emulator=$IS_EMULATOR)"
adb devices | grep -q "device$" || err "No Android emulator/device connected"
curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null \
  || err "Enterprise auth failed at $API_URL"
curl -sf -o /dev/null "$API_URL/health" 2>/dev/null || true
curl -sf -o /dev/null -H "ngrok-skip-browser-warning: true" "https://$HOST_ALIAS_DOMAIN" \
  || err "ngrok domain not reachable: https://$HOST_ALIAS_DOMAIN"

if [ "$IS_EMULATOR" = true ]; then
  # Local enterprise currently returns http:// metadata URLs, so cleartext workaround is required.
  grep -q 'android:usesCleartextTraffic=\"true\"' \
    "$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-android/src/main/AndroidManifest.xml" \
    || err "Missing local cleartext workaround. Apply: git apply scripts/e2e/patches/local-cleartext-workarounds.patch"
fi

# Verifier response_uri resolves to localhost:7500 for the emulator path.
adb reverse --list | grep -q 'tcp:7500 tcp:7500' \
  || err "Missing adb reverse for verifier callback. Run: adb reverse tcp:7500 tcp:7500"

if [ "$ATTESTED" = false ] && [ "$ISSUER_PROFILE" = "issuer2-noattest.mdl-profile" ]; then
  log "SETUP" "Ensuring non-attested issuer exists (issuer2-noattest)"
  TOKEN="$(get_token)"
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

GRADLE_ARGS=(
  :waltid-applications:waltid-wallet-demo-android:connectedDebugAndroidTest
  --no-configuration-cache
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS"
  -Pandroid.testInstrumentationRunnerArguments.e2e_host_alias_domain="$HOST_ALIAS_DOMAIN"
  -Pandroid.testInstrumentationRunnerArguments.e2e_api_base_url="$ANDROID_API_URL"
  -Pandroid.testInstrumentationRunnerArguments.e2e_api_host_header="$ANDROID_API_HOST_HEADER"
  -Pandroid.testInstrumentationRunnerArguments.e2e_admin_email="$ADMIN_EMAIL"
  -Pandroid.testInstrumentationRunnerArguments.e2e_admin_password="$ADMIN_PASSWORD"
  -Pandroid.testInstrumentationRunnerArguments.e2e_org="$ORG"
  -Pandroid.testInstrumentationRunnerArguments.e2e_tenant="$TENANT"
  -Pandroid.testInstrumentationRunnerArguments.e2e_issuer_profile="$ISSUER_PROFILE"
  -Pandroid.testInstrumentationRunnerArguments.e2e_verifier="$VERIFIER"
)

if [ "$ATTESTED" = true ]; then
  log "BUILD" "Attested mode enabled: injecting client-attester build config"
  TOKEN="$(get_token)"
  if [ "$IS_EMULATOR" = true ]; then
    ATTESTATION_BASE_URL="http://10.0.2.2:$PORT"
    ATTESTATION_HOST_HEADER="${ORG}.enterprise.localhost"
  else
    ATTESTATION_BASE_URL="https://$HOST_ALIAS_DOMAIN"
    ATTESTATION_HOST_HEADER=""
  fi
  GRADLE_ARGS+=(
    -Pattestation.baseUrl="$ATTESTATION_BASE_URL"
    -Pattestation.attesterPath="$ATTESTER_PATH"
    -Pattestation.bearerToken="$TOKEN"
    -Pattestation.hostHeader="$ATTESTATION_HOST_HEADER"
  )
else
  log "BUILD" "Non-attested mode: forcing empty attestation config"
  GRADLE_ARGS+=(
    -Pattestation.baseUrl=
    -Pattestation.attesterPath=
    -Pattestation.bearerToken=
    -Pattestation.hostHeader=
  )
fi

log "TEST" "Running $TEST_CLASS"
"$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" "${GRADLE_ARGS[@]}"

log "DONE" "Local enterprise instrumented E2E completed (attested=$ATTESTED)"
