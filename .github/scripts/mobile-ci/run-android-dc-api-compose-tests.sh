#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
source "$script_dir/prepare-android-dc-api-device.sh"

# The preparation job caches an unmodified factory-image AVD. Test jobs validate
# that exact baseline and fail before Gradle if the restored device has drifted.
validate_android_dc_api_device

# These suites need Google Play services and run separately from ordinary Compose tests.
test_classes="id.walt.walletdemo.compose.android.DigitalCredentialSharingE2ETest,id.walt.walletdemo.compose.android.DigitalCredentialIssuanceE2ETest"
IFS=',' read -r -a required_classes <<< "$test_classes"
instrumentation_args=()
if [[ -n "${ANDROID_TEST_NOT_CLASS:-}" ]]; then
  instrumentation_args+=("-Pandroid.testInstrumentationRunnerArguments.notClass=$ANDROID_TEST_NOT_CLASS")
fi

test_status=0
set +e
"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedProductionDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$test_classes" \
  "${instrumentation_args[@]}" \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=id.walt.mobile.test.PhysicalDeviceTest \
  -PtransactionDataProfiles.url=https://wallet.demo.walt.id/wallet-api/transaction-data-profiles \
  --info
test_status=$?
set -e

python3 "$script_dir/verify_test_results.py" \
  --require-class "${required_classes[@]}" \
  --reports "$identity_dir/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/androidTest-results/**/*.xml" \
  || test_status=1

postflight_status=0
assert_android_dc_api_device_unchanged || postflight_status=$?
log_android_dc_api_launcher_diagnostic

if (( test_status != 0 )); then
  exit "$test_status"
fi
exit "$postflight_status"
