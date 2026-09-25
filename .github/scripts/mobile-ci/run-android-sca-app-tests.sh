#!/usr/bin/env bash
# Real application flow with test-build-only simulated authentication. Never native SCA evidence.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
# Do not leave a second wallet provider in Credential Manager after this isolated lane.
cleanup() {
  adb uninstall id.walt.wallet.compose.sca.e2e.test >/dev/null 2>&1 || true
  adb uninstall id.walt.wallet.compose.sca.e2e >/dev/null 2>&1 || true
}
trap cleanup EXIT
args=(--no-configuration-cache -I "$script_dir/sca-app-e2e.init.gradle"
  -PenableAndroidBuild=true -PenableIosBuild=false -PwalletSigningProtectionMode=disabled)
"$identity_dir/gradlew" -p "$identity_dir" "${args[@]}" \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:installPreviewDebug
# This dedicated test package is never the user's or physical-test wallet.
adb shell pm clear id.walt.wallet.compose.sca.e2e
"$identity_dir/gradlew" -p "$identity_dir" "${args[@]}" \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedPreviewDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=id.walt.walletdemo.compose.android.ScaPaymentAppE2ETest
python3 "$script_dir/check-sca-app-results.py" \
  "$identity_dir/waltid-applications/waltid-wallet-demo-compose/androidApp/build/sca-app-e2e/outputs/androidTest-results" 4
