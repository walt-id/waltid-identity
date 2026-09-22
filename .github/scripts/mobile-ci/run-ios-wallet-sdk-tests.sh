#!/usr/bin/env bash
# Run the actual Swift package target with the WalletCore framework from this checkout.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
destination="${1:?An explicit iOS Simulator destination is required}"
case "$destination" in
  'platform=iOS Simulator,'*) ;;
  *) echo 'WalletSDK CI tests require an iOS Simulator destination' >&2; exit 1 ;;
esac

root="$identity_dir/build/proximity-tests"
mkdir -p "$root"
run_dir="$(mktemp -d "$root/swift-bridge.XXXXXX")"
echo "Swift bridge evidence: $run_dir"

# CI supplies an already restored release artifact. Verify it instead of linking it again.
# Local runs without an artifact retain the full release-build fallback.
release_artifact="${2:-}"
verify_release() {
  (cd "$identity_dir" && python3 "$script_dir/wallet-core-artifact.py" verify --artifact-dir "$release_artifact")
}
framework_tasks=(assembleWalletCoreBridgeFixturesReleaseXCFramework)
if [[ -n "$release_artifact" ]]; then
  verify_release
else
  framework_tasks=(assembleWalletCoreReleaseXCFramework "${framework_tasks[@]}")
fi

phase_start=$SECONDS
# Separate compiler lifetimes and one worker avoid overlapping native-link heaps.
for framework_task in "${framework_tasks[@]}"; do
  "$identity_dir/gradlew" -p "$identity_dir" \
    ":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:$framework_task" \
    -PenableIosBuild=true -PenableWalletSdkBridgeFixtures=true --no-daemon --console=plain --max-workers=1 \
    2>&1 | tee "$run_dir/$framework_task.log"
done
if [[ -n "$release_artifact" ]]; then
  verify_release
fi
echo "WalletCore assembly finished in $((SECONDS - phase_start))s"

# A workspace containing the local package exposes WalletSDKTests as its own target. Merely
# testing iosAppTests does not discover this target or its conditional WalletCore projections.
mkdir -p "$root/WalletSDK.xcworkspace/xcshareddata/xcschemes"
cat > "$root/WalletSDK.xcworkspace/contents.xcworkspacedata" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Workspace version="1.0">
  <FileRef location="group:../../waltid-libraries/protocols/waltid-wallet-sdk-ios"/>
</Workspace>
XML
cat > "$root/WalletSDK.xcworkspace/xcshareddata/xcschemes/WalletSDKTests.xcscheme" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Scheme version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
    <BuildActionEntries>
      <BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="NO">
        <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="WalletSDKTests" BuildableName="WalletSDKTests" BlueprintName="WalletSDKTests" ReferencedContainer="container:../../waltid-libraries/protocols/waltid-wallet-sdk-ios"/>
      </BuildActionEntry>
    </BuildActionEntries>
  </BuildAction>
  <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES">
    <Testables>
      <TestableReference skipped="NO">
        <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="WalletSDKTests" BuildableName="WalletSDKTests" BlueprintName="WalletSDKTests" ReferencedContainer="container:../../waltid-libraries/protocols/waltid-wallet-sdk-ios"/>
      </TestableReference>
    </Testables>
  </TestAction>
</Scheme>
XML

python3 - "$identity_dir" "$run_dir" "$destination" <<'PY'
import hashlib, json, pathlib, subprocess, sys
repo, output = map(pathlib.Path, sys.argv[1:3])
framework_root = repo / 'waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/XCFrameworks/release'
framework = framework_root.parents[1] / 'bridge-fixtures/XCFrameworks/release/WalletCore.xcframework'
release = framework_root / 'WalletCore.xcframework'
def sha(path):
    with path.open('rb') as file:
        return hashlib.file_digest(file, 'sha256').hexdigest()
binaries = sorted(framework.glob('*/WalletCore.framework/WalletCore'))
if not binaries:
    raise SystemExit('Assembled WalletCore framework is missing')
fixture_headers = list(framework.glob('*/WalletCore.framework/Headers/WalletCore.h'))
release_headers = list(release.glob('*/WalletCore.framework/Headers/WalletCore.h'))
if not fixture_headers or not all('ProximityBridgeTestSession' in path.read_text() for path in fixture_headers):
    raise SystemExit('Native bridge fixtures are missing from the test framework')
if not release_headers or any('ProximityBridgeTestSession' in path.read_text() for path in release_headers):
    raise SystemExit('Publishable framework is missing or contains test fixtures')
metadata = {
    'sourceRevision': subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip(),
    'trackedDiffSha256': hashlib.sha256(subprocess.check_output(['git', '-C', str(repo), 'diff', '--binary', 'HEAD'])).hexdigest(),
    'destination': sys.argv[3],
    'frameworkVariant': 'isolated native bridge fixtures with unchanged SDK sources',
    'frameworkBinaries': {str(path.relative_to(repo)): sha(path) for path in binaries},
}
(output / 'inputs.json').write_text(json.dumps(metadata, indent=2) + '\n')
PY

phase_start=$SECONDS
test_status=0
WALLET_SDK_BRIDGE_FIXTURES=1 xcodebuild test \
  -workspace "$root/WalletSDK.xcworkspace" -scheme WalletSDKTests \
  -destination "$destination" \
  -only-testing:WalletSDKTests/KMPProximityProjectionTests \
  -only-testing:WalletSDKTests/ProximityBridgeContractTests \
  -only-testing:WalletSDKTests/ProximityInputValidationTests \
  -resultBundlePath "$run_dir/Tests.xcresult" \
  -derivedDataPath "$root/bridge-fixture-derived" \
  -parallel-testing-enabled NO \
  -test-timeouts-enabled YES \
  -default-test-execution-time-allowance 60 \
  -maximum-test-execution-time-allowance 120 \
  2>&1 | tee "$run_dir/xcode-test.log" || test_status=$?
echo "WalletSDK build and test finished in $((SECONDS - phase_start))s"

if [[ ! -d "$run_dir/Tests.xcresult" ]]; then
  echo 'Missing WalletSDK result bundle' >&2
  exit 1
fi
if ! command -v xcresultparser >/dev/null 2>&1; then
  # See install-and-run-kdoctor: the runner image's untrusted aws/tap makes every
  # `brew install` emit a tap-trust warning. Drop it first.
  brew untap aws/tap 2>/dev/null || true
  brew install xcresultparser
fi
xcresultparser "$run_dir/Tests.xcresult" --output-format=junit > "$run_dir/results.xml"
python3 "$script_dir/verify_test_results.py" \
  --manifest "$script_dir/proximity-test-suites.json" --suite swift-bridge \
  --reports "$run_dir/results.xml" | tee "$run_dir/discovery.json" || test_status=1
exit "$test_status"
