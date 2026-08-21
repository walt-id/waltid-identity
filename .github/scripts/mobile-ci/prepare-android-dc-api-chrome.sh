#!/usr/bin/env bash

# Prepares and validates the Chrome userdata needed by Android Digital Credentials browser E2Es.
# This file is deliberately standalone: callers may source it next to the existing DC API device
# helper, but it neither sources nor changes that helper's Android/GMS baseline policy.

CHROME_ADB_BIN="${ADB_BIN:-adb}"
CHROME_ANDROID_SERIAL="${ANDROID_SERIAL:-}"
DC_API_CHROME_PACKAGE="${DC_API_CHROME_PACKAGE:-com.android.chrome}"
DC_API_CHROME_MAIN_ACTIVITY="${DC_API_CHROME_MAIN_ACTIVITY:-com.google.android.apps.chrome.Main}"
DC_API_CHROME_FIRST_RUN_ACTIVITY="${DC_API_CHROME_FIRST_RUN_ACTIVITY:-org.chromium.chrome.browser.firstrun.FirstRunActivity}"
DC_API_CHROME_TABBED_ACTIVITY="${DC_API_CHROME_TABBED_ACTIVITY:-org.chromium.chrome.browser.ChromeTabbedActivity}"
DC_API_CHROME_MINIMUM_MAJOR="${DC_API_CHROME_MINIMUM_MAJOR:-143}"
DC_API_CHROME_EXPECTED_VERSION_NAME="${DC_API_CHROME_EXPECTED_VERSION_NAME:-${EXPECTED_CHROME_VERSION_NAME:-}}"
DC_API_CHROME_EXPECTED_VERSION_CODE="${DC_API_CHROME_EXPECTED_VERSION_CODE:-${EXPECTED_CHROME_VERSION_CODE:-}}"
DC_API_CHROME_REQUIRE_PLAY_STORE_DISABLED="${DC_API_CHROME_REQUIRE_PLAY_STORE_DISABLED:-true}"
DC_API_CHROME_PLAY_STORE_PACKAGE="${DC_API_CHROME_PLAY_STORE_PACKAGE:-com.android.vending}"
DC_API_CHROME_FLAG_URI="${DC_API_CHROME_FLAG_URI:-chrome://flags/#web-identity-digital-credentials-creation}"
DC_API_CHROME_FLAG_ID="${DC_API_CHROME_FLAG_ID:-web-identity-digital-credentials-creation}"
DC_API_CHROME_FLAG_HINT="${DC_API_CHROME_FLAG_HINT:-DigitalCredentialsCreation}"
DC_API_CHROME_PORTAL_URL="${DC_API_CHROME_PORTAL_URL:-https://portal2.demo.walt.id/}"
DC_API_CHROME_PORTAL_URL_BAR_PATTERN="${DC_API_CHROME_PORTAL_URL_BAR_PATTERN:-^(https://)?portal2[.]demo[.]walt[.]id/?$}"
DC_API_CHROME_PORTAL_RENDER_TEXT_PATTERN="${DC_API_CHROME_PORTAL_RENDER_TEXT_PATTERN:-Demo Portal|Issue credential|Verify credential}"
DC_API_CHROME_PORTAL_ERROR_TEXT_PATTERN="${DC_API_CHROME_PORTAL_ERROR_TEXT_PATTERN:-Aw, Snap|This site can.t be reached|ERR_}"
DC_API_CHROME_READY_TIMEOUT_SECONDS="${DC_API_CHROME_READY_TIMEOUT_SECONDS:-45}"
DC_API_CHROME_FLAG_TIMEOUT_SECONDS="${DC_API_CHROME_FLAG_TIMEOUT_SECONDS:-60}"
DC_API_CHROME_PORTAL_TIMEOUT_SECONDS="${DC_API_CHROME_PORTAL_TIMEOUT_SECONDS:-90}"
DC_API_CHROME_PORTAL_STABILITY_SECONDS="${DC_API_CHROME_PORTAL_STABILITY_SECONDS:-4}"
DC_API_CHROME_POLL_SECONDS="${DC_API_CHROME_POLL_SECONDS:-2}"
DC_API_CHROME_OMNIBOX_ATTEMPTS="${DC_API_CHROME_OMNIBOX_ATTEMPTS:-3}"
DC_API_CHROME_VALIDATE_PORTAL_DURING_PREPARE="${DC_API_CHROME_VALIDATE_PORTAL_DURING_PREPARE:-false}"
DC_API_CHROME_ARTIFACT_DIR="${DC_API_CHROME_ARTIFACT_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/waltid-dc-api-chrome-artifacts}"
DC_API_CHROME_REMOTE_UI_DUMP="${DC_API_CHROME_REMOTE_UI_DUMP:-/sdcard/waltid-dc-api-chrome.xml}"
CHROME_LAST_UI_DUMP=""

