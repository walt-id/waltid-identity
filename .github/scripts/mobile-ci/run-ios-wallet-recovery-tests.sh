#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
cd "$identity_dir"

python3 -m unittest discover -s scripts/tests -p 'test_*recovery*.py'
mkdir -p build/reports/ios-wallet-recovery
output=$(mktemp -d "$identity_dir/build/reports/ios-wallet-recovery/run.XXXXXX")
python3 scripts/build-recovery-simulator-host.py --output "$output/host"

# Create and remove only this run's simulator. The default device type matches the iOS CI toolchain.
runtime=$(xcrun simctl list runtimes --json | python3 -c '
import json, sys
runtimes = [r for r in json.load(sys.stdin)["runtimes"] if r["isAvailable"] and r["name"].startswith("iOS ")]
print(max(runtimes, key=lambda r: tuple(map(int, r["version"].split("."))))["identifier"])
')
simulator=$(xcrun simctl create "Wallet recovery" \
  "${IOS_RECOVERY_DEVICE_TYPE:-com.apple.CoreSimulator.SimDeviceType.iPhone-17}" "$runtime")
trap 'xcrun simctl delete "$simulator"' EXIT
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b

app="$output/host/RecoveryTests.app"
xcrun simctl install "$simulator" "$app"
python3 scripts/check-ios-recovery-runner.py --device "$simulator" --output "$output/runner-controls"
python3 scripts/ios_simulator_test.py --device "$simulator" --output "$output" -- \
  --ktest_filter=id.walt.wallet2.recovery.keychain.KeychainIdentityRecoveryTest.compatibleAccessibilityClassesPreserveTheRecordContract \
  --require-keychain

# Both actual adapters use the same entitled host's Keychain: Kotlin write -> Swift read/write -> Kotlin read.
namespace="interop-$(uuidgen | tr '[:upper:]' '[:lower:]')"
python3 scripts/ios_simulator_test.py --device "$simulator" --output "$output/interop-write" -- \
  --ktest_filter=id.walt.wallet2.mobile.test.KeychainRecoveryWorkflowTest.exchangesRecordsWithTheSwiftAdapter \
  --interopNamespace="$namespace" --interopPhase=write --swiftRecoveryExchange="$namespace"
python3 scripts/ios_simulator_test.py --device "$simulator" --output "$output/interop-read" -- \
  --ktest_filter=id.walt.wallet2.mobile.test.KeychainRecoveryWorkflowTest.exchangesRecordsWithTheSwiftAdapter \
  --interopNamespace="$namespace" --interopPhase=read

for pair in EncryptedDatabase:EncryptedDatabase NativeStorage:NativeStorage EncryptedDatabase:NativeStorage; do
  storage=${pair%:*}
  destination=${pair#*:}
  python3 scripts/qualify-wallet-recovery.py local-loss \
    --platform ios-simulator --device "$simulator" --app "$app" \
    --storage "$storage" --restore-storage "$destination" --output "$output/$storage-to-$destination"
done
