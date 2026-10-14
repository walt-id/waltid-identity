#!/usr/bin/env bash
# Keep ordinary XCTest results; opt in to verbose system diagnostics when investigating a failure.
set -euo pipefail

diagnostics="${XCODE_TEST_DIAGNOSTICS:-never}"
case "$diagnostics" in
  never|on-failure) ;;
  *)
    echo 'XCODE_TEST_DIAGNOSTICS must be never or on-failure' >&2
    exit 2
    ;;
esac

exec xcodebuild test -collect-test-diagnostics "$diagnostics" "$@"
