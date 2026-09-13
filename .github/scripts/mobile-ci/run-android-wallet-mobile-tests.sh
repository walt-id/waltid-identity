#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"

# Preserve Enterprise isolation and any caller exclusion when adding the physical-suite guard.
"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest \
  :waltid-libraries:crypto:waltid-crypto2-signum:connectedAndroidDeviceTest \
  "-Pandroid.testInstrumentationRunnerArguments.notAnnotation=id.walt.wallet2.mobile.test.EnterpriseMobileTest,id.walt.proximity.test.PhysicalDeviceTest${ANDROID_TEST_NOT_ANNOTATION:+,$ANDROID_TEST_NOT_ANNOTATION}" \
  --info
