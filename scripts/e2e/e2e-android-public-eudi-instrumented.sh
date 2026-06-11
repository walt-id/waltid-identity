#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
TEST_CLASS="id.walt.walletdemo.EudiPublicBackendReceiveInstrumentedTest"

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

adb devices | grep -q "device$" || err "No Android emulator/device connected"
[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"

log "TEST" "Running Android instrumented E2E test"
"$IDENTITY_DIR/gradlew" -p "$IDENTITY_DIR" \
  :waltid-applications:waltid-wallet-demo-android:connectedDebugAndroidTest \
  --no-configuration-cache \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_CLASS" \
  -Pandroid.testInstrumentationRunnerArguments.e2e_credential_id="$CREDENTIAL_ID"

log "DONE" "Android public EUDI instrumented E2E completed"
