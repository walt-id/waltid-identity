#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$script_dir/prepare-android-dc-api-device.sh"

echo "DC API AVD preparation: creating a known-good Play Store baseline for Quick Boot caching"
prepare_android_dc_api_device false
echo "DC API AVD preparation: rebooting once after GMS provisioning before snapshotting"
adb_cmd reboot
prepare_android_dc_api_device true
assert_android_dc_api_device_unchanged
echo "DC API AVD preparation: GMS and system UI health gates passed; the runner will persist the Quick Boot snapshot"
