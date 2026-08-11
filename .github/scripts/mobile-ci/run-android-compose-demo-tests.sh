#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
instrumentation_args=()

if [[ -n "${ANDROID_TEST_CLASS:-}" ]]; then
  instrumentation_args+=("-Pandroid.testInstrumentationRunnerArguments.class=$ANDROID_TEST_CLASS")
fi

"$identity_dir/gradlew" -p "$identity_dir" \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedDebugAndroidTest \
  "${instrumentation_args[@]}" \
  -PtransactionDataProfiles.url=https://wallet.demo.walt.id/wallet-api/transaction-data-profiles \
  --info
