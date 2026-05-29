#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/e2e.env" 2>/dev/null || true

IDENTITY_DIR="${IDENTITY_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
IOSAPP_DIR="$IDENTITY_DIR/waltid-applications/waltid-wallet-demo-ios/iosApp"
CREDENTIAL_ID="${EUDI_CREDENTIAL_ID:-eu.europa.ec.eudi.pid_vc_sd_jwt}"
SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"

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

log "CHECK" "Simulator: $SIMULATOR_ID"
curl -sf -o /dev/null https://issuer.eudiw.dev/credential_offer || err "issuer.eudiw.dev is not reachable"
curl -sf -o /dev/null https://backend.issuer.eudiw.dev/credential_offer || err "backend.issuer.eudiw.dev is not reachable"

log "TEST" "Running EudiPublicBackendUITests"

env \
  E2E_CREDENTIAL_ID="$CREDENTIAL_ID" \
  xcodebuild \
    -workspace "$IOSAPP_DIR/iosApp.xcworkspace" \
    -scheme iosApp \
    -destination "id=$SIMULATOR_ID" \
    test \
    -only-testing:iosAppUITests/EudiPublicBackendUITests/testReceiveAndPresentAgainstEudiPublicBackends

log "DONE" "iOS public EUDI instrumented E2E completed"
