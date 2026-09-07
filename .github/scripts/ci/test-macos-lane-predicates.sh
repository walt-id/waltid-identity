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

if ((failures > 0)); then
  echo "macOS lane predicate fixtures: $failures/$count failed"
  exit 1
fi

echo "macOS lane predicate fixtures: $count passed"
