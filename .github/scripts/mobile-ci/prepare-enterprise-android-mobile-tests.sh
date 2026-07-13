#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
workspace_dir="$(cd "$identity_dir/.." && pwd -P)"

cd "$workspace_dir"

./gradlew :waltid-enterprise-integration-tests:classes --no-configuration-cache
"$identity_dir/gradlew" -p "$identity_dir" \
  -PenableAndroidBuild=true \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleAndroidDeviceTest \
  --no-configuration-cache

./gradlew --stop
"$identity_dir/gradlew" -p "$identity_dir" --stop