chrome_log() {
  echo "DC API Chrome: $*" >&2
}

chrome_error() {
  echo "::error title=DC API Chrome substrate::$*" >&2
}

chrome_adb_cmd() {
  if [[ -n "$CHROME_ANDROID_SERIAL" ]]; then
    "$CHROME_ADB_BIN" -s "$CHROME_ANDROID_SERIAL" "$@"
  else
    "$CHROME_ADB_BIN" "$@"
  fi
}

chrome_adb_shell() {
  chrome_adb_cmd shell "$@" | tr -d '\r'
}

chrome_initialize_work_dir() {
  mkdir -p "$DC_API_CHROME_ARTIFACT_DIR"
  if [[ -z "$CHROME_LAST_UI_DUMP" ]]; then
    CHROME_LAST_UI_DUMP="$DC_API_CHROME_ARTIFACT_DIR/latest-ui.xml"
  fi
}

chrome_dump_ui() {
  chrome_initialize_work_dir || return 1
  if ! chrome_adb_cmd shell uiautomator dump --compressed "$DC_API_CHROME_REMOTE_UI_DUMP" \
    >"$DC_API_CHROME_ARTIFACT_DIR/uiautomator-command.log" 2>&1; then
    return 1
  fi
  if ! chrome_adb_cmd exec-out cat "$DC_API_CHROME_REMOTE_UI_DUMP" >"$CHROME_LAST_UI_DUMP"; then
    return 1
  fi
  grep -q '<hierarchy' "$CHROME_LAST_UI_DUMP"
}

# Query the most recent UiAutomator XML with Python's standard library.
#
# Usage:
#   chrome_ui_query count _ key=value ...
#   chrome_ui_query value text key=value ...
#   chrome_ui_query center _ key=value ...
#
# Supported filters: resource-id, resource-id-suffix, text, text-regex, content-desc, hint,
# package, class, enabled, focused, clickable, checkable, selected, and visible. `value` and
# `center` require exactly one match so selector drift cannot silently click an arbitrary node.
chrome_ui_query() {
  local mode="${1:?query mode is required}"
  local output_attribute="${2:-_}"
  shift 2
  python3 - "$CHROME_LAST_UI_DUMP" "$mode" "$output_attribute" "$@" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, mode, output_attribute, *raw_filters = sys.argv[1:]
filters = []
for raw_filter in raw_filters:
    if "=" not in raw_filter:
        raise SystemExit(f"invalid filter: {raw_filter}")
    key, value = raw_filter.split("=", 1)
    filters.append((key, value))

def boolean(value):
    return value.lower() == "true"

def bounds(node):
    match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.get("bounds", ""))
    if not match:
        return None
    return tuple(map(int, match.groups()))

def matches(node):
    for key, expected in filters:
        if key == "resource-id-suffix":
            if not node.get("resource-id", "").endswith(expected):
                return False
        elif key == "text-regex":
            if re.search(expected, node.get("text", "")) is None:
                return False
        elif key == "visible":
            node_bounds = bounds(node)
            visible = node_bounds is not None and node_bounds[2] > node_bounds[0] and node_bounds[3] > node_bounds[1]
            if visible != boolean(expected):
                return False
        elif key in {"enabled", "focused", "clickable", "checkable", "selected"}:
            if boolean(node.get(key, "false")) != boolean(expected):
                return False
        elif node.get(key, "") != expected:
            return False
    return True

nodes = [node for node in ET.parse(xml_path).iter("node") if matches(node)]
if mode == "count":
    print(len(nodes))
    raise SystemExit(0)
if len(nodes) != 1:
    print(f"expected exactly one UI node, found {len(nodes)}", file=sys.stderr)
    raise SystemExit(1)
node = nodes[0]
if mode == "value":
    print(node.get(output_attribute, ""))
