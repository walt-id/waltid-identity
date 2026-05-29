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
ISSUER_PROFILE="${ISSUER_PROFILE:-issuer2.mdl-profile}"
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

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

get_token() {
  curl -sf "$API_URL/auth/account/emailpass" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])"
}

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e/e2e.env or env"
[ -n "$ANDROID_API_URL" ] || ANDROID_API_URL="https://$HOST_ALIAS_DOMAIN"
if [ -z "$ANDROID_API_HOST_HEADER" ] && echo "$ANDROID_API_URL" | grep -Eq '10\.0\.2\.2|localhost'; then
  ANDROID_API_HOST_HEADER="${ORG}.enterprise.localhost"
fi
[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"
[ -f "$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-android/src/main/AndroidManifest.xml" ] || err "AndroidManifest.xml not found"

log "CHECK" "Verifying Android + backend prerequisites"
adb devices | grep -q "device$" || err "No Android emulator/device connected"
curl -sf "$API_URL/auth/account/emailpass" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" > /dev/null \
  || err "Enterprise auth failed at $API_URL"
curl -sf -o /dev/null "$API_URL/health" 2>/dev/null || true
curl -sf -o /dev/null -H "ngrok-skip-browser-warning: true" "https://$HOST_ALIAS_DOMAIN" \
  || err "ngrok domain not reachable: https://$HOST_ALIAS_DOMAIN"

# Local enterprise currently returns http:// metadata URLs, so cleartext workaround is required.
grep -q 'android:usesCleartextTraffic=\"true\"' \
  "$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-android/src/main/AndroidManifest.xml" \
  || err "Missing local cleartext workaround. Apply: git apply scripts/e2e/patches/local-cleartext-workarounds.patch"

# Verifier response_uri resolves to localhost:7500 for the emulator path.
adb reverse --list | grep -q 'tcp:7500 tcp:7500' \
  || err "Missing adb reverse for verifier callback. Run: adb reverse tcp:7500 tcp:7500"

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
  GRADLE_ARGS+=(
    -Pattestation.baseUrl="http://10.0.2.2:$PORT"
    -Pattestation.attesterPath="$ATTESTER_PATH"
    -Pattestation.bearerToken="$TOKEN"
    -Pattestation.hostHeader="${ORG}.enterprise.localhost"
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
