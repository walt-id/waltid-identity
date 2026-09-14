#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
ANDROID_SERIAL="${ANDROID_SERIAL:-}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
DC_API_BASELINE_SCHEMA="${DC_API_BASELINE_SCHEMA:-3}"
DC_API_EMULATOR_PACKAGE_REVISION="${DC_API_EMULATOR_PACKAGE_REVISION:-37.1.11}"
DC_API_PLATFORM_TOOLS_REVISION="${DC_API_PLATFORM_TOOLS_REVISION:-37.0.1}"
DC_API_SYSTEM_IMAGE_PACKAGE="${DC_API_SYSTEM_IMAGE_PACKAGE:-system-images;android-37.0;google_apis;x86_64}"
DC_API_SYSTEM_IMAGE_REVISION="${DC_API_SYSTEM_IMAGE_REVISION:-6.5.11}"
DC_API_AVD_NAME="${DC_API_AVD_NAME:-dc-api-api37-pixel7-google-apis}"
DC_API_AVD_ROOT="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
DC_API_AVD_DIR="$DC_API_AVD_ROOT/${DC_API_AVD_NAME}.avd"
DC_API_BASELINE_MANIFEST="$DC_API_AVD_DIR/waltid-dc-api-baseline.manifest"
MIN_GMS_VERSION="${MIN_GMS_VERSION:-24.31.0}"
DC_API_READY_TIMEOUT_SECONDS="${DC_API_READY_TIMEOUT_SECONDS:-120}"
DC_API_POLL_SECONDS="${DC_API_POLL_SECONDS:-2}"
DC_API_LAUNCHER_READY_TIMEOUT_SECONDS="${DC_API_LAUNCHER_READY_TIMEOUT_SECONDS:-30}"
DC_API_LAUNCHER_STABILITY_SECONDS="${DC_API_LAUNCHER_STABILITY_SECONDS:-6}"

adb_cmd() {
  if [[ -n "$ANDROID_SERIAL" ]]; then
    "$ADB_BIN" -s "$ANDROID_SERIAL" "$@"
  else
    "$ADB_BIN" "$@"
  fi
}

adb_shell() {
  adb_cmd shell "$@" | tr -d '\r'
}

package_revision() {
  local package_xml="$1"
  [[ -f "$package_xml" ]] || return 1
  awk '
    function value(text, tag, pattern, match_text) {
      pattern = "<" tag ">[0-9]+</" tag ">"
      if (!match(text, pattern)) return ""
      match_text = substr(text, RSTART, RLENGTH)
      gsub(/<[^>]+>/, "", match_text)
      return match_text
    }
    {
      text = $0
      if (index(text, "<revision>") > 0) {
        in_revision = 1
        text = substr(text, index(text, "<revision>") + length("<revision>"))
      }
      if (!in_revision) next
      major = major == "" ? value(text, "major") : major
      minor = minor == "" ? value(text, "minor") : minor
      micro = micro == "" ? value(text, "micro") : micro
      if (index(text, "</revision>") > 0) {
        if (major == "") exit 1
        printf "%s", major
        if (minor != "") printf ".%s", minor
        if (micro != "") printf ".%s", micro
        printf "\n"
        exit
      }
    }
  ' "$package_xml"
}

avd_config_file() {
  if [[ -f "$DC_API_AVD_DIR/config.ini" ]]; then
    printf '%s\n' "$DC_API_AVD_DIR/config.ini"
    return 0
  fi

  [[ -d "$DC_API_AVD_ROOT" ]] || return 1
  find "$DC_API_AVD_ROOT" -maxdepth 2 -type f \
    -path "*/${DC_API_AVD_NAME}.avd/config.ini" -print -quit
}

avd_config_value() {
  local key="$1"
  local config_file
  config_file="$(avd_config_file || true)"
  [[ -n "$config_file" ]] || return 1
  sed -n -E "s/^[[:space:]]*${key//./\\.}[[:space:]]*=[[:space:]]*(.*)$/\1/p" "$config_file" |
    tail -n 1
}

avd_system_image_dir() {
  local image_sysdir
  image_sysdir="$(avd_config_value image.sysdir.1 || true)"
  image_sysdir="${image_sysdir%/}"
  [[ -n "$image_sysdir" ]] || return 1
  printf '%s/%s\n' "$ANDROID_HOME/system-images" "${image_sysdir#system-images/}"
}

expected_avd_system_image_sysdir() {
  local package_path="${DC_API_SYSTEM_IMAGE_PACKAGE#system-images;}"
  printf 'system-images/%s/\n' "${package_path//;/\/}"
}

gms_details() {
  adb_shell dumpsys package com.google.android.gms 2>/dev/null || true
}

