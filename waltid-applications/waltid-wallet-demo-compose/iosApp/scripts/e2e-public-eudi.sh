#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOSAPP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"
RESULT_BUNDLE_PATH="${RESULT_BUNDLE_PATH:-$IOSAPP_DIR/build/compose-eudi-e2e.xcresult}"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-$IOSAPP_DIR/build/xcode-derived-compose-eudi}"
SKIP_IOS_APP_SETUP="${SKIP_IOS_APP_SETUP:-false}"

log() { echo -e "\n\033[1;36m[$1]\033[0m $2"; }
err() { echo -e "\033[1;31m[ERROR]\033[0m $1" >&2; exit 1; }

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

[ -f "$IDENTITY_DIR/gradlew" ] || err "gradlew not found at $IDENTITY_DIR"
[ -d "$IOSAPP_DIR/iosApp.xcodeproj" ] || err "iosApp project not found"

if [ "$SKIP_IOS_APP_SETUP" != "true" ]; then
  log "BUILD" "Resolving Compose iOS SwiftPM linkage package"
  (
    cd "$IDENTITY_DIR"
    XCODEPROJ_PATH="$IOSAPP_DIR/iosApp.xcodeproj" \
      ./gradlew :waltid-applications:waltid-wallet-demo-compose:sharedUI:integrateLinkagePackage \
        -PenableIosBuild=true
  )
fi

log "CHECK" "Simulator: $SIMULATOR_ID"
curl -sf -o /dev/null https://issuer.eudiw.dev/credential_offer || err "issuer.eudiw.dev is not reachable"
curl -sf -o /dev/null https://backend.issuer.eudiw.dev/credential_offer || err "backend.issuer.eudiw.dev is not reachable"

log "TEST" "Running Compose iOS EudiPublicBackendE2ETests"
rm -rf "$RESULT_BUNDLE_PATH"
mkdir -p "$(dirname "$RESULT_BUNDLE_PATH")" "$DERIVED_DATA_PATH"

env \
  E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  xcodebuild \
    -project "$IOSAPP_DIR/iosApp.xcodeproj" \
    -scheme iosApp \
    -destination "id=$SIMULATOR_ID" \
    -resultBundlePath "$RESULT_BUNDLE_PATH" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    test \
    -only-testing:iosAppUITests/EudiPublicBackendE2ETests/testReceiveAndPresentAgainstEudiPublicBackends

log "DONE" "Compose iOS public EUDI UI E2E completed"
