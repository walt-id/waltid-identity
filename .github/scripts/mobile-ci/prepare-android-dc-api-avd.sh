#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$script_dir/prepare-android-dc-api-device.sh"

echo "DC API AVD preparation: creating a known-good Play Store baseline for configured-AVD caching"
prepare_android_dc_api_device false
echo "DC API AVD preparation: rebooting once after GMS provisioning before caching the userdata disk"
adb_cmd reboot
prepare_android_dc_api_device true
assert_android_dc_api_device_unchanged
write_android_dc_api_baseline_manifest
assert_android_dc_api_baseline_manifest
echo "DC API AVD preparation: exact substrate/GMS/system UI gates passed; the runner will cache only the configured AVD disk"
