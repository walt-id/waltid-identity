#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_SCRIPT="$SCRIPT_DIR/check-mobile-swift-parity-decision.sh"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

pass_count=0

write_changes() {
  local file="$1"
  shift
  printf '%s\n' "$@" > "$file"
}

expect_pass() {
  local name="$1"
  local changes_file="$2"
  if "$CHECK_SCRIPT" --changed-files-file "$changes_file" > "$TMP_DIR/out" 2>&1; then
    pass_count=$((pass_count + 1))
  else
    echo "FAIL: expected pass for $name"
    cat "$TMP_DIR/out"
    exit 1
  fi
}

expect_fail() {
  local name="$1"
  local changes_file="$2"
  if "$CHECK_SCRIPT" --changed-files-file "$changes_file" > "$TMP_DIR/out" 2>&1; then
    echo "FAIL: expected failure for $name"
    cat "$TMP_DIR/out"
    exit 1
  fi

  if ! grep -q "Swift facade parity decision required" "$TMP_DIR/out"; then
    echo "FAIL: missing helpful failure message for $name"
    cat "$TMP_DIR/out"
    exit 1
  fi
  pass_count=$((pass_count + 1))
}

expect_git_fail() {
  local name="$1"
  local repo="$2"
  shift 2
  if (cd "$repo" && "$@" > "$TMP_DIR/out" 2>&1); then
    echo "FAIL: expected failure for $name"
    cat "$TMP_DIR/out"
    exit 1
  fi

  if ! grep -q "Swift facade parity decision required" "$TMP_DIR/out"; then
    echo "FAIL: missing helpful failure message for $name"
    cat "$TMP_DIR/out"
    exit 1
  fi
  pass_count=$((pass_count + 1))
}

changes="$TMP_DIR/changes.txt"

: > "$changes"
expect_pass "empty change list" "$changes"

write_changes "$changes" \
  "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonMain/kotlin/id/walt/wallet2/mobile/MobileWallet.kt"
expect_pass "private/source-only mobile change" "$changes"

write_changes "$changes" \
  "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/waltid-openid4vc-wallet-mobile.klib.api"
expect_fail "ABI change without Swift evidence" "$changes"

write_changes "$changes" \
  "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/waltid-openid4vc-wallet-mobile.klib.api" \
  "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/Wallet.swift"
expect_pass "ABI change with Swift source evidence" "$changes"

write_changes "$changes" \
  "waltid-libraries/protocols/waltid-openid4vc-wallet-persistence-mobile/api/waltid-openid4vc-wallet-persistence-mobile.klib.api" \
  "waltid-libraries/protocols/waltid-wallet-sdk-ios/Tests/WalletSDKTests/WalletAPITests.swift"
expect_pass "ABI change with Swift test evidence" "$changes"

write_changes "$changes" \
  "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/waltid-openid4vc-wallet-mobile.klib.api" \
  "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/Documentation.docc/WalletSDK.md"
expect_pass "ABI change with DocC evidence" "$changes"

write_changes "$changes" \
  "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/waltid-openid4vc-wallet-mobile.klib.api" \
  "waltid-libraries/protocols/waltid-wallet-sdk-ios/SwiftParityDecisions.md"
expect_pass "ABI change with explicit parity decision note" "$changes"

stacked_repo="$TMP_DIR/stacked-repo"
git init -q "$stacked_repo"
git -C "$stacked_repo" config user.email "test@example.com"
git -C "$stacked_repo" config user.name "Test User"
mkdir -p \
  "$stacked_repo/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api" \
  "$stacked_repo/waltid-libraries/protocols/waltid-wallet-sdk-ios"
printf 'baseline\n' > "$stacked_repo/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/waltid-openid4vc-wallet-mobile.klib.api"
git -C "$stacked_repo" add .
git -C "$stacked_repo" commit -qm "main baseline"
git -C "$stacked_repo" update-ref refs/remotes/origin/main HEAD
git -C "$stacked_repo" checkout -qb base-with-parity-decision
printf 'base parity decision\n' > "$stacked_repo/waltid-libraries/protocols/waltid-wallet-sdk-ios/SwiftParityDecisions.md"
git -C "$stacked_repo" add .
git -C "$stacked_repo" commit -qm "add base parity decision"
git -C "$stacked_repo" checkout -qb stacked-head
printf 'changed baseline\n' > "$stacked_repo/waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/api/waltid-openid4vc-wallet-mobile.klib.api"
git -C "$stacked_repo" commit -am "change stacked ABI baseline" -q
expect_git_fail \
  "stacked ABI change without fresh Swift evidence" \
  "$stacked_repo" \
  env GITHUB_BASE_REF=base-with-parity-decision "$CHECK_SCRIPT"

echo "mobile Swift parity gate tests passed ($pass_count cases)"
