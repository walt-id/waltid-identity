#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true
source "$SCRIPT_DIR/../../../mobile-e2e-fixtures/local-enterprise-fixtures.sh"

IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
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
VERIFIER="${VERIFIER:-verifier2-mobile}"
ATTESTER_PATH="${ATTESTER_PATH:-$TENANT_PATH.client-attester}"
HOST_ALIAS_DOMAIN="${HOST_ALIAS_DOMAIN:-}"
TEST_CLASS="id.walt.walletdemo.compose.android.LocalEnterpriseBackendE2ETest"

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
get_token() { get_enterprise_admin_token "$API_URL" "$ADMIN_EMAIL" "$ADMIN_PASSWORD"; }

[ -n "$HOST_ALIAS_DOMAIN" ] || err "HOST_ALIAS_DOMAIN must be set in scripts/e2e.env or env"
require_e2e_command curl
require_e2e_command python3

[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"
[ -f "$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-compose/androidApp/src/main/AndroidManifest.xml" ] || err "AndroidManifest.xml not found"

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

require_e2e_command adb

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

log "CHECK" "Verifying Android prerequisites (emulator=$IS_EMULATOR)"
adb devices | grep -q "device$" || err "No Android emulator/device connected"

if [ "$IS_EMULATOR" = true ]; then
  grep -q 'cleartextTrafficPermitted=\"true\"' \
    "$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-compose/androidApp/src/debug/res/xml/network_security_config.xml" \
    || err "Missing cleartext permission in debug network_security_config.xml (needed for emulator local-enterprise E2E)"
fi

GRADLE_ARGS=(
  :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedDebugAndroidTest
  --no-configuration-cache
  -PenableAndroidBuild=true
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
  -Pandroid.testInstrumentationRunnerArguments.e2e_local_enterprise=true
)

if [ "$ATTESTED" = true ]; then
  log "BUILD" "Attested mode enabled: injecting client-attester build config"
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
# Android 16+ (targetSdk 37) requires ACCESS_LOCAL_NETWORK for emulator 10.0.2.2 connections.
# Pre-install the APK so we can grant the runtime permission before connectedAndroidTest runs.
"$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" :waltid-applications:waltid-wallet-demo-compose:androidApp:installDebug --no-configuration-cache \
  -PenableAndroidBuild=true \
  -Pattestation.baseUrl="${ATTESTATION_BASE_URL:-}" \
  -Pattestation.attesterPath="${ATTESTER_PATH:-}" \
  -Pattestation.bearerToken="${TOKEN:-}" \
  -Pattestation.hostHeader="${ATTESTATION_HOST_HEADER:-}"
adb shell pm grant id.walt.walletdemo.compose android.permission.ACCESS_LOCAL_NETWORK 2>/dev/null || true
"$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" "${GRADLE_ARGS[@]}"

log "DONE" "Local enterprise instrumented E2E completed (attested=$ATTESTED)"
