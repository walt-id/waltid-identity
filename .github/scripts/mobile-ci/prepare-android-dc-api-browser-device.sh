#!/usr/bin/env bash

# Browser-only state layered on top of the native DC API device baseline. The
# Play Store image and cache are separate from the native google_apis substrate.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
source "$script_dir/prepare-android-dc-api-device.sh"
source "$script_dir/prepare-android-dc-api-chrome.sh"

DC_API_BROWSER_BASELINE_SCHEMA="${DC_API_BROWSER_BASELINE_SCHEMA:-1}"
DC_API_BROWSER_BASELINE_MANIFEST="$DC_API_AVD_DIR/waltid-dc-api-browser-baseline.manifest"
DC_API_BROWSER_READY_TIMEOUT_SECONDS="${DC_API_BROWSER_READY_TIMEOUT_SECONDS:-120}"
DC_API_BROWSER_EXPECTED_GMS_VERSION_NAME="${DC_API_BROWSER_EXPECTED_GMS_VERSION_NAME:-}"
DC_API_BROWSER_EXPECTED_GMS_VERSION_CODE="${DC_API_BROWSER_EXPECTED_GMS_VERSION_CODE:-}"

wait_for_android_browser_package_manager() {
  local deadline=$((SECONDS + DC_API_BROWSER_READY_TIMEOUT_SECONDS))
  local boot_completed

  echo "DC API browser device: waiting to freeze Play Store updates"
  while (( SECONDS < deadline )); do
    if adb_cmd get-state >/dev/null 2>&1; then
      boot_completed="$(adb_shell getprop sys.boot_completed 2>/dev/null || true)"
      if [[ "$boot_completed" == "1" ]] && adb_shell pm list packages >/dev/null 2>&1; then
        return 0
      fi
    fi
    sleep "$DC_API_POLL_SECONDS"
  done

  echo "::error::DC API browser device package manager was not ready within ${DC_API_BROWSER_READY_TIMEOUT_SECONDS}s" >&2
  return 1
}

disable_android_browser_play_store_updates() {
  wait_for_android_browser_package_manager
  adb_shell am force-stop "$DC_API_CHROME_PLAY_STORE_PACKAGE" >/dev/null 2>&1 || true
  adb_shell pm disable-user --user 0 "$DC_API_CHROME_PLAY_STORE_PACKAGE"
  assert_android_browser_play_store_disabled
  echo "DC API browser device: Play Store disabled for user 0"
}

assert_android_browser_play_store_disabled() {
  if ! adb_shell pm list packages -d "$DC_API_CHROME_PLAY_STORE_PACKAGE" 2>/dev/null |
    grep -Fxq "package:$DC_API_CHROME_PLAY_STORE_PACKAGE"; then
    echo "::error::Play Store is not disabled for user 0; browser versions may update during the suite" >&2
    return 1
  fi
}

assert_android_browser_expected_gms_identity() {
  local version code
  version="$(gms_version_name)"
  code="$(gms_version_code)"

  if [[ -z "$DC_API_BROWSER_EXPECTED_GMS_VERSION_NAME" ||
    ! "$DC_API_BROWSER_EXPECTED_GMS_VERSION_CODE" =~ ^[0-9]+$
  ]]; then
    echo "::error::Exact x86_64 browser GMS version name/code pins are required" >&2
    return 1
  fi
  if [[ "$version" != "$DC_API_BROWSER_EXPECTED_GMS_VERSION_NAME" ||
    "$code" != "$DC_API_BROWSER_EXPECTED_GMS_VERSION_CODE"
  ]]; then
    echo "::error::Browser GMS identity mismatch: expected=${DC_API_BROWSER_EXPECTED_GMS_VERSION_NAME}/${DC_API_BROWSER_EXPECTED_GMS_VERSION_CODE} actual=${version:-<missing>}/${code:-<missing>}" >&2
    return 1
  fi
  echo "DC API browser device: exact GMS identity $version/$code"
}

