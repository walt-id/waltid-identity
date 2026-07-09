#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOSAPP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../../../.." && pwd)}"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"
IOS_SIMULATOR_ARCHS="${IOS_SIMULATOR_ARCHS:-arm64}"
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
[ -f "$IOSAPP_DIR/iosApp.xcworkspace/contents.xcworkspacedata" ] || err "iosApp workspace not found"

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

log "CHECK" "Simulator: $SIMULATOR_ID"
curl -sf -o /dev/null https://issuer.eudiw.dev/credential_offer || err "issuer.eudiw.dev is not reachable"
curl -sf -o /dev/null https://backend.issuer.eudiw.dev/credential_offer || err "backend.issuer.eudiw.dev is not reachable"

log "TEST" "Running Compose iOS EudiPublicBackendE2ETests"
rm -rf "$RESULT_BUNDLE_PATH"
mkdir -p "$(dirname "$RESULT_BUNDLE_PATH")" "$DERIVED_DATA_PATH"

env \
  E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  xcodebuild \
    -workspace "$IOSAPP_DIR/iosApp.xcworkspace" \
    -scheme iosApp \
    -destination "id=$SIMULATOR_ID" \
    -resultBundlePath "$RESULT_BUNDLE_PATH" \
    -derivedDataPath "$DERIVED_DATA_PATH" \
    test \
    -only-testing:iosAppUITests/EudiPublicBackendE2ETests/testReceiveAndPresentAgainstEudiPublicBackends \
    OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES

log "DONE" "Compose iOS public EUDI UI E2E completed"