elif mode == "center":
    node_bounds = bounds(node)
    if node_bounds is None or node_bounds[2] <= node_bounds[0] or node_bounds[3] <= node_bounds[1]:
        raise SystemExit("matched node has no visible bounds")
    x1, y1, x2, y2 = node_bounds
    print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
else:
    raise SystemExit(f"unknown query mode: {mode}")
PY
}

chrome_ui_count() {
  chrome_ui_query count _ "$@"
}

chrome_ui_value() {
  local attribute="${1:?attribute is required}"
  shift
  chrome_ui_query value "$attribute" "$@"
}

chrome_wait_for_ui_center() {
  local description="${1:?description is required}"
  local timeout_seconds="${2:?timeout is required}"
  shift 2
  local deadline=$((SECONDS + timeout_seconds))
  local center=""
  while (( SECONDS < deadline )); do
    if chrome_dump_ui && center="$(chrome_ui_query center _ "$@" 2>/dev/null)"; then
      printf '%s\n' "$center"
      return 0
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  chrome_error "Timed out waiting for $description"
  return 1
}

chrome_wait_for_ui_value() {
  local description="${1:?description is required}"
  local timeout_seconds="${2:?timeout is required}"
  local attribute="${3:?attribute is required}"
  shift 3
  local deadline=$((SECONDS + timeout_seconds))
  local value=""
  while (( SECONDS < deadline )); do
    if chrome_dump_ui && value="$(chrome_ui_query value "$attribute" "$@" 2>/dev/null)"; then
      printf '%s\n' "$value"
      return 0
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  chrome_error "Timed out waiting for $description"
  return 1
}

chrome_tap_center() {
  local center="${1:?center is required}"
  local x y
  read -r x y <<<"$center"
  [[ "$x" =~ ^[0-9]+$ && "$y" =~ ^[0-9]+$ ]] || {
    chrome_error "Invalid UI center: $center"
    return 1
  }
  chrome_adb_shell input tap "$x" "$y" >/dev/null
}

chrome_current_activity() {
  chrome_adb_shell dumpsys activity activities 2>/dev/null |
    sed -n -E 's/.*(topResumedActivity|mResumedActivity|ResumedActivity)[=:].* u0 ([^ ]+).*/\2/p' |
    head -n 1
}

chrome_package_details() {
  chrome_adb_shell dumpsys package "$DC_API_CHROME_PACKAGE" 2>/dev/null || true
}

chrome_version_name() {
  chrome_package_details | sed -n 's/.*versionName=\([^[:space:]]*\).*/\1/p' | head -n 1
}

chrome_version_code() {
  chrome_package_details | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1
}

chrome_apk_path() {
  chrome_adb_shell pm path "$DC_API_CHROME_PACKAGE" 2>/dev/null |
    sed -n 's/^package:\(.*\)$/\1/p' |
    head -n 1
}

chrome_play_store_is_disabled() {
  chrome_adb_shell pm list packages -d "$DC_API_CHROME_PLAY_STORE_PACKAGE" 2>/dev/null |
    grep -Fxq "package:$DC_API_CHROME_PLAY_STORE_PACKAGE"
}

