#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
source "$script_dir/prepare-android-dc-api-device.sh"

# The AVD preparation job owns the bounded GMS update wait. Test jobs validate the
# restored baseline and fail before Gradle if the cached device is stale or unhealthy.
validate_android_dc_api_device

instrumentation_args=()
if [[ -n "${ANDROID_TEST_CLASS:-}" ]]; then
  instrumentation_args+=("-Pandroid.testInstrumentationRunnerArguments.class=$ANDROID_TEST_CLASS")
fi

test_status=0
set +e
"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedDebugAndroidTest \
  "${instrumentation_args[@]}" \
  -PtransactionDataProfiles.url=https://wallet.demo.walt.id/wallet-api/transaction-data-profiles \
  --info
test_status=$?
set -e

postflight_status=0
assert_android_dc_api_device_unchanged || postflight_status=$?
log_android_dc_api_launcher_diagnostic

if (( test_status != 0 )); then
  exit "$test_status"
fi
exit "$postflight_status"
