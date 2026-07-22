#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"

"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-libraries:crypto:waltid-crypto2-signum:connectedAndroidDeviceTest \
  --info
