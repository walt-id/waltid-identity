#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
workspace_dir="$(cd "$identity_dir/.." && pwd -P)"

source "$script_dir/prepare-android-dc-api-device.sh"
wait_for_android_dc_api_device

cd "$workspace_dir"

adb shell pm path com.android.chrome | grep -q '^package:' || {
  echo 'Enterprise authorization tests require Chrome on the emulator' >&2
  exit 1
}
adb shell cmd role add-role-holder android.app.role.BROWSER com.android.chrome

./gradlew :waltid-enterprise-integration-tests:enterpriseAndroidMobileIntegrationTest --no-configuration-cache
