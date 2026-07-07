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

changes="$TMP_DIR/changes.txt"

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

echo "mobile Swift parity gate tests passed ($pass_count cases)"
