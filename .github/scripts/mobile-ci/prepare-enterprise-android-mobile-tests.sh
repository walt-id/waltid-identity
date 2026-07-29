#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"

"$identity_dir/gradlew" -p "$identity_dir" \
  -PenableAndroidBuild=true \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleAndroidDeviceTest \
  --no-configuration-cache
