#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
cd "$identity_dir"

# CI pins the device and runtime. Local runs let simctl select a compatible runtime.
device_type="${IOS_RECOVERY_DEVICE_TYPE:-com.apple.CoreSimulator.SimDeviceType.iPhone-17}"
runtime_description="newest compatible runtime"
simulator_command=(xcrun simctl create "Wallet recovery" "$device_type")
if [[ -n "${IOS_SIMULATOR_DESTINATION+x}" ]]; then
  IFS=, read -r platform device os extra <<< "$IOS_SIMULATOR_DESTINATION"
  if [[ "$platform" != "platform=iOS Simulator" || "$device" != name=?* || "$os" != OS=* ||
        ! "${os#OS=}" =~ ^[0-9]+\.[0-9]+(\.[0-9]+)?$ || -n "$extra" ||
        "$IOS_SIMULATOR_DESTINATION" != "$platform,$device,$os" ]]; then
    echo "::error::IOS_SIMULATOR_DESTINATION must specify a device-type name and explicit iOS version: platform=iOS Simulator,name=<device>,OS=<version>" >&2
    exit 1
  fi
  device_type="${IOS_RECOVERY_DEVICE_TYPE:-${device#name=}}"
  runtime_description="iOS ${os#OS=}"
  simulator_command=(xcrun simctl create "Wallet recovery" "$device_type" "$runtime_description")
fi

python3 -m unittest discover -s scripts/tests -p 'test_*recovery*.py'
mkdir -p build/reports/ios-wallet-recovery
output=$(mktemp -d "$identity_dir/build/reports/ios-wallet-recovery/run.XXXXXX")
python3 scripts/build-recovery-simulator-host.py --output "$output/host"

# Create and remove only this run's simulator.
printf 'Recovery simulator device: %s; runtime: %s\n' "$device_type" "$runtime_description"
simulator=$("${simulator_command[@]}")
trap 'xcrun simctl delete "$simulator"' EXIT
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b

./waltid-libraries/crypto/waltid-crypto2-signum/scripts/test-ios-keychain.sh "$simulator" "$output/signum-keychain"

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
