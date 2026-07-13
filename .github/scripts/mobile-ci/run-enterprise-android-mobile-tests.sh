#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
identity_dir="$(cd "$script_dir/../../.." && pwd -P)"
workspace_dir="$(cd "$identity_dir/.." && pwd -P)"

cd "$workspace_dir"

./gradlew :waltid-enterprise-integration-tests:enterpriseAndroidMobileIntegrationTest --no-configuration-cache
