#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=macos-lane-predicates.sh
source "$SCRIPT_DIR/macos-lane-predicates.sh"

LANES_FILE="${LANES_FILE:-$SCRIPT_DIR/../../ci/macos-lanes.json}"
FORCE_ALL_LABELS=${FORCE_ALL_LABELS:-'["ci:macos","ci:mobile"]'}
DOCS_LABEL="${DOCS_LABEL:-ci:sdk-docs}"

write_outputs() {
  local should_run="false"
  echo "run-ios-simulator=${RUN_IOS_SIMULATOR}" >> "$GITHUB_OUTPUT"
  echo "run-native=${RUN_NATIVE}" >> "$GITHUB_OUTPUT"
  echo "run-compose=${RUN_COMPOSE}" >> "$GITHUB_OUTPUT"
  echo "run-enterprise=${RUN_ENTERPRISE}" >> "$GITHUB_OUTPUT"
  echo "run-sdk-docs=${RUN_SDK_DOCS}" >> "$GITHUB_OUTPUT"

  if [[ "$RUN_IOS_SIMULATOR" == "true" || "$RUN_NATIVE" == "true" || "$RUN_COMPOSE" == "true" || "$RUN_ENTERPRISE" == "true" || "$RUN_SDK_DOCS" == "true" ]]; then
    should_run="true"
  fi
  echo "should-run=${should_run}" >> "$GITHUB_OUTPUT"
  echo "reason=${REASON}" >> "$GITHUB_OUTPUT"
  echo "macOS lane eligibility: simulator=${RUN_IOS_SIMULATOR} native=${RUN_NATIVE} compose=${RUN_COMPOSE} enterprise=${RUN_ENTERPRISE} sdk-docs=${RUN_SDK_DOCS}"
  echo "Reason: ${REASON}"
}

set_all_lanes() {
  RUN_IOS_SIMULATOR="$1"
  RUN_NATIVE="$1"
  RUN_COMPOSE="$1"
  RUN_ENTERPRISE="$1"
  RUN_SDK_DOCS="$1"
}

label_match() {
  jq -er \
    --argjson force_all "$FORCE_ALL_LABELS" \
    --arg docs_label "$DOCS_LABEL" '
      . as $pr_labels
      | {
          force_all: any($force_all[]; . as $label | ($pr_labels | index($label)) != null),
          docs: (($pr_labels | index($docs_label)) != null)
        }
      | if .force_all then "force_all"
        elif .docs then "docs"
        else "none"
        end
    ' <<< "$PR_LABELS"
}

RUN_IOS_SIMULATOR="false"
RUN_NATIVE="false"
RUN_COMPOSE="false"
RUN_ENTERPRISE="false"
RUN_SDK_DOCS="false"
REASON=""

case "$EVENT_NAME" in
  workflow_call)
    set_all_lanes true
    REASON="workflow call"
    write_outputs
    exit 0
    ;;
  workflow_dispatch)
    set_all_lanes true
    REASON="manual dispatch"
    write_outputs
    exit 0
    ;;
  push)
    if [[ "$REF" == "refs/heads/main" ]]; then
      set_all_lanes true
      REASON="main push"
    else
      set_all_lanes false
      REASON="non-main branch push"
    fi
    write_outputs
    exit 0
    ;;
  pull_request)
    if [[ "$PR_HEAD_REPO" != "$GITHUB_REPOSITORY" ]]; then
      set_all_lanes false
      REASON="fork PR requires manual dispatch by maintainer"
      write_outputs
      exit 0
    fi

    case "$(label_match)" in
      force_all)
        set_all_lanes true
        REASON="ci:macos or ci:mobile label"
        write_outputs
        exit 0
        ;;
      docs)
        RUN_SDK_DOCS="true"
        if [[ "$PR_DRAFT" == "true" ]]; then
          REASON="ci:sdk-docs label on draft PR"
          write_outputs
          exit 0
        fi
        ;;
    esac

    if [[ "$PR_DRAFT" == "true" ]]; then
      REASON="draft PR without ci:macos or ci:mobile label"
      write_outputs
      exit 0
    fi

    changed_files=()
    while IFS= read -r changed_file; do
      changed_files+=("$changed_file")
    done < <(
      gh api --paginate "repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/files" --jq '.[].filename'
    )

    matched_lanes=()
    if ((${#changed_files[@]} > 0)); then
      while IFS= read -r lane; do
        matched_lanes+=("$lane")
      done < <(matched_macos_lanes "$LANES_FILE" "${changed_files[@]}")
    fi

    for lane in "${matched_lanes[@]+"${matched_lanes[@]}"}"; do
      case "$lane" in
        ios-simulator) RUN_IOS_SIMULATOR="true" ;;
        native) RUN_NATIVE="true" ;;
        compose) RUN_COMPOSE="true" ;;
        enterprise) RUN_ENTERPRISE="true" ;;
        sdk-docs) RUN_SDK_DOCS="true" ;;
      esac
    done

    if ((${#matched_lanes[@]} > 0)); then
      REASON="path-matched lanes: ${matched_lanes[*]}"
    else
      REASON="no macOS-relevant path changes; use ci:macos, ci:mobile, or ci:sdk-docs to opt in"
    fi
    write_outputs
    exit 0
    ;;
esac

set_all_lanes false
REASON="unsupported event: ${EVENT_NAME:-}"
write_outputs
