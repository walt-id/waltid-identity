#!/usr/bin/env bash
# Simulator app E2Es. Authentication is compiled as a test double, never enabled at runtime.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
destination="${1:?An explicit simulator destination is required}"
shift
[[ "$destination" == 'platform=iOS Simulator,'* ]] || { echo 'Simulator required' >&2; exit 1; }
[[ $# -gt 0 ]] || { echo 'Select native and/or compose' >&2; exit 1; }
mobile="$identity_dir/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile"
"$identity_dir/gradlew" -p "$identity_dir" --no-configuration-cache \
  -I "$script_dir/sca-app-e2e.init.gradle" -PenableIosBuild=true -PenableAndroidBuild=false \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:linkReleaseFrameworkIosSimulatorArm64
framework="$mobile/build/sca-app-e2e/XCFrameworks/release/WalletCore.xcframework"
rm -rf "$framework"
xcodebuild -create-xcframework \
  -framework "$mobile/build/sca-app-e2e/bin/iosSimulatorArm64/releaseFramework/WalletCore.framework" \
  -output "$framework"
export WALLET_SCA_APP_E2E=1
for app in "$@"; do
  case "$app" in native) folder=waltid-wallet-demo-ios; kotlin_override=YES;;
    compose) folder=waltid-wallet-demo-compose; kotlin_override=NO;;
    *) echo "Unknown app: $app" >&2; exit 1;; esac
  output="$identity_dir/build/sca-app-e2e/ios-$app"
  mkdir -p "$output"
  rm -rf "$output/results.xcresult"
  # Xcode, rather than the shell, expands its inherited build-setting marker.
  # shellcheck disable=SC2016
  xcodebuild test -project "$identity_dir/waltid-applications/$folder/iosApp/iosApp.xcodeproj" \
    -scheme iosApp -configuration Debug -destination "$destination" \
    -derivedDataPath "$output/derived-data" -resultBundlePath "$output/results.xcresult" \
    -parallel-testing-enabled NO -enableCodeCoverage NO \
    -only-testing:iosAppUITests/PublicDemoBackendE2ETests/testScaAppApproval \
    -only-testing:iosAppUITests/PublicDemoBackendE2ETests/testScaAppCancellation \
    -only-testing:iosAppUITests/PublicDemoBackendE2ETests/testScaAppDeniedAuthentication \
    'SWIFT_ACTIVE_COMPILATION_CONDITIONS=$(inherited) DEBUG SCA_APP_E2E' \
    "OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=$kotlin_override" WALLET_SCA_APP_E2E=1
  xcrun xcresulttool get test-results summary --path "$output/results.xcresult" > "$output/summary.json"
  python3 - "$output/summary.json" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
assert r['passedTests'] == 3 and r['failedTests'] == 0 and r['skippedTests'] == 0, r
print('Three unattended SCA app cases passed; authentication was simulated')
PY
done