gms_version_name() {
  gms_details | sed -n 's/.*versionName=\([^[:space:]]*\).*/\1/p' | head -n 1
}

gms_version_code() {
  gms_details | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1
}

version_at_least() {
  local current_major current_minor current_patch minimum_major minimum_minor minimum_patch
  IFS=. read -r current_major current_minor current_patch <<< "$1"
  IFS=. read -r minimum_major minimum_minor minimum_patch <<< "$2"

  [[ "${current_major:-}" =~ ^[0-9]+$ && "${current_minor:-}" =~ ^[0-9]+$ &&
    "${current_patch:-}" =~ ^[0-9]+$ && "${minimum_major:-}" =~ ^[0-9]+$ &&
    "${minimum_minor:-}" =~ ^[0-9]+$ && "${minimum_patch:-}" =~ ^[0-9]+$ ]] || return 1

  if (( current_major != minimum_major )); then
    (( current_major > minimum_major ))
  elif (( current_minor != minimum_minor )); then
    (( current_minor > minimum_minor ))
  else
    (( current_patch >= minimum_patch ))
  fi
}

wait_for_android_dc_api_device() {
  local deadline=$((SECONDS + DC_API_READY_TIMEOUT_SECONDS))
  local boot_completed version code

  echo "DC API device readiness: waiting for boot, package manager, and Google Play services"
  while (( SECONDS < deadline )); do
    if ! adb_cmd get-state >/dev/null 2>&1; then
      sleep "$DC_API_POLL_SECONDS"
      continue
    fi

    boot_completed="$(adb_shell getprop sys.boot_completed 2>/dev/null || true)"
    if [[ "$boot_completed" != "1" ]] || ! adb_shell pm list packages >/dev/null 2>&1; then
      sleep "$DC_API_POLL_SECONDS"
      continue
    fi

    version="$(gms_version_name)"
    code="$(gms_version_code)"
    if [[ -z "$version" || ! "$code" =~ ^[0-9]+$ ]]; then
      sleep "$DC_API_POLL_SECONDS"
      continue
    fi
    if ! version_at_least "$version" "$MIN_GMS_VERSION"; then
      echo "::error::DC API device has Google Play services $version/$code; required >= $MIN_GMS_VERSION" >&2
      return 1
    fi

    GMS_VERSION_BEFORE_TESTS="$version"
    GMS_VERSION_CODE_BEFORE_TESTS="$code"
    export GMS_VERSION_BEFORE_TESTS GMS_VERSION_CODE_BEFORE_TESTS
    echo "DC API device readiness: GMS=$version versionCode=$code"
    return 0
  done

  echo "::error::DC API device did not reach boot/package-manager/GMS readiness within ${DC_API_READY_TIMEOUT_SECONDS}s" >&2
  log_android_dc_api_device_identity
  return 1
}

android_dc_api_manifest_current_values() {
  local emulator_revision platform_tools_revision image_revision image_sysdir
  local data_partition device_profile device_api device_fingerprint device_page_size
  local gms_version gms_code
  emulator_revision="$(package_revision "$ANDROID_HOME/emulator/package.xml" || true)"
  platform_tools_revision="$(package_revision "$ANDROID_HOME/platform-tools/package.xml" || true)"
  image_revision="$(package_revision "$(avd_system_image_dir 2>/dev/null || true)/package.xml" || true)"
  image_sysdir="$(avd_config_value image.sysdir.1 || true)"
  data_partition="$(avd_config_value disk.dataPartition.size || true)"
  device_profile="$(avd_config_value hw.device.name || true)"
  device_api="$(adb_shell getprop ro.build.version.sdk 2>/dev/null || true)"
  device_fingerprint="$(adb_shell getprop ro.build.fingerprint 2>/dev/null || true)"
  device_page_size="$(adb_shell getconf PAGESIZE 2>/dev/null || true)"
  gms_version="$(gms_version_name)"
  gms_code="$(gms_version_code)"

  if [[ "$emulator_revision" != "$DC_API_EMULATOR_PACKAGE_REVISION" ||
    "$platform_tools_revision" != "$DC_API_PLATFORM_TOOLS_REVISION" ||
    "$image_revision" != "$DC_API_SYSTEM_IMAGE_REVISION" ||
    "$image_sysdir" != "$(expected_avd_system_image_sysdir)" ||
    "$data_partition" != "6G" || "$device_profile" != "pixel_7" ||
    "$device_api" != "37" || -z "$device_fingerprint" ||
    ! "$device_page_size" =~ ^[0-9]+$ || ! "$gms_code" =~ ^[0-9]+$
  ]]; then
    echo "::error::DC API baseline identity is incomplete or outside the selected substrate" >&2
    echo "host: emulator=$emulator_revision platformTools=$platform_tools_revision image=$image_revision" >&2
    echo "avd: profile=$device_profile imageSysdir=$image_sysdir dataPartition=$data_partition" >&2
    echo "device: api=$device_api fingerprint=${device_fingerprint:-<missing>} pageSize=${device_page_size:-<missing>}" >&2
    echo "gms: version=${gms_version:-<missing>} code=${gms_code:-<missing>}" >&2
    return 1
  fi
  if ! version_at_least "$gms_version" "$MIN_GMS_VERSION"; then
    echo "::error::DC API baseline has GMS $gms_version/$gms_code; required >= $MIN_GMS_VERSION" >&2
    return 1
  fi

  printf 'schema=%s\n' "$DC_API_BASELINE_SCHEMA"
  printf 'avd_name=%s\n' "$DC_API_AVD_NAME"
  printf 'emulator_package_revision=%s\n' "$emulator_revision"
  printf 'platform_tools_package_revision=%s\n' "$platform_tools_revision"
  printf 'system_image_package=%s\n' "$DC_API_SYSTEM_IMAGE_PACKAGE"
  printf 'system_image_revision=%s\n' "$image_revision"
  printf 'avd_image_sysdir=%s\n' "$image_sysdir"
  printf 'avd_data_partition_size=%s\n' "$data_partition"
  printf 'avd_device_profile=%s\n' "$device_profile"
  printf 'device_api=%s\n' "$device_api"
  printf 'device_fingerprint=%s\n' "$device_fingerprint"
  printf 'device_page_size=%s\n' "$device_page_size"
  printf 'gms_version=%s\n' "$gms_version"
  printf 'gms_version_code=%s\n' "$gms_code"
}

