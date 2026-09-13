#!/usr/bin/env bash
set -euo pipefail

# Run from the coordinated checkout. The Enterprise task records its framework, app-build,
# fixture-startup and execution phases; this entry point measures its prerequisite compilation.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${IOS_SIMULATOR_DESTINATION:?An explicit iOS Simulator destination is required}"
[[ -x ./gradlew && -d waltid-identity-enterprise ]] || { echo 'Run from the unified checkout' >&2; exit 1; }
report_root="waltid-identity-enterprise/waltid-enterprise-integration-tests/build/ios-test-results"
mkdir -p "$report_root"
run_dir="$(mktemp -d "$report_root/phases.XXXXXX")"
phase_run() {
  local phase="$1" started status
  shift
  started="$SECONDS"
  printf '%s\t%s\tstarted\t0\n' "$(date -u +%FT%TZ)" "$phase" | tee -a "$run_dir/phases.tsv"
  set +e
  "$@" 2>&1 | tee "$run_dir/$phase.log"
  status="${PIPESTATUS[0]}"
  set -e
  printf '%s\t%s\texit-%s\t%s\n' "$(date -u +%FT%TZ)" "$phase" "$status" "$((SECONDS - started))" | tee -a "$run_dir/phases.tsv"
  return "$status"
}
trap 'status=$?; printf "%s\trun\texit-%s\n" "$(date -u +%FT%TZ)" "$status" >> "$run_dir/phases.tsv"' EXIT
trap 'exit 143' TERM

xcrun simctl list devices available --json > "$run_dir/simulators.json"
simulator_id="$(python3 "$script_dir/select_ios_simulator.py" --destination "$IOS_SIMULATOR_DESTINATION" < "$run_dir/simulators.json")"
phase_run fixture-compilation ./gradlew :waltid-enterprise-integration-tests:classes --no-configuration-cache --console=plain
phase_run simulator-startup xcrun simctl bootstatus "$simulator_id" -b
phase_run mobile-suite ./gradlew :waltid-enterprise-integration-tests:enterpriseIosMobileIntegrationTest \
  --no-configuration-cache --console=plain \
  "-Penterprise.ios.destination=platform=iOS Simulator,id=$simulator_id"