chrome_assert_identity() {
  chrome_adb_cmd wait-for-device || return 1
  local apk_path version_name version_code major resolved_activity
  apk_path="$(chrome_apk_path)"
  version_name="$(chrome_version_name)"
  version_code="$(chrome_version_code)"
  resolved_activity="$(chrome_adb_shell cmd package resolve-activity --brief \
    -a android.intent.action.VIEW -d "$DC_API_CHROME_PORTAL_URL" "$DC_API_CHROME_PACKAGE" 2>/dev/null |
    awk '/\// { print $NF }' | tail -n 1)"

  [[ -n "$apk_path" ]] || {
    chrome_error "Chrome APK is missing for $DC_API_CHROME_PACKAGE"
    return 1
  }
  [[ -n "$version_name" && -n "$version_code" ]] || {
    chrome_error "Chrome package identity is incomplete: version=$version_name versionCode=$version_code"
    return 1
  }
  [[ -n "$resolved_activity" ]] || {
    chrome_error "Chrome cannot resolve an HTTPS VIEW Activity"
    return 1
  }
  if ! chrome_adb_shell pm list packages -e "$DC_API_CHROME_PACKAGE" 2>/dev/null |
    grep -Fxq "package:$DC_API_CHROME_PACKAGE"; then
    chrome_error "Chrome is not enabled for user 0"
    return 1
  fi

  major="${version_name%%.*}"
  if [[ ! "$DC_API_CHROME_MINIMUM_MAJOR" =~ ^[0-9]+$ ]]; then
    chrome_error "DC_API_CHROME_MINIMUM_MAJOR must be numeric: $DC_API_CHROME_MINIMUM_MAJOR"
    return 1
  fi
  if [[ ! "$major" =~ ^[0-9]+$ ]] || (( major < DC_API_CHROME_MINIMUM_MAJOR )); then
    chrome_error "Chrome $version_name does not meet the required major >= $DC_API_CHROME_MINIMUM_MAJOR"
    return 1
  fi
  if [[ -n "$DC_API_CHROME_EXPECTED_VERSION_NAME" && "$version_name" != "$DC_API_CHROME_EXPECTED_VERSION_NAME" ]]; then
    chrome_error "Chrome version mismatch: expected=$DC_API_CHROME_EXPECTED_VERSION_NAME actual=$version_name"
    return 1
  fi
  if [[ -n "$DC_API_CHROME_EXPECTED_VERSION_CODE" && "$version_code" != "$DC_API_CHROME_EXPECTED_VERSION_CODE" ]]; then
    chrome_error "Chrome versionCode mismatch: expected=$DC_API_CHROME_EXPECTED_VERSION_CODE actual=$version_code"
    return 1
  fi
  if [[ "$DC_API_CHROME_REQUIRE_PLAY_STORE_DISABLED" == "true" ]] && ! chrome_play_store_is_disabled; then
    chrome_error "Play Store must be disabled before Chrome preparation/validation"
    return 1
  fi

  chrome_log "identity package=$DC_API_CHROME_PACKAGE version=$version_name versionCode=$version_code apk=$apk_path activity=$resolved_activity"
}

chrome_start_main() {
  chrome_adb_shell am start -W -n "$DC_API_CHROME_PACKAGE/$DC_API_CHROME_MAIN_ACTIVITY" >/dev/null
}

chrome_click_ui_node() {
  local description="${1:?description is required}"
  local timeout_seconds="${2:?timeout is required}"
  shift 2
  local center
  center="$(chrome_wait_for_ui_center "$description" "$timeout_seconds" "$@")" || return 1
  chrome_tap_center "$center"
}

chrome_wait_for_tabbed_ui() {
  local deadline=$((SECONDS + DC_API_CHROME_READY_TIMEOUT_SECONDS))
  local activity toolbar_count
  while (( SECONDS < deadline )); do
    activity="$(chrome_current_activity)"
    if [[ "$activity" == *"$DC_API_CHROME_FIRST_RUN_ACTIVITY"* ]]; then
      sleep "$DC_API_CHROME_POLL_SECONDS"
      continue
    fi
    if chrome_dump_ui; then
      toolbar_count="$(chrome_ui_count resource-id-suffix=url_bar visible=true 2>/dev/null || printf 0)"
      if (( toolbar_count == 1 )) && [[ "$activity" == *"$DC_API_CHROME_TABBED_ACTIVITY"* || "$activity" == *"$DC_API_CHROME_MAIN_ACTIVITY"* ]]; then
        return 0
      fi
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  chrome_error "Chrome did not reach its tabbed UI; activity=$(chrome_current_activity)"
  return 1
}

