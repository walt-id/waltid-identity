#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
workspace_dir="$(cd "$identity_dir/.." && pwd -P)"

cd "$workspace_dir"

# Release the fixture build JVM before starting the separate Android build.
./gradlew :waltid-enterprise-integration-tests:classes --no-configuration-cache --no-daemon
"$identity_dir/gradlew" -p "$identity_dir" \
  -PenableAndroidBuild=true \
  :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleAndroidDeviceTest \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleProductionDebug \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:assembleProductionDebugAndroidTest \
  --no-configuration-cache

"$identity_dir/gradlew" -p "$identity_dir" --stop
