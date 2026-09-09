#!/usr/bin/env bash
# Shared path matching for macOS CI lanes. [[ == ]] pattern matching treats * as
# matching any string, including slashes, which matches platform-eligibility.yml.

path_matches_pattern() {
  local path="$1"
  local pattern="$2"
  [[ "$path" == $pattern ]]
}

lane_patterns_json() {
  local lanes_file="$1"
  local lane="$2"
  jq -c --arg lane "$lane" '
    (.shared // []) + (.lanes[$lane].patterns // [])
  ' "$lanes_file"
}

path_matches_any_pattern() {
  local path="$1"
  local patterns_json="$2"
  local pattern
  while IFS= read -r pattern; do
    [[ -n "$pattern" ]] || continue
    if path_matches_pattern "$path" "$pattern"; then
      return 0
    fi
  done < <(jq -r '.[]' <<< "$patterns_json")
  return 1
}

matched_macos_lanes() {
  local lanes_file="$1"
  shift
  local files=("$@")
  local lane patterns_json file

  while IFS= read -r lane; do
    patterns_json="$(lane_patterns_json "$lanes_file" "$lane")"
    for file in "${files[@]}"; do
      if path_matches_any_pattern "$file" "$patterns_json"; then
        printf '%s\n' "$lane"
        break
      fi
    done
  done < <(jq -r '.lanes | keys[]' "$lanes_file")
}
