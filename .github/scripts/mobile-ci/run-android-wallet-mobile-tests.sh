#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"

# The disposable CI emulator needs a secure lock screen for credential-protected key reopening.
if [[ "${CI:-}" == true ]]; then
  serial="$(adb get-serialno)"
  case "$serial" in
    emulator-*) adb -s "$serial" shell locksettings set-pin 1234 ;;
    *) echo 'CI PIN setup requires a disposable emulator' >&2; exit 1 ;;
  esac
fi

# Preserve Enterprise isolation and any caller exclusion when adding the physical-suite guard.
"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest \
  :waltid-libraries:crypto:waltid-crypto2-signum:connectedAndroidDeviceTest \
  "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=id.walt.wallet2.mobile.test.EnterpriseMobileTest,id.walt.mobile.test.PhysicalDeviceTest${ANDROID_TEST_NOT_ANNOTATION:+,$ANDROID_TEST_NOT_ANNOTATION}" \
  -Pandroid.testInstrumentationRunnerArguments.notClass=id.walt.wallet2.mobile.test.BlockStoreRecoveryWorkflowTest \
  --info

python3 "$script_dir/verify_test_results.py" \
  --manifest "$script_dir/proximity-test-suites.json" --suite android-credential-key \
  --reports "$identity_dir/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**/*.xml"
