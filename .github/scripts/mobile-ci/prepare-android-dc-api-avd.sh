#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$script_dir/prepare-android-dc-api-device.sh"

DC_API_CACHE_QUIESCENCE_TIMEOUT_SECONDS="${DC_API_CACHE_QUIESCENCE_TIMEOUT_SECONDS:-60}"

run_bounded_adb() {
  local timeout_seconds="$1"
  shift
  local command=("$ADB_BIN")
  if [[ -n "$ANDROID_SERIAL" ]]; then
    command+=(-s "$ANDROID_SERIAL")
  fi
  timeout --signal=TERM --kill-after=5s "${timeout_seconds}s" "${command[@]}" "$@"
}

echo "DC API AVD preparation: validating the factory image before configured-AVD caching"
prepare_android_dc_api_device
write_android_dc_api_baseline_manifest
assert_android_dc_api_baseline_manifest
echo "DC API AVD preparation: draining Android broadcast and application work before caching"
if ! barrier_output="$(
  run_bounded_adb "$DC_API_CACHE_QUIESCENCE_TIMEOUT_SECONDS" \
    shell am wait-for-broadcast-barrier \
    --flush-broadcast-loopers \
    --flush-application-threads
)"; then
  echo "::error::Android broadcast/application quiescence command failed" >&2
  exit 1
fi
printf '%s\n' "$barrier_output"
if [[ "$barrier_output" != *"Finished application barriers!"* ]]; then
  echo "::error::Android application work did not reach the quiescence barrier" >&2
  exit 1
fi
echo "DC API AVD preparation: flushing the guest filesystem"
run_bounded_adb "$DC_API_CACHE_QUIESCENCE_TIMEOUT_SECONDS" shell sync
echo "DC API AVD preparation: immutable manifest and guest durability gates passed"
