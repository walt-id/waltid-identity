#!/usr/bin/env bash
set -euo pipefail

out="${1:-/dev/stdout}"
variant="${VARIANT:-unknown}"
pr_number="${PR_NUMBER:-}"
commit="${COMMIT_SHA:-}"
author="${AUTHOR:-}"
tag="${RELEASE_TAG:-}"
short_commit="${commit:0:7}"

{
  echo "variant: ${variant}"
  if [[ -n "$pr_number" ]]; then
    echo "pr: #${pr_number}"
  fi
  if [[ -n "$short_commit" ]]; then
    echo "commit: ${short_commit}"
  fi
  if [[ -n "$author" ]]; then
    echo "author: ${author}"
  fi
  if [[ -n "$tag" ]]; then
    echo "tag: ${tag}"
  fi
} > "$out"
