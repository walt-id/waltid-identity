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

# Recovery lifecycle runs separately; Signum authorization needs an operator.
excluded_classes=(
  id.walt.wallet2.mobile.test.BlockStoreRecoveryWorkflowTest
  id.walt.crypto2.signum.AndroidInteractiveAuthorizationTest
)
# Preserve Enterprise isolation and any caller annotation exclusion.
"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest \
  :waltid-libraries:crypto:waltid-crypto2-signum:connectedAndroidDeviceTest \
  "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=id.walt.mobile.test.EnterpriseMobileTest,id.walt.mobile.test.PhysicalDeviceTest${ANDROID_TEST_NOT_ANNOTATION:+,$ANDROID_TEST_NOT_ANNOTATION}" \
  "-Pandroid.testInstrumentationRunnerArguments.notClass=$(IFS=,; echo "${excluded_classes[*]}")" \
  --info

# Missing secure-lock-screen setup produces a skip and fails this check.
python3 "$script_dir/verify_test_results.py" \
  --require-class id.walt.crypto2.signum.AndroidSignumKeyBackendDeviceTest \
  --reports "$identity_dir/waltid-libraries/crypto/waltid-crypto2-signum/build/outputs/androidTest-results/**/*.xml"
python3 "$script_dir/verify_test_results.py" \
  --reports "$identity_dir/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/androidTest-results/**/*.xml"