android_dc_api_browser_manifest_current_values() {
  local chrome_version chrome_code chrome_path locale chrome_helper_sha browser_helper_sha
  assert_android_browser_play_store_disabled
  assert_android_browser_expected_gms_identity
  chrome_assert_identity

  chrome_version="$(chrome_version_name)"
  chrome_code="$(chrome_version_code)"
  chrome_path="$(chrome_apk_path)"
  locale="$(adb_shell getprop persist.sys.locale 2>/dev/null || true)"
  [[ -n "$locale" ]] || locale="$(adb_shell getprop ro.product.locale 2>/dev/null || true)"
  chrome_helper_sha="$(sha256sum "$script_dir/prepare-android-dc-api-chrome.sh" | awk '{print $1}')"
  browser_helper_sha="$(sha256sum "$script_dir/prepare-android-dc-api-browser-device.sh" | awk '{print $1}')"

  if [[ -z "$chrome_version" || ! "$chrome_code" =~ ^[0-9]+$ || -z "$chrome_path" ||
    -z "$locale" || ! "$chrome_helper_sha" =~ ^[0-9a-f]{64}$ ||
    ! "$browser_helper_sha" =~ ^[0-9a-f]{64}$
  ]]; then
    echo "::error::DC API browser baseline identity is incomplete" >&2
    return 1
  fi

  printf 'schema=%s\n' "$DC_API_BROWSER_BASELINE_SCHEMA"
  printf 'gms_version_name=%s\n' "$DC_API_BROWSER_EXPECTED_GMS_VERSION_NAME"
  printf 'gms_version_code=%s\n' "$DC_API_BROWSER_EXPECTED_GMS_VERSION_CODE"
  printf 'chrome_package=%s\n' "$DC_API_CHROME_PACKAGE"
  printf 'chrome_version_name=%s\n' "$chrome_version"
  printf 'chrome_version_code=%s\n' "$chrome_code"
  printf 'chrome_apk_path=%s\n' "$chrome_path"
  printf 'chrome_minimum_major=%s\n' "$DC_API_CHROME_MINIMUM_MAJOR"
  printf 'chrome_first_run_complete=true\n'
  printf 'chrome_issuance_flag_id=%s\n' "$DC_API_CHROME_FLAG_ID"
  printf 'chrome_issuance_flag_state=Enabled\n'
  printf 'chrome_profile_locale=%s\n' "$locale"
  printf 'chrome_helper_sha256=%s\n' "$chrome_helper_sha"
  printf 'browser_helper_sha256=%s\n' "$browser_helper_sha"
  printf 'play_store_disabled_for_user_0=true\n'
}

write_android_dc_api_browser_baseline_manifest() {
  [[ -d "$DC_API_AVD_DIR" ]] || {
    echo "::error::Cannot write DC API browser baseline; AVD directory is missing: $DC_API_AVD_DIR" >&2
    return 1
  }
  local temporary_manifest="$DC_API_BROWSER_BASELINE_MANIFEST.tmp.$$"
  android_dc_api_browser_manifest_current_values > "$temporary_manifest"
  mv "$temporary_manifest" "$DC_API_BROWSER_BASELINE_MANIFEST"
  echo "DC API browser baseline manifest: wrote $DC_API_BROWSER_BASELINE_MANIFEST"
  cat "$DC_API_BROWSER_BASELINE_MANIFEST"
}

assert_android_dc_api_browser_baseline_manifest() {
  [[ -f "$DC_API_BROWSER_BASELINE_MANIFEST" ]] || {
    echo "::error::DC API browser baseline manifest is missing: $DC_API_BROWSER_BASELINE_MANIFEST" >&2
    return 1
  }

  local expected current
  expected="$(cat "$DC_API_BROWSER_BASELINE_MANIFEST")"
  current="$(android_dc_api_browser_manifest_current_values)"
  if [[ "$expected" != "$current" ]]; then
    echo "::error::Restored DC API browser AVD does not match its immutable Chrome baseline" >&2
    diff -u <(printf '%s\n' "$expected") <(printf '%s\n' "$current") || true
    return 1
  fi
  echo "DC API browser baseline manifest: exact Chrome/profile identity match"
}
