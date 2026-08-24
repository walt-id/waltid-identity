#!/usr/bin/env bash
# Skip the Linux Gradle job when every changed file is a docs/asset path that
# used to be listed in build.yml paths-ignore. The workflow itself must still
# start so ci-gate can run as a required check.
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

changed_files=()
case "${EVENT_NAME:-}" in
  pull_request)
    while IFS= read -r changed_file; do
      [[ -n "$changed_file" ]] && changed_files+=("$changed_file")
    done < <(
      gh api --paginate "repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/files" --jq '.[].filename'
    )
    ;;
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
