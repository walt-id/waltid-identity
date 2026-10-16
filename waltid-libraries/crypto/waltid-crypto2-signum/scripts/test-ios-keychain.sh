#!/usr/bin/env bash
# Runs native Keychain lifecycle tests in an isolated simulator application.
set -euo pipefail

simulator_id="${1:?Usage: test-ios-keychain.sh SIMULATOR_UDID [OUTPUT_DIR]}"
module_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_dir="$(git -C "$module_dir" rev-parse --show-toplevel)"
mkdir -p "$module_dir/build"
output_dir="${2:-$(mktemp -d "$module_dir/build/keychain-tests.XXXXXX")}"
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd -P)"
app_dir="$output_dir/KeychainTests.app"
bundle_id="id.walt.crypto2.keychain-tests"
mkdir -p "$app_dir"

python3 - "$output_dir" "$bundle_id" <<'PY'
import pathlib, plistlib, sys
out = pathlib.Path(sys.argv[1])
bundle = sys.argv[2]
with (out / 'entitlements.plist').open('wb') as file:
    plistlib.dump({'application-identifier': bundle, 'keychain-access-groups': [bundle]}, file)
with (out / 'KeychainTests.app' / 'Info.plist').open('wb') as file:
    plistlib.dump({'CFBundleIdentifier': bundle, 'CFBundleExecutable': 'KeychainTests',
                  'CFBundleName': 'KeychainTests', 'CFBundlePackageType': 'APPL',
                  'CFBundleVersion': '1', 'CFBundleShortVersionString': '1.0',
                  'MinimumOSVersion': '16.0', 'UIDeviceFamily': [1, 2]}, file)
PY
cat > "$output_dir/app-host.init.gradle" <<'GRADLE'
gradle.projectsEvaluated {
    def module = gradle.rootProject.findProject(":waltid-libraries:crypto:waltid-crypto2-signum")
    if (module == null) return
    def target = module.extensions.getByName("kotlin").targets.getByName("iosSimulatorArm64")
    target.binaries.getTest("DEBUG").linkerOpts(
        "-sectcreate", "__TEXT", "__entitlements", System.getenv("KEYCHAIN_TEST_ENTITLEMENTS"),
        "-L" + System.getenv("KEYCHAIN_TEST_SWIFT_LIBS"))
}
GRADLE
export KEYCHAIN_TEST_ENTITLEMENTS="$output_dir/entitlements.plist"
export KEYCHAIN_TEST_SWIFT_LIBS="$(xcode-select -p)/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/iphonesimulator"
(
    cd "$repo_dir"
    ./gradlew :waltid-libraries:crypto:waltid-crypto2-signum:linkDebugTestIosSimulatorArm64 \
        -PenableIosBuild=true -PenableIosKeychainTests=true \
        -I "$output_dir/app-host.init.gradle" --console=plain
)
cp "$module_dir/build/bin/iosSimulatorArm64/debugTest/test.kexe" "$app_dir/KeychainTests"
codesign --force --sign - "$app_dir"
xcrun simctl bootstatus "$simulator_id" -b
xcrun simctl install "$simulator_id" "$app_dir"
# The app owns only temporary test keys; each test removes its own fixtures.
python3 "$module_dir/scripts/run_keychain_tests.py" "$simulator_id" "$output_dir"
python3 "$repo_dir/.github/scripts/mobile-ci/verify_test_results.py" \
    --reports "$output_dir/results.xml" --require-class id.walt.crypto2.signum.IosKeyOwnershipTest