chrome_complete_first_run() {
  chrome_start_main || return 1
  local dismiss_count=0 negative_count=0 toolbar_count=0 activity deadline
  deadline=$((SECONDS + DC_API_CHROME_READY_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    activity="$(chrome_current_activity)"
    if chrome_dump_ui; then
      dismiss_count="$(chrome_ui_count resource-id-suffix=signin_fre_dismiss_button text='Use without an account' enabled=true visible=true 2>/dev/null || printf 0)"
      if (( dismiss_count == 1 )); then
        chrome_click_ui_node "Chrome Use without an account button" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" \
          resource-id-suffix=signin_fre_dismiss_button text='Use without an account' enabled=true clickable=true visible=true || return 1
        break
      fi
      negative_count="$(chrome_ui_count resource-id-suffix=negative_button text='No thanks' enabled=true visible=true 2>/dev/null || printf 0)"
      if (( negative_count == 1 )); then
        chrome_click_ui_node "Chrome No thanks notification button" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" \
          resource-id-suffix=negative_button text='No thanks' enabled=true clickable=true visible=true || return 1
        chrome_wait_for_tabbed_ui || return 1
        chrome_log "completed a partially finished first run without adding an account"
        return 0
      fi
      toolbar_count="$(chrome_ui_count resource-id-suffix=url_bar visible=true 2>/dev/null || printf 0)"
      if [[ "$activity" != *"$DC_API_CHROME_FIRST_RUN_ACTIVITY"* ]] && (( toolbar_count == 1 )); then
        chrome_log "first run was already complete"
        return 0
      fi
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  if (( dismiss_count != 1 )); then
    chrome_error "Chrome first-run Activity exposed no recognized dismissal control"
    return 1
  fi

  # Chrome 145 shows an in-app notification education dialog after account dismissal. It is optional,
  # but when present it must be dismissed without granting notification permission.
  deadline=$((SECONDS + DC_API_CHROME_READY_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if chrome_dump_ui; then
      if [[ "$(chrome_ui_count resource-id-suffix=negative_button text='No thanks' enabled=true visible=true 2>/dev/null || printf 0)" == "1" ]]; then
        chrome_click_ui_node "Chrome No thanks notification button" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" \
          resource-id-suffix=negative_button text='No thanks' enabled=true clickable=true visible=true || return 1
        break
      fi
      if [[ "$(chrome_ui_count resource-id-suffix=url_bar visible=true 2>/dev/null || printf 0)" == "1" ]]; then
        break
      fi
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  chrome_wait_for_tabbed_ui || return 1
  chrome_log "first run completed without adding an account"
}

chrome_assert_first_run_complete() {
  chrome_start_main || return 1
  local deadline=$((SECONDS + DC_API_CHROME_READY_TIMEOUT_SECONDS))
  local activity first_run_controls toolbar_count
  while (( SECONDS < deadline )); do
    activity="$(chrome_current_activity)"
    if chrome_dump_ui; then
      first_run_controls="$(chrome_ui_count resource-id-suffix=signin_fre_dismiss_button visible=true 2>/dev/null || printf 0)"
      toolbar_count="$(chrome_ui_count resource-id-suffix=url_bar visible=true 2>/dev/null || printf 0)"
      if [[ "$activity" == *"$DC_API_CHROME_FIRST_RUN_ACTIVITY"* ]] || (( first_run_controls > 0 )); then
        chrome_error "Chrome first run recurred on a baseline that must already be prepared"
        return 1
      fi
      if (( toolbar_count == 1 )); then
        chrome_log "first-run validation passed"
        return 0
      fi
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  chrome_error "Chrome first-run validation timed out; activity=$activity"
  return 1
}

chrome_focus_and_type_url() {
  local url="${1:?URL is required}"
  local attempt typed_count
  for ((attempt = 1; attempt <= DC_API_CHROME_OMNIBOX_ATTEMPTS; attempt++)); do
    chrome_adb_shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_L >/dev/null || return 1
    if ! chrome_wait_for_ui_value "focused Chrome omnibox" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" text \
      resource-id-suffix=url_bar focused=true visible=true >/dev/null; then
      continue
    fi
    chrome_adb_shell input text "$url" >/dev/null || return 1
    sleep 1
    if chrome_dump_ui; then
      typed_count="$(chrome_ui_count resource-id-suffix=url_bar text="$url" focused=true visible=true 2>/dev/null || printf 0)"
      if (( typed_count == 1 )); then
        chrome_adb_shell input keyevent KEYCODE_ENTER >/dev/null || return 1
        return 0
      fi
    fi
    chrome_log "omnibox text did not match on attempt $attempt; retrying from a fresh Ctrl+L selection"
  done
  chrome_error "Could not type the exact internal URL after $DC_API_CHROME_OMNIBOX_ATTEMPTS attempts: $url"
  return 1
}

chrome_open_flag_page() {
  chrome_wait_for_tabbed_ui || return 1
  chrome_focus_and_type_url "$DC_API_CHROME_FLAG_URI" || return 1
  chrome_wait_for_ui_value "Digital Credentials issuance flag" "$DC_API_CHROME_FLAG_TIMEOUT_SECONDS" text \
    hint="$DC_API_CHROME_FLAG_HINT" clickable=true visible=true
}

chrome_flag_state() {
  chrome_dump_ui || return 1
  chrome_ui_value text hint="$DC_API_CHROME_FLAG_HINT" clickable=true visible=true
}

chrome_assert_flag_enabled() {
  local state
  state="$(chrome_open_flag_page)" || return 1
  if [[ "$state" != "Enabled" ]]; then
    chrome_error "Chrome issuance flag $DC_API_CHROME_FLAG_ID is $state, expected Enabled"
    return 1
  fi
  if [[ "$(chrome_ui_count content-desc="#$DC_API_CHROME_FLAG_ID" visible=true 2>/dev/null || printf 0)" != "1" ]]; then
    chrome_error "Chrome issuance flag anchor is missing: #$DC_API_CHROME_FLAG_ID"
    return 1
  fi
  chrome_log "issuance flag is enabled and visible after relaunch"
}

chrome_enable_issuance_flag() {
  local state selector_center enabled_center relaunch_center
  state="$(chrome_open_flag_page)" || return 1
  if [[ "$state" == "Enabled" ]]; then
    chrome_log "issuance flag was already enabled"
    return 0
  fi
  if [[ "$state" != "Default" && "$state" != "Disabled" ]]; then
    chrome_error "Unexpected Chrome issuance flag state: $state"
    return 1
  fi

  selector_center="$(chrome_ui_query center _ hint="$DC_API_CHROME_FLAG_HINT" text="$state" clickable=true visible=true)" || return 1
  chrome_tap_center "$selector_center" || return 1
  enabled_center="$(chrome_wait_for_ui_center "native Enabled flag choice" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" \
    resource-id-suffix=text1 text=Enabled enabled=true checkable=true visible=true)" || return 1
  chrome_tap_center "$enabled_center" || return 1

  if [[ "$(chrome_wait_for_ui_value "enabled issuance flag row" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" text \
    hint="$DC_API_CHROME_FLAG_HINT" text=Enabled clickable=true visible=true)" != "Enabled" ]]; then
    chrome_error "Chrome issuance flag did not change to Enabled"
    return 1
  fi
  relaunch_center="$(chrome_wait_for_ui_center "Chrome Relaunch button" "$DC_API_CHROME_READY_TIMEOUT_SECONDS" \
    resource-id-suffix=experiment-restart-button text=Relaunch enabled=true clickable=true visible=true)" || return 1
  chrome_tap_center "$relaunch_center" || return 1
  chrome_wait_for_tabbed_ui || return 1
  chrome_assert_flag_enabled
}

chrome_host_portal_probe() {
  command -v curl >/dev/null 2>&1 || {
    chrome_error "curl is required for the Portal2 host probe"
    return 1
  }
  curl --fail --silent --show-error --location \
    --connect-timeout 15 --max-time "$DC_API_CHROME_PORTAL_TIMEOUT_SECONDS" \
    --output /dev/null "$DC_API_CHROME_PORTAL_URL"
}

chrome_launch_portal() {
  chrome_adb_shell am start -W -a android.intent.action.VIEW \
    -d "$DC_API_CHROME_PORTAL_URL" \
    -n "$DC_API_CHROME_PACKAGE/$DC_API_CHROME_MAIN_ACTIVITY" >/dev/null
}

chrome_assert_portal_rendered() {
  chrome_host_portal_probe || return 1
  chrome_launch_portal || return 1
  local deadline=$((SECONDS + DC_API_CHROME_PORTAL_TIMEOUT_SECONDS))
  local stable_for=0 activity url_count rendered_count error_count
  while (( SECONDS < deadline )); do
    activity="$(chrome_current_activity)"
    if [[ "$activity" == *"$DC_API_CHROME_FIRST_RUN_ACTIVITY"* ]]; then
      chrome_error "Chrome first run blocked Portal2 validation"
      return 1
    fi
    if chrome_dump_ui; then
      url_count="$(chrome_ui_count resource-id-suffix=url_bar text-regex="$DC_API_CHROME_PORTAL_URL_BAR_PATTERN" visible=true 2>/dev/null || printf 0)"
      error_count="$(chrome_ui_count package="$DC_API_CHROME_PACKAGE" text-regex="$DC_API_CHROME_PORTAL_ERROR_TEXT_PATTERN" visible=true 2>/dev/null || printf 0)"
      rendered_count="$(chrome_ui_count package="$DC_API_CHROME_PACKAGE" text-regex="$DC_API_CHROME_PORTAL_RENDER_TEXT_PATTERN" visible=true 2>/dev/null || printf 0)"
      if (( error_count > 0 )); then
        chrome_error "Chrome rendered an error page instead of Portal2"
        return 1
      fi
      if (( url_count == 1 && rendered_count > 0 )) && [[ "$activity" == *"$DC_API_CHROME_TABBED_ACTIVITY"* || "$activity" == *"$DC_API_CHROME_MAIN_ACTIVITY"* ]]; then
        stable_for=$((stable_for + DC_API_CHROME_POLL_SECONDS))
        chrome_log "Portal2 render stableFor=${stable_for}s activity=$activity"
        if (( stable_for >= DC_API_CHROME_PORTAL_STABILITY_SECONDS )); then
          return 0
        fi
      else
        stable_for=0
      fi
    fi
    sleep "$DC_API_CHROME_POLL_SECONDS"
  done
  chrome_error "Portal2 did not render stable page content within ${DC_API_CHROME_PORTAL_TIMEOUT_SECONDS}s; activity=$(chrome_current_activity)"
  return 1
}

chrome_return_home() {
  chrome_adb_shell am force-stop "$DC_API_CHROME_PACKAGE" >/dev/null 2>&1 || true
  chrome_adb_shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME \
    -f 0x10000000 >/dev/null 2>&1 || true
}

chrome_capture_failure_diagnostics() {
  local label="${1:-failure}"
  local timestamp safe_label prefix
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  safe_label="$(tr -cs '[:alnum:]_.-' '-' <<<"$label" | sed 's/^-*//; s/-*$//')"
  prefix="$DC_API_CHROME_ARTIFACT_DIR/${timestamp}-${safe_label:-failure}"
  mkdir -p "$DC_API_CHROME_ARTIFACT_DIR" || return 0

  chrome_adb_cmd exec-out screencap -p >"$prefix-screen.png" 2>/dev/null || true
  chrome_dump_ui || true
  [[ -f "$CHROME_LAST_UI_DUMP" ]] && cp "$CHROME_LAST_UI_DUMP" "$prefix-ui.xml" || true
  chrome_adb_shell dumpsys activity activities >"$prefix-activities.txt" 2>&1 || true
  chrome_adb_shell dumpsys window windows >"$prefix-windows.txt" 2>&1 || true
  chrome_package_details >"$prefix-chrome-package.txt" 2>&1 || true
  chrome_adb_shell dumpsys package "$DC_API_CHROME_PLAY_STORE_PACKAGE" >"$prefix-play-store-package.txt" 2>&1 || true
  chrome_adb_shell dumpsys package com.google.android.gms >"$prefix-gms-package.txt" 2>&1 || true
  chrome_adb_shell dumpsys connectivity >"$prefix-connectivity.txt" 2>&1 || true
  chrome_adb_cmd logcat -d -v threadtime -t 4000 >"$prefix-logcat.txt" 2>&1 || true
  grep -Ei 'chromium|cr_|chrome|renderer|sandbox|credential|identity' "$prefix-logcat.txt" \
    >"$prefix-logcat-focused.txt" 2>/dev/null || true
  {
    echo "portal_url=$DC_API_CHROME_PORTAL_URL"
    echo "current_activity=$(chrome_current_activity || true)"
    echo "chrome_version=$(chrome_version_name || true)"
    echo "chrome_version_code=$(chrome_version_code || true)"
    echo "chrome_apk=$(chrome_apk_path || true)"
    echo "play_store_disabled=$(chrome_play_store_is_disabled && echo true || echo false)"
  } >"$prefix-summary.txt"
  if command -v curl >/dev/null 2>&1; then
    curl --silent --show-error --location --connect-timeout 15 --max-time 30 \
      --dump-header "$prefix-portal-headers.txt" --output /dev/null "$DC_API_CHROME_PORTAL_URL" \
      >"$prefix-portal-curl.txt" 2>&1 || true
  fi
  chrome_log "failure diagnostics written with prefix $prefix"
}

chrome_prepare_impl() {
  chrome_initialize_work_dir || return 1
  chrome_assert_identity || return 1
  chrome_complete_first_run || return 1
  chrome_assert_identity || return 1
  chrome_enable_issuance_flag || return 1
  chrome_assert_identity || return 1
  if [[ "$DC_API_CHROME_VALIDATE_PORTAL_DURING_PREPARE" == "true" ]]; then
    chrome_assert_portal_rendered || return 1
  fi
  chrome_return_home
  chrome_log "preparation passed"
}

chrome_validate_impl() {
  chrome_initialize_work_dir || return 1
  chrome_assert_identity || return 1
  chrome_assert_first_run_complete || return 1
  chrome_assert_flag_enabled || return 1
  chrome_assert_portal_rendered || return 1
  chrome_assert_identity || return 1
  chrome_return_home
  chrome_log "validation passed"
}

prepare_android_dc_api_chrome() {
  if chrome_prepare_impl; then
    return 0
  else
    local status=$?
    chrome_capture_failure_diagnostics prepare
    return "$status"
  fi
}

validate_android_dc_api_chrome() {
  if chrome_validate_impl; then
    return 0
  else
    local status=$?
    chrome_capture_failure_diagnostics validate
    return "$status"
  fi
}

chrome_self_test() {
  local self_test_dir
  self_test_dir="$(mktemp -d "${TMPDIR:-/tmp}/waltid-chrome-self-test.XXXXXX")"
  CHROME_LAST_UI_DUMP="$self_test_dir/ui.xml"
  trap 'rm -rf -- "$self_test_dir"; trap - RETURN' RETURN
  cat >"$CHROME_LAST_UI_DUMP" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<hierarchy rotation="0">
  <node text="DigitalCredentialsCreation" resource-id="web-identity-digital-credentials-creation_name" class="android.view.View" package="com.android.chrome" content-desc="" enabled="true" focused="false" clickable="false" checkable="false" selected="false" bounds="[49,458][498,511]" />
  <node text="" resource-id="" class="android.view.View" package="com.android.chrome" content-desc="#web-identity-digital-credentials-creation" enabled="true" focused="false" clickable="true" checkable="false" selected="false" bounds="[49,577][672,619]" />
  <node text="Enabled" resource-id="" class="android.view.View" package="com.android.chrome" content-desc="" hint="DigitalCredentialsCreation" enabled="true" focused="false" clickable="true" checkable="false" selected="false" bounds="[57,674][454,745]" />
  <node text="portal2.demo.walt.id" resource-id="com.android.chrome:id/url_bar" class="android.widget.EditText" package="com.android.chrome" content-desc="" enabled="true" focused="false" clickable="true" checkable="false" selected="false" bounds="[211,136][691,283]" />
  <node text="Verify credential" resource-id="portal-action" class="android.widget.Button" package="com.android.chrome" content-desc="" enabled="true" focused="false" clickable="true" checkable="false" selected="false" bounds="[100,900][900,1000]" />
  <node text="Default" resource-id="" class="android.view.View" package="com.android.chrome" content-desc="" hint="OtherFlag" enabled="true" focused="false" clickable="true" checkable="false" selected="false" bounds="[57,2337][454,2337]" />
</hierarchy>
XML

  [[ "$(chrome_ui_query value text hint=DigitalCredentialsCreation clickable=true visible=true)" == "Enabled" ]]
  [[ "$(chrome_ui_query center _ hint=DigitalCredentialsCreation text=Enabled visible=true)" == "255 709" ]]
  [[ "$(chrome_ui_count content-desc='#web-identity-digital-credentials-creation' visible=true)" == "1" ]]
  [[ "$(chrome_ui_count resource-id-suffix=url_bar text-regex='^(https://)?portal2[.]demo[.]walt[.]id/?$' visible=true)" == "1" ]]
  [[ "$(chrome_ui_count text-regex='Demo Portal|Issue credential|Verify credential' visible=true)" == "1" ]]
  [[ "$(chrome_ui_count hint=OtherFlag visible=true)" == "0" ]]
  chrome_log "self-test passed"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  case "${1:-}" in
    prepare)
      prepare_android_dc_api_chrome
      ;;
    validate)
      validate_android_dc_api_chrome
      ;;
    --self-test)
      chrome_self_test
      ;;
    *)
      echo "Usage: $0 {prepare|validate|--self-test}" >&2
      exit 2
      ;;
  esac
fi
