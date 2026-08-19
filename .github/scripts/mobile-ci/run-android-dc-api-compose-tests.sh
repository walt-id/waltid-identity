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

test_results_root="$identity_dir/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results"
test_result_count=0
test_case_count=0
if [[ -d "$test_results_root" ]]; then
  test_result_count="$(find "$test_results_root" -type f -name '*.xml' -print | wc -l | tr -d ' ')"
  if (( test_result_count > 0 )); then
    test_case_count="$(find "$test_results_root" -type f -name '*.xml' -print0 |
      xargs -0 grep -h -o '<testcase[[:space:]>]' 2>/dev/null |
      wc -l | tr -d ' ' || true)"
  fi
fi
echo "DC API instrumentation result summary: xmlFiles=$test_result_count testCases=$test_case_count"
if (( test_result_count == 0 || test_case_count == 0 )); then
  echo "::error title=DC API instrumentation produced no test results::Gradle did not produce a non-empty Android instrumentation result set; classify this as device/setup infrastructure failure, not a passing/skipped test phase." >&2
  test_status=1
fi

postflight_status=0
assert_android_dc_api_device_unchanged || postflight_status=$?
log_android_dc_api_launcher_diagnostic

if (( test_status != 0 )); then
  exit "$test_status"
fi
exit "$postflight_status"
