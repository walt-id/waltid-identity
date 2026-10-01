#!/usr/bin/env bash
set -euo pipefail

# Instrumentation shares the app process. Separate invocations are required to kill
# process-global DID state while retaining the wallet database and Android KeyStore.
serial="${1:?Pass the Android device or emulator serial}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
class='id.walt.walletdemo.compose.android.PublicDemoBackendE2ETest#receiveAndPresentAfterColdProcessRestart'
"$identity_dir/gradlew" -p "$identity_dir" -PenableAndroidBuild=true \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleProductionDebug \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleProductionDebugAndroidTest \
  --console=plain
apk_dir="$identity_dir/waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/apk"
adb -s "$serial" install -r "$apk_dir/production/debug/androidApp-production-debug.apk"
adb -s "$serial" install -r "$apk_dir/androidTest/production/debug/androidApp-production-debug-androidTest.apk"
results="$identity_dir/build/cold-restart"
mkdir -p "$results"
for phase in seed present; do
  adb -s "$serial" shell am force-stop id.walt.walletdemo.compose
  adb -s "$serial" shell am instrument -w -r -e class "$class" \
    -e coldRestartPhase "$phase" \
    id.walt.walletdemo.compose.test/androidx.test.runner.AndroidJUnitRunner | tee "$results/$phase.txt"
  # am instrument can exit zero on failure or skip; JUnit's OK also includes skips.
  grep -q '^OK (1 test)' "$results/$phase.txt"
  ! grep -qE '^INSTRUMENTATION_STATUS_CODE: -[1-4]$' "$results/$phase.txt"
done
