#!/usr/bin/env bash
set -euo pipefail

BASE_REF=""
HEAD_REF="HEAD"
CHANGED_FILES_FILE=""

usage() {
  cat <<'USAGE'
Usage: scripts/check-mobile-swift-parity-decision.sh [--base-ref REF] [--head-ref REF] [--changed-files-file FILE]

Fails when a mobile SDK ABI baseline changes without an explicit Swift facade parity decision.
USAGE
}

read_changed_files() {
  changed_files=()
  while IFS= read -r changed_file; do
    changed_files+=("$changed_file")
  done
}

current_branch_name() {
  if [[ -n "${GITHUB_HEAD_REF:-}" ]]; then
    printf '%s\n' "$GITHUB_HEAD_REF"
    return
  fi

  if [[ -n "${GITHUB_REF_NAME:-}" ]]; then
    printf '%s\n' "$GITHUB_REF_NAME"
    return
  fi

  git branch --show-current 2>/dev/null || true
}

detect_base_branch() {
  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    printf '%s\n' "$GITHUB_BASE_REF"
    return
  fi

  local branch_name
  branch_name="$(current_branch_name)"
  if [[ -z "$branch_name" || -z "${GITHUB_REPOSITORY:-}" ]] || ! command -v gh >/dev/null 2>&1; then
    return
  fi

  gh pr view "$branch_name" \
    --repo "$GITHUB_REPOSITORY" \
    --json baseRefName \
    --jq .baseRefName 2>/dev/null || true
}

resolve_branch_ref() {
  local branch_name="$1"
  if git rev-parse --verify "refs/remotes/origin/$branch_name" >/dev/null 2>&1; then
    printf 'origin/%s\n' "$branch_name"
    return
  fi

  if git rev-parse --verify "refs/heads/$branch_name" >/dev/null 2>&1; then
    printf '%s\n' "$branch_name"
    return
  fi

  printf 'origin/%s\n' "$branch_name"
}

ensure_base_ref() {
  if git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
    return
  fi

  if [[ "$BASE_REF" == origin/* ]]; then
    local branch_name="${BASE_REF#origin/}"
    git fetch --prune origin "+refs/heads/${branch_name}:refs/remotes/origin/${branch_name}" >/dev/null 2>&1 || true
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-ref)
      BASE_REF="${2:?missing value for --base-ref}"
      shift 2
      ;;
    --head-ref)
      HEAD_REF="${2:?missing value for --head-ref}"
      shift 2
      ;;
    --changed-files-file)
      CHANGED_FILES_FILE="${2:?missing value for --changed-files-file}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -n "$CHANGED_FILES_FILE" ]]; then
  read_changed_files < "$CHANGED_FILES_FILE"
else
  if [[ -z "$BASE_REF" ]]; then
    base_branch="$(detect_base_branch)"
    if [[ -n "$base_branch" ]]; then
      BASE_REF="$(resolve_branch_ref "$base_branch")"
    else
      BASE_REF="origin/main"
    fi
  fi

  ensure_base_ref

  if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
    echo "Swift parity gate could not find base ref '$BASE_REF'." >&2
    echo "Fetch the base branch or pass --changed-files-file for deterministic verification." >&2
    exit 2
  fi
  merge_base="$(git merge-base "$BASE_REF" "$HEAD_REF")"
  read_changed_files < <(git diff --name-only "$merge_base...$HEAD_REF")
fi

mobile_abi_changed=false
swift_parity_evidence=false

for changed_file in "${changed_files[@]}"; do
  case "$changed_file" in
    waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/*|\
    waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/api/*)
      mobile_abi_changed=true
      ;;
  esac

  case "$changed_file" in
    waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/*.swift|\
    waltid-libraries/protocols/waltid-wallet-sdk-ios/Tests/WalletSDKTests/*|\
    waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md|\
    waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/Documentation.docc/*|\
    waltid-libraries/protocols/waltid-wallet-sdk-ios/SwiftParityDecisions.md)
      swift_parity_evidence=true
      ;;
  esac
done

if [[ "$mobile_abi_changed" == false ]]; then
  echo "No mobile SDK ABI baseline changes detected; Swift parity gate skipped."
  exit 0
fi

if [[ "$swift_parity_evidence" == true ]]; then
  echo "Mobile SDK ABI baseline changed and Swift facade parity evidence is present."
  exit 0
fi

cat >&2 <<'ERROR'
Swift facade parity decision required.

The mobile SDK Kotlin ABI baseline changed, which means reviewers need explicit evidence
that the Swift facade was considered. This does not require a mechanical 1:1 Swift mirror.

Satisfy this gate with one of:
- Swift facade source changes under waltid-wallet-sdk-ios/Sources/WalletSDK/
- Swift facade tests under waltid-wallet-sdk-ios/Tests/WalletSDKTests/
- WalletSDK README or DocC updates
- An entry in waltid-wallet-sdk-ios/SwiftParityDecisions.md explaining why Swift intentionally omits or defers the KMP capability
ERROR
exit 1
