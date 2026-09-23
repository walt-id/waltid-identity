#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=macos-lane-predicates.sh
source "$SCRIPT_DIR/macos-lane-predicates.sh"

LANES_FILE="${LANES_FILE:-$SCRIPT_DIR/../../ci/macos-lanes.json}"
FIXTURES_FILE="${FIXTURES_FILE:-$SCRIPT_DIR/../../ci/macos-lane-fixtures.json}"

failures=0
count=0

while IFS= read -r fixture; do
  count=$((count + 1))
  name="$(jq -r '.name' <<< "$fixture")"
  files=()
  while IFS= read -r file; do
    files+=("$file")
  done < <(jq -r '.files[]' <<< "$fixture")
  expected="$(jq -r '.lanes | sort | join(" ")' <<< "$fixture")"
  actual=""
  if ((${#files[@]} > 0)); then
    actual="$(matched_macos_lanes "$LANES_FILE" "${files[@]}" | sort | paste -sd' ' -)"
  fi

  if [[ "$actual" == "$expected" ]]; then
    echo "PASS: $name"
  else
    echo "FAIL: $name"
    echo "  files:    ${files[*]}"
    echo "  expected: ${expected:-<none>}"
    echo "  actual:   ${actual:-<none>}"
    failures=$((failures + 1))
  fi
done < <(jq -c '.fixtures[]' "$FIXTURES_FILE")

# Exercise the real selector as well as the path predicates: label parsing must
# not silently turn an opted-in draft into a successful all-lanes-skipped result.
selection_dir="$(mktemp -d)"
trap 'rm -rf "$selection_dir"' EXIT

check_draft_selection() {
  local name="$1" labels="$2" head_repo="$3" expected="$4"
  local output="$selection_dir/output" log="$selection_dir/log" status=0
  count=$((count + 1))
  : > "$output"
  env EVENT_NAME=pull_request PR_DRAFT=true PR_LABELS="$labels" \
    PR_HEAD_REPO="$head_repo" GITHUB_REPOSITORY=walt-id/waltid-identity \
    GITHUB_OUTPUT="$output" \
    bash "$SCRIPT_DIR/decide-macos-lanes.sh" > "$log" 2>&1 || status=$?

  local matches=true lane value
  if [[ "$expected" == error ]]; then
    [[ "$status" != 0 && ! -s "$output" ]] || matches=false
  else
    [[ "$status" == 0 ]] || matches=false
    for lane in ios-simulator native compose enterprise sdk-docs; do
      value=false
      if [[ "$expected" == all || ("$expected" == docs && "$lane" == sdk-docs) ]]; then
        value=true
      fi
      grep -qx "run-$lane=$value" "$output" || matches=false
    done
  fi
  if [[ "$matches" == true ]]; then
    echo "PASS: $name"
  else
    echo "FAIL: $name (exit=$status, expected=$expected)"
    cat "$log" "$output"
    failures=$((failures + 1))
  fi
}

check_draft_selection 'ci:mobile enables every lane on a draft' '["ci:mobile"]' walt-id/waltid-identity all
check_draft_selection 'ci:macos enables every lane on a draft' '["ci:macos"]' walt-id/waltid-identity all
check_draft_selection 'force-all wins over docs-only selection' '["ci:sdk-docs","ci:mobile"]' walt-id/waltid-identity all
check_draft_selection 'docs-only label enables only SDK docs on a draft' '["ci:sdk-docs"]' walt-id/waltid-identity docs
check_draft_selection 'an unlabelled draft stays skipped' '[]' walt-id/waltid-identity none
check_draft_selection 'an unrelated label does not opt a draft in' '["bug"]' walt-id/waltid-identity none
check_draft_selection 'a fork cannot opt into privileged lanes' '["ci:mobile"]' contributor/fork none
check_draft_selection 'invalid label JSON fails without publishing skip outputs' 'invalid-json' walt-id/waltid-identity error

if ((failures > 0)); then
  echo "macOS lane tests: $failures/$count failed"
  exit 1
fi

echo "macOS lane tests: $count passed"
