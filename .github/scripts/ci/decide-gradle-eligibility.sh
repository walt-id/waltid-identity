#!/usr/bin/env bash
# Route PR-only release workflow changes to the release contract checks.
set -euo pipefail

set_decision() {
  echo "should-run=$1" >> "${GITHUB_OUTPUT:-/dev/stdout}"
  echo "reason=$2" >> "${GITHUB_OUTPUT:-/dev/stdout}"
  echo "Gradle eligibility: $1"
  echo "Reason: $2"
}

is_docs_or_asset() {
  local path="$1"
  # [[ == ]] treats * as matching any string, including slashes.
  [[ "$path" == *.md ]] && return 0
  [[ "$path" == *.png ]] && return 0
  [[ "$path" == *.jpg ]] && return 0
  [[ "$path" == *.webp ]] && return 0
  [[ "$path" == *.svg ]] && return 0
  [[ "$path" == *.ico ]] && return 0
  [[ "$path" == */LICENSE || "$path" == LICENSE ]] && return 0
  [[ "$path" == .github/ISSUE_TEMPLATE/* ]] && return 0
  [[ "$path" == .github/PULL_REQUEST_TEMPLATE.md ]] && return 0
  [[ "$path" == docs/* ]] && return 0
  return 1
}

if [[ "${EVENT_NAME:-}" == pull_request ]]; then
  gradle=false
  release=false
  only_release=true
  if [[ -s "${CHANGED_PATHS:-}" ]]; then
    while IFS= read -r -d '' path; do
      case "$path" in
        .github/workflows/api-docker.yml|.github/workflows/promote.yml|.github/workflows/release-candidate.yml|.github/tests/release/*)
          release=true ;;
        *)
          only_release=false
          is_docs_or_asset "$path" || gradle=true
          case "$path" in
            .github/workflows/build.yml|.github/scripts/ci/decide-gradle-eligibility.sh|.github/actions/pr-change-scope/*|.github/workflows/data/api-docker-matrix.json)
              release=true ;;
          esac ;;
      esac
    done < "$CHANGED_PATHS"
  else
    gradle=true
    release=true
    only_release=false
  fi
  if [[ "${FULL_COVERAGE:-true}" != false ]]; then
    gradle=true
    only_release=false
  fi
  {
    echo "release=$release"
    echo "release-only=$only_release"
  } >> "${GITHUB_OUTPUT:-/dev/stdout}"
  set_decision "$gradle" "PR paths and companion branches evaluated"
  exit 0
fi

changed_files=()
case "${EVENT_NAME:-}" in
  push)
    if [[ -n "${BEFORE:-}" && "${BEFORE}" != "0000000000000000000000000000000000000000" ]]; then
      while IFS= read -r changed_file; do
        [[ -n "$changed_file" ]] && changed_files+=("$changed_file")
      done < <(git diff --name-only "$BEFORE" "${AFTER:-HEAD}")
    fi
    ;;
  *)
    set_decision true "event ${EVENT_NAME:-unknown} always runs Gradle"
    exit 0
    ;;
esac

if ((${#changed_files[@]} == 0)); then
  set_decision true "no changed-file list; run Gradle"
  exit 0
fi

for file in "${changed_files[@]}"; do
  if ! is_docs_or_asset "$file"; then
    set_decision true "matched code path: $file"
    exit 0
  fi
done

set_decision false "docs/asset-only changes"
