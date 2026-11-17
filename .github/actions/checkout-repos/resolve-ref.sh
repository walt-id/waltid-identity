#!/usr/bin/env bash
set -euo pipefail

: "${CHECKOUT_REPOSITORY:?Checkout repository is required}"
: "${FALLBACK_REF:?Fallback ref is required}"
: "${GITHUB_OUTPUT:?GitHub output file is required}"

repository_url="https://github.com/${CHECKOUT_REPOSITORY}.git"
if [[ -n "${CHECKOUT_TOKEN:-}" ]]; then
  repository_url="https://x-access-token:${CHECKOUT_TOKEN}@github.com/${CHECKOUT_REPOSITORY}.git"
fi

resolved_ref="$FALLBACK_REF"
if [[ -n "${REQUESTED_REF:-}" ]]; then
  if GIT_TERMINAL_PROMPT=0 git ls-remote --exit-code "$repository_url" "$REQUESTED_REF" > /dev/null 2>&1; then
    resolved_ref="$REQUESTED_REF"
  else
    lookup_status=$?
    if [[ "$lookup_status" -ne 2 ]]; then
      echo "::error::Unable to inspect refs for ${CHECKOUT_REPOSITORY}; check repository access and connectivity." >&2
      exit "$lookup_status"
    fi
  fi
fi

echo "Selected ${resolved_ref} for ${CHECKOUT_REPOSITORY}"
printf 'ref=%s\n' "$resolved_ref" >> "$GITHUB_OUTPUT"
