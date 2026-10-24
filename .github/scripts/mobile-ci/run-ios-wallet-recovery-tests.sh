#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
cd "$identity_dir"

IFS=, read -r platform device os extra <<< "${IOS_SIMULATOR_DESTINATION:?Set the iOS simulator destination}"
if [[ "$platform" != 'platform=iOS Simulator' || "$device" != name=?* || "$os" != OS=[0-9]* || -n "$extra" ]]; then
  echo "::error::Recovery tests require a device-type name and explicit iOS version"
  exit 1
fi

python3 -m unittest discover -s scripts/tests -p 'test_*recovery*.py'
mkdir -p build/reports/ios-wallet-recovery
output=$(mktemp -d "$identity_dir/build/reports/ios-wallet-recovery/run.XXXXXX")
python3 scripts/build-recovery-simulator-host.py --output "$output/host"

# Create and remove only this run's simulator, using the workflow's pinned destination.
printf 'Recovery simulator: %s, iOS %s\n' "${device#name=}" "${os#OS=}"
simulator=$(xcrun simctl create "Wallet recovery" "${device#name=}" "iOS ${os#OS=}")
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
