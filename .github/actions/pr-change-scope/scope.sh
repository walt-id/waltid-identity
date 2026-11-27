#!/usr/bin/env bash
set -euo pipefail

files="$(mktemp "$RUNNER_TEMP/pr-files.XXXXXX")"
echo "files=$files" >> "$GITHUB_OUTPUT"
# The caller checks out the PR with full history. Disabling rename detection includes both paths.
if ! git diff --no-ext-diff --no-renames --name-only -z "$BASE_SHA...$HEAD_SHA" -- > "$files" || [[ ! -s "$files" ]]; then
  : > "$files"
  echo "full=true" >> "$GITHUB_OUTPUT"
  echo "Could not establish the complete PR diff. Selecting broad coverage."
  exit 0
fi

repo="${GITHUB_REPOSITORY#*/}"
# shellcheck disable=SC2016 # GraphQL variables are bound by gh, not by bash.
query='query($branch:String!, $cli:Boolean!) {
  unified:repository(owner:"walt-id",name:"waltid-unified-build"){...refs}
  identity:repository(owner:"walt-id",name:"waltid-identity"){...refs}
  enterprise:repository(owner:"walt-id",name:"waltid-identity-enterprise"){...refs}
  license:repository(owner:"walt-id",name:"waltid-identity-enterprise-license"){...refs}
  quickstart:repository(owner:"walt-id",name:"waltid-enterprise-quickstart") @include(if:$cli){...refs}
} fragment refs on Repository {name ref(qualifiedName:$branch){name}}'
cli=false
[[ "$repo" != waltid-identity-enterprise ]] || cli=true
if [[ -n "$GH_TOKEN" ]] && refs="$(gh api graphql -f query="$query" -f branch="refs/heads/$HEAD_BRANCH" -F cli="$cli")"; then
  if jq -e --arg repo "$repo" --argjson cli "$cli" '
      (.errors == null) and (.data | length == (if $cli then 5 else 4 end)) and
      (.data | all(.[]; type == "object" and has("ref") and (.name == $repo or .ref == null)))' <<< "$refs" >/dev/null; then
    echo "full=false" >> "$GITHUB_OUTPUT"
    exit 0
  fi
fi
echo "full=true" >> "$GITHUB_OUTPUT"
echo "Companion branches are present or could not be ruled out. Selecting broad coverage."