write_android_dc_api_baseline_manifest() {
  [[ -d "$DC_API_AVD_DIR" ]] || {
    echo "::error::Cannot write DC API baseline; AVD directory is missing: $DC_API_AVD_DIR" >&2
    return 1
  }
  local temporary_manifest="$DC_API_BASELINE_MANIFEST.tmp.$$"
  android_dc_api_manifest_current_values > "$temporary_manifest"
  mv "$temporary_manifest" "$DC_API_BASELINE_MANIFEST"
  echo "DC API baseline manifest: wrote $DC_API_BASELINE_MANIFEST"
  cat "$DC_API_BASELINE_MANIFEST"
}

assert_android_dc_api_baseline_manifest() {
  [[ -f "$DC_API_BASELINE_MANIFEST" ]] || {
    echo "::error::DC API baseline manifest is missing: $DC_API_BASELINE_MANIFEST" >&2
    return 1
  }

  local expected current
  expected="$(cat "$DC_API_BASELINE_MANIFEST")"
  current="$(android_dc_api_manifest_current_values)"
  if [[ "$expected" != "$current" ]]; then
    echo "::error::Restored DC API AVD does not match its immutable baseline manifest" >&2
    diff -u <(printf '%s\n' "$expected") <(printf '%s\n' "$current") || true
    return 1
  fi
  echo "DC API baseline manifest: exact host/AVD/device/GMS identity match"
}

log_android_dc_api_device_identity() {
  echo "DC API device: state=$(adb_cmd get-state 2>/dev/null || true) boot=$(adb_shell getprop sys.boot_completed 2>/dev/null || true)"
  echo "DC API device: api=$(adb_shell getprop ro.build.version.sdk 2>/dev/null || true) fingerprint=$(adb_shell getprop ro.build.fingerprint 2>/dev/null || true)"
  echo "DC API device: GMS=$(gms_version_name)/$(gms_version_code)"
}

current_foreground_package() {
  adb_shell dumpsys activity activities 2>/dev/null |
    sed -n -E 's/.*(mResumedActivity|topResumedActivity|ResumedActivity)[=:].* u0 ([^/[:space:]]+)\/.*/\2/p' |
    head -n 1
}

home_activity() {
  adb_shell cmd package resolve-activity --brief \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME 2>/dev/null |
    awk '/\// { print $NF }' |
    tail -n 1
}

start_android_dc_api_home() {
  local output
  output="$(adb_shell am start -W \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME \
    -f 0x10000000 2>&1 || true)"
  if grep -q 'Status: ok' <<< "$output"; then
    return 0
  fi
  echo "DC API system UI health: HOME start did not report Status: ok" >&2
  printf '%s\n' "$output" >&2
  return 1
}

