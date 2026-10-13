#!/usr/bin/env bash
# Run an app scheme on a fresh simulator; native and Compose apps share callback schemes.
set -euo pipefail

destination="${1:?An explicit simulator destination is required}"
shift
IFS=, read -r platform device os extra <<< "$destination"
if [[ "$platform" != 'platform=iOS Simulator' || "$device" != name=?* || "$os" != OS=[0-9]* || -n "$extra" ]]; then
  echo 'App tests require a device-type name and explicit iOS version' >&2
  exit 1
fi
simulator=$(xcrun simctl create 'Wallet CI' "${device#name=}" "iOS ${os#OS=}")
cleanup() {
  test_status=$?
  xcrun simctl delete "$simulator" || {
    cleanup_status=$?
    echo 'Failed to remove test simulator' >&2
    (( test_status != 0 )) || test_status=$cleanup_status
  }
  exit "$test_status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
xcrun simctl bootstatus "$simulator" -b

xcodebuild test -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination "platform=iOS Simulator,id=$simulator" \
  -enableCodeCoverage NO -parallel-testing-enabled NO "$@"
