#!/usr/bin/env bash
# Run the tenant coordinator with the isolated Compose Preview test APKs built from this checkout.
set -euo pipefail

mode="${1:?Select unattended or attended}"
serial="${2:?Select the Android device serial}"
case "$mode" in
  unattended) [[ "$serial" =~ ^emulator-[0-9]+$ ]] || { echo 'Unattended ITB requires an emulator.' >&2; exit 2; } ;;
  attended) [[ "${CI:-false}" != true && ! "$serial" =~ ^emulator- ]] || { echo 'Attended ITB requires a physical device and operator.' >&2; exit 2; } ;;
  *) echo 'Select unattended or attended.' >&2; exit 2 ;;
esac
export ANDROID_SERIAL="$serial"
export ITB_ANDROID_DEMO_MODE="$mode"
if [[ -z "${ITB_CASES:-}" ]]; then unset ITB_CASES; fi

app=id.walt.wallet.compose.test
outputs=waltid-applications/waltid-wallet-demo-compose/androidApp/build/outputs/apk
apk="$outputs/preview/debug/androidApp-preview-debug.apk"
test_apk="$outputs/androidTest/preview/debug/androidApp-preview-debug-androidTest.apk"
[[ -r "$apk" && -r "$test_apk" ]] || { echo 'Build the Preview Debug app and test APKs first.' >&2; exit 2; }
ITB_WALLET_APP_APK_SHA256="$(openssl dgst -sha256 -r "$apk" | cut -d' ' -f1)"
export ITB_WALLET_APP_APK_SHA256
ITB_ANDROID_APK_SHA256="$(openssl dgst -sha256 -r "$test_apk" | cut -d' ' -f1)"
export ITB_ANDROID_APK_SHA256

if adb shell pm path "$app" | tr -d '\r' | grep -q '^package:'; then
  echo 'Use a device with a fresh Preview installation. Uninstall the disposable ITB copy before another run.' >&2
  exit 2
fi
adb install "$apk"
adb install -t "$test_apk"

# ADB chooses unused host ports and only the created forwards are removed in cleanup.
instrumentation_pid=
ITB_ANDROID_PORT=
ITB_CHROME_PORT=
instrumentation_output="$(mktemp)"
chmod 600 "$instrumentation_output"
# shellcheck disable=SC2329 # Called by the EXIT trap.
cleanup() {
  if [[ -n "$instrumentation_pid" ]] && kill -0 "$instrumentation_pid" 2>/dev/null; then
    adb shell am force-stop "$app" >/dev/null 2>&1 || true
    kill "$instrumentation_pid" 2>/dev/null || true
    wait "$instrumentation_pid" 2>/dev/null || true
  fi
  if [[ -n "$ITB_ANDROID_PORT" ]]; then adb forward --remove "tcp:$ITB_ANDROID_PORT" >/dev/null || true; fi
  if [[ -n "$ITB_CHROME_PORT" ]]; then adb forward --remove "tcp:$ITB_CHROME_PORT" >/dev/null || true; fi
  rm -f "$instrumentation_output"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
device_port=39143
ITB_ANDROID_PORT="$(adb forward tcp:0 "tcp:$device_port" | tr -d '\r')"
ITB_CHROME_PORT="$(adb forward tcp:0 localabstract:chrome_devtools_remote | tr -d '\r')"
export ITB_ANDROID_PORT ITB_CHROME_PORT
ITB_ANDROID_TOKEN="$(openssl rand -hex 32)"
export ITB_ANDROID_TOKEN

adb shell am start -a android.intent.action.VIEW -d about:blank -p com.android.chrome >/dev/null
adb shell am instrument -w -r \
  -e class id.walt.walletdemo.compose.android.ItbDemoWalletTest \
  -e wallet.itb.port "$device_port" -e wallet.itb.token "$ITB_ANDROID_TOKEN" -e wallet.itb.mode "$mode" \
  "$app.test/androidx.test.runner.AndroidJUnitRunner" >"$instrumentation_output" 2>&1 &
instrumentation_pid=$!
for attempt in {1..60}; do
  if grep -q '^INSTRUMENTATION_STATUS: itb_state=listening' "$instrumentation_output"; then break; fi
  if ! kill -0 "$instrumentation_pid" 2>/dev/null; then
    echo 'Demo instrumentation exited before connecting.' >&2
    exit 2
  fi
  if [[ "$attempt" == 60 ]]; then echo 'Demo instrumentation did not open its bridge.' >&2; exit 2; fi
  sleep 1
done

status=0
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:itbWallet --max-workers=2 --console=plain || status=$?
if [[ "$status" != 0 ]]; then
  # The raw instrumentation log may contain wallet data. Print only known bridge stages.
  grep -E '^INSTRUMENTATION_STATUS: itb_state=(listening|wallet_setup|ready)[[:space:]]*$' "$instrumentation_output" >&2 || true
fi
if [[ "$status" == 0 ]]; then
  wait "$instrumentation_pid"
  if ! grep -q '^OK (1 test)' "$instrumentation_output"; then
    echo 'Demo instrumentation did not complete its test.' >&2
    exit 1
  fi
fi
exit "$status"