assert_android_dc_api_launcher_health() {
  local deadline=$((SECONDS + DC_API_LAUNCHER_READY_TIMEOUT_SECONDS))
  local healthy_for_seconds=0
  local resolved_activity=""
  local home_package=""
  local launcher_pid=""
  local foreground=""
  local launcher_state=""
  local home_start_attempted=false
  local recovery_attempted=false

  echo "DC API system UI health: waiting for stable HOME launcher"
  while (( SECONDS < deadline )); do
    resolved_activity="$(home_activity || true)"
    home_package="${resolved_activity%%/*}"
    if [[ -z "$resolved_activity" || -z "$home_package" || "$home_package" == "$resolved_activity" ]]; then
      healthy_for_seconds=0
      sleep "$DC_API_POLL_SECONDS"
      continue
    fi

    launcher_pid="$(adb_shell pidof "$home_package" 2>/dev/null || true)"
    foreground="$(current_foreground_package || true)"
    launcher_state="$(adb_shell dumpsys activity processes "$home_package" 2>/dev/null || true)"

    if grep -Eqi '(^|[[:space:]])m?(notResponding|crashing)=true' <<< "$launcher_state"; then
      healthy_for_seconds=0
      if [[ "$recovery_attempted" != "true" ]]; then
        echo "DC API system UI health: launcher reports ANR/crash; restarting HOME once"
        adb_shell am force-stop "$home_package" >/dev/null 2>&1 || true
        start_android_dc_api_home || true
        recovery_attempted=true
        home_start_attempted=true
      fi
      sleep "$DC_API_POLL_SECONDS"
      continue
    fi

    if [[ -n "$launcher_pid" && "$foreground" == "$home_package" && -n "$launcher_state" ]]; then
      healthy_for_seconds=$((healthy_for_seconds + DC_API_POLL_SECONDS))
      echo "DC API system UI health: package=$home_package pid=$launcher_pid foreground=$foreground healthyFor=${healthy_for_seconds}s"
      if (( healthy_for_seconds >= DC_API_LAUNCHER_STABILITY_SECONDS )); then
        return 0
      fi
    else
      healthy_for_seconds=0
      if [[ "$home_start_attempted" != "true" ]]; then
        echo "DC API system UI health: HOME is not ready; starting it once"
        start_android_dc_api_home || true
        home_start_attempted=true
      fi
    fi
    sleep "$DC_API_POLL_SECONDS"
  done

  echo "::error::DC API launcher did not remain healthy for ${DC_API_LAUNCHER_STABILITY_SECONDS}s within ${DC_API_LAUNCHER_READY_TIMEOUT_SECONDS}s" >&2
  echo "resolvedHome=${resolved_activity:-<missing>}" >&2
  echo "launcherPid=${launcher_pid:-<missing>} foreground=${foreground:-<missing>}" >&2
  printf '%s\n' "$launcher_state" >&2
  log_android_dc_api_launcher_diagnostic
  return 1
}

log_android_dc_api_launcher_diagnostic() {
  local home_activity home_package foreground state
  home_activity="$(adb_shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | awk '/\// { print $NF }' | tail -n 1 || true)"
  home_package="${home_activity%%/*}"
  foreground="$(adb_shell dumpsys activity activities 2>/dev/null | sed -n -E 's/.*(mResumedActivity|topResumedActivity|ResumedActivity)[=:].* u0 ([^/[:space:]]+)\/.*/\2/p' | head -n 1 || true)"
  state="$(adb_shell dumpsys activity processes "$home_package" 2>/dev/null || true)"
  echo "DC API launcher diagnostic: activity=${home_activity:-<missing>} pid=$(adb_shell pidof "$home_package" 2>/dev/null || true) foreground=${foreground:-<missing>}"
  if grep -Eqi '(^|[[:space:]])m?notResponding=true|(^|[[:space:]])m?crashing=true' <<< "$state"; then
    echo "DC API launcher diagnostic: launcher reports ANR/crash"
  fi
}

prepare_android_dc_api_device() {
  wait_for_android_dc_api_device
  android_dc_api_manifest_current_values >/dev/null
  log_android_dc_api_device_identity
}

validate_android_dc_api_device() {
  wait_for_android_dc_api_device
  assert_android_dc_api_baseline_manifest
  assert_android_dc_api_launcher_health
  log_android_dc_api_device_identity
}

assert_android_dc_api_device_unchanged() {
  local after_version after_code
  after_version="$(gms_version_name)"
  after_code="$(gms_version_code)"
  echo "DC API device postflight: GMS=$after_version versionCode=$after_code"
  if [[ "${GMS_VERSION_BEFORE_TESTS:-}" != "$after_version" ||
    "${GMS_VERSION_CODE_BEFORE_TESTS:-}" != "$after_code"
  ]]; then
    echo "::error::Google Play services changed during the DC API suite: before=${GMS_VERSION_BEFORE_TESTS:-<unset>}/${GMS_VERSION_CODE_BEFORE_TESTS:-<unset>} after=$after_version/$after_code" >&2
    return 1
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  prepare_android_dc_api_device
fi
