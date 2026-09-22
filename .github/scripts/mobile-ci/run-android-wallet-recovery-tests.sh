#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
cd "$identity_dir"

: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
# CI has one emulator. Local callers can explicitly select a device with ANDROID_SERIAL.
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  devices=$("$ANDROID_HOME/platform-tools/adb" devices | awk '$2 == "device" { print $1 }')
  if [[ -z "$devices" || "$devices" == *$'\n'* || "$devices" != emulator-* ]]; then
    echo "Set ANDROID_SERIAL explicitly unless exactly one emulator is connected" >&2
    exit 1
  fi
  export ANDROID_SERIAL="$devices"
fi

python3 -m unittest discover -s scripts/tests -p test_qualify_wallet_recovery.py
./gradlew \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-recovery-blockstore:connectedAndroidDeviceTest \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleAndroidDeviceTest \
  -PenableAndroidBuild=true -PenableIosBuild=false \
  -Pandroid.testInstrumentationRunnerArguments.class=id.walt.wallet2.recovery.blockstore.BlockStoreIdentityRecoveryDeviceTest \
  --console=plain

# A missing provider, skipped suite or incomplete discovery must never make this lane green.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path('waltid-libraries/protocols/waltid-openid4vc-wallet-recovery-blockstore/build/outputs/androidTest-results')
cases = [case for path in root.rglob('*.xml') for case in ET.parse(path).iter('testcase')]
if len(cases) != 3 or any(case.find(tag) is not None for case in cases for tag in ('skipped', 'failure', 'error')):
    raise SystemExit(f'Expected three passing Block Store contracts; found {len(cases)} test cases')
PY

mkdir -p build/reports/wallet-recovery
output=$(mktemp -d "$identity_dir/build/reports/wallet-recovery/run.XXXXXX")
for pair in EncryptedDatabase:EncryptedDatabase NativeStorage:NativeStorage EncryptedDatabase:NativeStorage; do
  storage=${pair%:*}
  destination=${pair#*:}
  python3 scripts/qualify-wallet-recovery.py local-loss \
    --device "$ANDROID_SERIAL" \
    --apk waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/outputs/apk/androidTest/waltid-openid4vc-wallet-mobile-androidTest.apk \
    --storage "$storage" --restore-storage "$destination" --output "$output/$storage-to-$destination"
done
