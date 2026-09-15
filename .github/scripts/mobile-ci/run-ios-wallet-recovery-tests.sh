#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
cd "$identity_dir"

python3 -m unittest discover -s scripts/tests -p test_qualify_wallet_recovery.py
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
xcrun simctl launch --console --terminate-running-process "$simulator" id.walt.wallet.recovery-tests \
  --ktest_filter=id.walt.wallet2.recovery.keychain.KeychainIdentityRecoveryTest.compatibleAccessibilityClassesPreserveTheRecordContract \
  --require-keychain > "$output/keychain-contract.log" 2>&1
python3 - "$output" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

output = Path(sys.argv[1])
log = (output / 'keychain-contract.log').read_text()
passed = '[  PASSED  ] 1 tests.' in log and '[  FAILED  ]' not in log
suite = ET.Element('testsuite', name='recovery.keychain', tests='1', failures='0' if passed else '1')
case = ET.SubElement(suite, 'testcase', classname='recovery.keychain', name='record-contract')
if not passed:
    ET.SubElement(case, 'failure', message='Expected one passing Keychain contract; see keychain-contract.log')
ET.ElementTree(suite).write(output / 'results.xml', encoding='utf-8', xml_declaration=True)
if not passed:
    raise SystemExit('Keychain contract failed or produced incomplete results')
PY

for pair in EncryptedDatabase:EncryptedDatabase NativeStorage:NativeStorage EncryptedDatabase:NativeStorage; do
  storage=${pair%:*}
  destination=${pair#*:}
  python3 scripts/qualify-wallet-recovery.py local-loss \
    --platform ios-simulator --device "$simulator" --app "$app" \
    --storage "$storage" --restore-storage "$destination" --output "$output/$storage-to-$destination"
done
