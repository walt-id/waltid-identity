#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"

"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:connectedAndroidDeviceTest \
  --info
