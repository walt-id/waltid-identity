#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${ADB_BIN:-adb}"
ANDROID_SERIAL="${ANDROID_SERIAL:-}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
DC_API_BASELINE_SCHEMA="${DC_API_BASELINE_SCHEMA:-2}"
DC_API_EMULATOR_PACKAGE_REVISION="${DC_API_EMULATOR_PACKAGE_REVISION:-37.1.11}"
DC_API_PLATFORM_TOOLS_REVISION="${DC_API_PLATFORM_TOOLS_REVISION:-37.0.1}"
DC_API_SYSTEM_IMAGE_PACKAGE="${DC_API_SYSTEM_IMAGE_PACKAGE:-system-images;android-37.0;google_apis_playstore_ps16k;x86_64}"
DC_API_SYSTEM_IMAGE_REVISION="${DC_API_SYSTEM_IMAGE_REVISION:-6.5.11}"
DC_API_AVD_NAME="${DC_API_AVD_NAME:-dc-api-api37-pixel7-playstore}"
DC_API_AVD_ROOT="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
DC_API_AVD_DIR="$DC_API_AVD_ROOT/${DC_API_AVD_NAME}.avd"
DC_API_BASELINE_MANIFEST="$DC_API_AVD_DIR/waltid-dc-api-baseline.manifest"
DC_API_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
MIN_GMS_VERSION="${MIN_GMS_VERSION:-26.29.32}"
GMS_READY_TIMEOUT_SECONDS="${GMS_READY_TIMEOUT_SECONDS:-300}"
GMS_STABILITY_SECONDS="${GMS_STABILITY_SECONDS:-15}"
GMS_POLL_SECONDS="${GMS_POLL_SECONDS:-2}"
GMS_VALIDATION_TIMEOUT_SECONDS="${GMS_VALIDATION_TIMEOUT_SECONDS:-60}"
LAUNCHER_READY_TIMEOUT_SECONDS="${LAUNCHER_READY_TIMEOUT_SECONDS:-30}"
LAUNCHER_STABILITY_SECONDS="${LAUNCHER_STABILITY_SECONDS:-6}"

package_revision() {
  local package_xml="$1"
  [[ -f "$package_xml" ]] || return 1
  awk '
    function tag_value(text, tag, pattern, match_text) {
      pattern = "<" tag ">[0-9]+</" tag ">"
      if (match(text, pattern)) {
        match_text = substr(text, RSTART, RLENGTH)
        gsub(/<[^>]+>/, "", match_text)
        return match_text
      }
      return ""
    }
    {
      text = $0
      if (index(text, "<revision>") > 0) {
        in_revision = 1
        text = substr(text, index(text, "<revision>") + length("<revision>"))
      }
      if (!in_revision) next
      major = major == "" ? tag_value(text, "major") : major
      minor = minor == "" ? tag_value(text, "minor") : minor
      micro = micro == "" ? tag_value(text, "micro") : micro
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

script_sha256() {
  local script="$1"
  sha256sum "$script" | awk '{print $1}'
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

  awk -F= -v expected_key="$key" '
    {
      actual_key = $1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", actual_key)
    }
    actual_key == expected_key {
      value = $0
      sub(/^[^=]*=/, "", value)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      print value
      found = 1
    }
    END {
      if (!found) exit 1
    }
  ' "$config_file" | tail -n 1
}

avd_system_image_dir() {
  local image_sysdir
  image_sysdir="$(avd_config_value image.sysdir.1 || true)"
  image_sysdir="${image_sysdir%/}"
  [[ -n "$image_sysdir" ]] || return 1
  printf '%s/%s\n' "${ANDROID_HOME}/system-images" "${image_sysdir#system-images/}"
}

expected_avd_system_image_sysdir() {
  local package_path="${DC_API_SYSTEM_IMAGE_PACKAGE#system-images;}"
  package_path="${package_path//;/\/}"
  printf 'system-images/%s/\n' "$package_path"
}

android_device_property() {
  adb_shell getprop "$1"
}

android_device_page_size() {
  adb_shell getconf PAGESIZE 2>/dev/null || true
}

gms_apk_path() {
  adb_shell pm path com.google.android.gms 2>/dev/null |
    sed -n 's/^package:\(.*\)$/\1/p' |
    head -n 1
}

android_emulator_binary_version() {
  local version
  version="$(${ANDROID_HOME}/emulator/emulator -version 2>&1 | sed -n -E 's/^Android emulator version ([^[:space:]]+).*/\1/p' | head -n 1 || true)"
  printf '%s\n' "${version:-<unavailable>}"
}

android_platform_tools_version() {
  local version
  version="$(${ANDROID_HOME}/platform-tools/adb version 2>&1 | sed -n -E 's/^Android Debug Bridge version ([^[:space:]]+).*/\1/p' | head -n 1 || true)"
  printf '%s\n' "${version:-<unavailable>}"
}

log_android_dc_api_host_identity() {
  local emulator_package_xml platform_tools_package_xml system_image_package_xml
  local config_file
  emulator_package_xml="${ANDROID_HOME}/emulator/package.xml"
  platform_tools_package_xml="${ANDROID_HOME}/platform-tools/package.xml"
  system_image_package_xml="$(avd_system_image_dir 2>/dev/null || true)/package.xml"
  config_file="$(avd_config_file || true)"

  echo "DC API host identity: androidHome=${ANDROID_HOME:-<unset>}"
  echo "DC API host identity: emulatorBinary=$(android_emulator_binary_version) emulatorPackageRevision=$(package_revision "$emulator_package_xml" || true)"
  echo "DC API host identity: adbBinary=$(android_platform_tools_version) platformToolsPackageRevision=$(package_revision "$platform_tools_package_xml" || true)"
  echo "DC API host identity: systemImagePackage=${DC_API_SYSTEM_IMAGE_PACKAGE} systemImageRevision=$(package_revision "$system_image_package_xml" || true)"
  echo "DC API host identity: avdDir=$DC_API_AVD_DIR configFile=${config_file:-<missing>} configBytes=$(wc -c < "${config_file:-/dev/null}" 2>/dev/null || printf 0) configImageSysdir=$(avd_config_value image.sysdir.1 || true) ram=$(avd_config_value hw.ramSize || true) cpuCores=$(avd_config_value hw.cpu.ncore || true)"
  if [[ -n "$config_file" ]]; then
    echo "DC API host identity: config key lines"
    sed -n -E '/^[[:space:]]*(image\.sysdir\.1|hw\.ramSize|hw\.cpu\.ncore|hw\.gpu\.mode)[[:space:]]*=/p' "$config_file" || true
  fi
}

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

gms_details() {
  adb_shell dumpsys package com.google.android.gms 2>/dev/null || true
}

gms_version_name() {
  gms_details | sed -n 's/.*versionName=\([^[:space:]]*\).*/\1/p' | head -n 1
}

gms_version_code() {
  gms_details | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1
}

gms_pid() {
  adb_shell pidof com.google.android.gms 2>/dev/null |
    tr '\n' ' ' |
    xargs || true
}

version_at_least() {
  local current_major current_minor current_patch
  local minimum_major minimum_minor minimum_patch
  IFS=. read -r current_major current_minor current_patch <<< "$1"
  IFS=. read -r minimum_major minimum_minor minimum_patch <<< "$2"

  [[ "${current_major:-}" =~ ^[0-9]+$ ]] || return 1
  [[ "${current_minor:-}" =~ ^[0-9]+$ ]] || return 1
  [[ "${current_patch:-}" =~ ^[0-9]+$ ]] || return 1
  [[ "${minimum_major:-}" =~ ^[0-9]+$ ]] || return 1
  [[ "${minimum_minor:-}" =~ ^[0-9]+$ ]] || return 1
  [[ "${minimum_patch:-}" =~ ^[0-9]+$ ]] || return 1

  if (( current_major != minimum_major )); then
    (( current_major > minimum_major ))
  elif (( current_minor != minimum_minor )); then
    (( current_minor > minimum_minor ))
  else
    (( current_patch >= minimum_patch ))
  fi
}

gms_version_identity() {
  local version code
  version="$(gms_version_name)"
  code="$(gms_version_code)"
  printf '%s|%s\n' "$version" "$code"
}

android_dc_api_manifest_value() {
  local key="$1"
  [[ -f "$DC_API_BASELINE_MANIFEST" ]] || return 1
  sed -n -E "s/^${key}=(.*)$/\1/p" "$DC_API_BASELINE_MANIFEST" | tail -n 1
}

android_dc_api_manifest_current_values() {
  local image_dir image_package_xml
  image_dir="$(avd_system_image_dir || true)"
  image_package_xml="${image_dir:+$image_dir/package.xml}"

  printf 'schema=%s\n' "$DC_API_BASELINE_SCHEMA"
  printf 'avd_name=%s\n' "$DC_API_AVD_NAME"
  printf 'emulator_binary_version=%s\n' "$(android_emulator_binary_version)"
  printf 'emulator_package_revision=%s\n' "$(package_revision "${ANDROID_HOME}/emulator/package.xml" || true)"
  printf 'platform_tools_version=%s\n' "$(android_platform_tools_version)"
  printf 'platform_tools_package_revision=%s\n' "$(package_revision "${ANDROID_HOME}/platform-tools/package.xml" || true)"
  printf 'system_image_package=%s\n' "$DC_API_SYSTEM_IMAGE_PACKAGE"
  printf 'system_image_revision=%s\n' "$(package_revision "$image_package_xml" || true)"
  printf 'avd_image_sysdir=%s\n' "$(avd_config_value image.sysdir.1 || true)"
  printf 'avd_ram=%s\n' "$(avd_config_value hw.ramSize || true)"
  printf 'avd_cpu_cores=%s\n' "$(avd_config_value hw.cpu.ncore || true)"
  printf 'avd_gpu_mode=%s\n' "$(avd_config_value hw.gpu.mode || true)"
  printf 'device_api=%s\n' "$(android_device_property ro.build.version.sdk)"
  printf 'device_release=%s\n' "$(android_device_property ro.build.version.release)"
  printf 'device_build_id=%s\n' "$(android_device_property ro.build.id)"
  printf 'device_incremental=%s\n' "$(android_device_property ro.build.version.incremental)"
  printf 'device_fingerprint=%s\n' "$(android_device_property ro.build.fingerprint)"
  printf 'device_page_size=%s\n' "$(android_device_page_size)"
  printf 'gms_version=%s\n' "$(gms_version_name)"
  printf 'gms_version_code=%s\n' "$(gms_version_code)"
  printf 'gms_apk_path=%s\n' "$(gms_apk_path)"
  printf 'script_configure_sha256=%s\n' "$(script_sha256 "$DC_API_SCRIPT_DIR/configure-android-device-phase.sh")"
  printf 'script_device_sha256=%s\n' "$(script_sha256 "$DC_API_SCRIPT_DIR/prepare-android-dc-api-device.sh")"
  printf 'script_avd_sha256=%s\n' "$(script_sha256 "$DC_API_SCRIPT_DIR/prepare-android-dc-api-avd.sh")"
}

write_android_dc_api_baseline_manifest() {
  [[ -d "$DC_API_AVD_DIR" ]] || {
    echo "::error::Cannot write DC API baseline: AVD directory is missing: $DC_API_AVD_DIR" >&2
    return 1
  }

  log_android_dc_api_host_identity
  local temporary_manifest="$DC_API_BASELINE_MANIFEST.tmp.$$"
  android_dc_api_manifest_current_values > "$temporary_manifest"
  mv "$temporary_manifest" "$DC_API_BASELINE_MANIFEST"
  echo "DC API baseline manifest: wrote $DC_API_BASELINE_MANIFEST"
  cat "$DC_API_BASELINE_MANIFEST"
}

assert_android_dc_api_baseline_manifest() {
  if [[ ! -f "$DC_API_BASELINE_MANIFEST" ]]; then
    echo "::error::DC API AVD baseline manifest is missing: $DC_API_BASELINE_MANIFEST" >&2
    log_android_dc_api_host_identity
    log_android_dc_api_device_identity
    return 1
  fi

  local expected actual key mismatch=0 current_values
  current_values="$(android_dc_api_manifest_current_values)"
  while IFS='=' read -r key expected; do
    [[ -n "$key" ]] || continue
    actual="$(sed -n -E "s/^${key}=(.*)$/\1/p" <<< "$current_values" | tail -n 1)"
    if [[ "$expected" != "$actual" ]]; then
      echo "::error::DC API AVD baseline mismatch: ${key} expected='${expected}' actual='${actual}'" >&2
      mismatch=1
    fi
  done < "$DC_API_BASELINE_MANIFEST"

  local declared_emulator declared_platform declared_image declared_image_sysdir
  declared_emulator="$(sed -n -E 's/^emulator_package_revision=(.*)$/\1/p' <<< "$current_values")"
  declared_platform="$(sed -n -E 's/^platform_tools_package_revision=(.*)$/\1/p' <<< "$current_values")"
  declared_image="$(sed -n -E 's/^system_image_revision=(.*)$/\1/p' <<< "$current_values")"
  declared_image_sysdir="$(sed -n -E 's/^avd_image_sysdir=(.*)$/\1/p' <<< "$current_values")"
  if [[ "$declared_emulator" != "$DC_API_EMULATOR_PACKAGE_REVISION" ||
    "$declared_platform" != "$DC_API_PLATFORM_TOOLS_REVISION" ||
    "$declared_image" != "$DC_API_SYSTEM_IMAGE_REVISION"
  ]]; then
    echo "::error::DC API AVD baseline uses an unexpected SDK substrate: emulator=$declared_emulator platform-tools=$declared_platform system-image=$declared_image" >&2
    echo "Expected: emulator=$DC_API_EMULATOR_PACKAGE_REVISION platform-tools=$DC_API_PLATFORM_TOOLS_REVISION system-image=$DC_API_SYSTEM_IMAGE_REVISION" >&2
    mismatch=1
  fi
  if [[ "$declared_image_sysdir" != "$(expected_avd_system_image_sysdir)" ]]; then
    echo "::error::DC API AVD baseline uses an unexpected system image path: actual=$declared_image_sysdir expected=$(expected_avd_system_image_sysdir)" >&2
    mismatch=1
  fi

  if (( mismatch != 0 )); then
    echo "DC API baseline manifest:" >&2
    cat "$DC_API_BASELINE_MANIFEST" >&2
    log_android_dc_api_host_identity
    log_android_dc_api_device_identity
    return 1
  fi
  echo "DC API baseline manifest: exact substrate/device/GMS identity match"
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

log_android_dc_api_device_identity() {
  local sdk release build incremental fingerprint page_size avd emulator_version home foreground gms_state
  log_android_dc_api_host_identity
  sdk="$(adb_shell getprop ro.build.version.sdk || true)"
  release="$(adb_shell getprop ro.build.version.release || true)"
  build="$(adb_shell getprop ro.build.id || true)"
  incremental="$(adb_shell getprop ro.build.version.incremental || true)"
  fingerprint="$(adb_shell getprop ro.build.fingerprint || true)"
  page_size="$(android_device_page_size)"
  avd="$(adb_cmd emu avd name 2>/dev/null | tr -d '\r' | head -n 1 || true)"
  emulator_version="$(android_emulator_binary_version)"
  home="$(home_activity || true)"
  foreground="$(current_foreground_package || true)"
  gms_state="$(adb_shell cmd activity get-uid-state com.google.android.gms 2>/dev/null || true)"

  echo "DC API device identity: serial=${ANDROID_SERIAL:-<default>} avd=${avd:-<unknown>}"
  echo "DC API device identity: adbState=$(adb_cmd get-state 2>/dev/null || true) bootCompleted=$(adb_shell getprop sys.boot_completed || true)"
  echo "DC API device identity: api=$sdk release=$release build=$build incremental=$incremental pageSize=$page_size"
  echo "DC API device identity: fingerprint=$fingerprint"
  echo "DC API device identity: emulator=$emulator_version emulatorPackageRevision=$(package_revision "${ANDROID_HOME}/emulator/package.xml" || true) platformTools=$(android_platform_tools_version) systemImageRevision=$(package_revision "$(avd_system_image_dir 2>/dev/null || true)/package.xml" || true)"
  echo "DC API device identity: home=$home foreground=$foreground"
  echo "DC API device identity: GMS version=$(gms_version_name || true) versionCode=$(gms_version_code || true) apk=$(gms_apk_path || true) pid=$(gms_pid || true) uidState=$gms_state"
}

assert_android_dc_api_launcher_health() {
  local failure_annotation="${1:-error}"
  local deadline=$((SECONDS + LAUNCHER_READY_TIMEOUT_SECONDS))
  local healthy_for_seconds=0
  local resolved_activity=""
  local home_package=""
  local launcher_pid=""
  local foreground=""
  local start_output=""
  local launcher_state=""
  local launcher_started=false

  echo "DC API system UI health: waiting for stable HOME launcher"
  while (( SECONDS < deadline )); do
    if [[ "$launcher_started" != "true" ]]; then
      resolved_activity="$(home_activity || true)"
      home_package="${resolved_activity%%/*}"
      if [[ -z "$resolved_activity" || -z "$home_package" || "$home_package" == "$resolved_activity" ]]; then
        healthy_for_seconds=0
        echo "DC API system UI health: HOME activity not resolved yet"
        sleep "$GMS_POLL_SECONDS"
        continue
      fi

      start_output="$(adb_shell am start -W \
        -a android.intent.action.MAIN \
        -c android.intent.category.HOME \
        -f 0x10000000 2>&1 || true)"
      if ! grep -q 'Status: ok' <<< "$start_output"; then
        healthy_for_seconds=0
        echo "DC API system UI health: HOME start did not report Status: ok"
        echo "$start_output"
        sleep "$GMS_POLL_SECONDS"
        continue
      fi

      launcher_started=true
      echo "DC API system UI health: HOME start succeeded; observing launcher passively"
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    launcher_pid="$(adb_shell pidof "$home_package" || true)"
    foreground="$(current_foreground_package || true)"
    launcher_state="$(adb_shell dumpsys activity processes "$home_package" 2>/dev/null || true)"
    if [[ -z "$launcher_pid" || "$foreground" != "$home_package" || -z "$launcher_state" ]]; then
      healthy_for_seconds=0
      echo "DC API system UI health: waiting for launcher package=$home_package pid=${launcher_pid:-<missing>} foreground=${foreground:-<missing>} processState=$([[ -n "$launcher_state" ]] && echo present || echo missing)"
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    if grep -Eqi '(^|[[:space:]])m?notResponding=true|(^|[[:space:]])m?crashing=true' <<< "$launcher_state"; then
      healthy_for_seconds=0
      echo "DC API system UI health: launcher process reports ANR/crash; waiting for recovery"
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    healthy_for_seconds=$((healthy_for_seconds + GMS_POLL_SECONDS))
    echo "DC API system UI health: package=$home_package pid=$launcher_pid foreground=$foreground healthyFor=${healthy_for_seconds}s"
    if (( healthy_for_seconds >= LAUNCHER_STABILITY_SECONDS )); then
      return 0
    fi
    sleep "$GMS_POLL_SECONDS"
  done

  echo "::${failure_annotation}::DC API launcher did not remain healthy for ${LAUNCHER_STABILITY_SECONDS}s within ${LAUNCHER_READY_TIMEOUT_SECONDS}s" >&2
  echo "resolvedHome=$resolved_activity"
  echo "homePackage=$home_package"
  echo "launcherPid=${launcher_pid:-<missing>}"
  echo "foreground=${foreground:-<missing>}"
  echo "launcher process state:"
  printf '%s\n' "$launcher_state"
  log_android_dc_api_device_identity
  return 1
}

log_android_dc_api_launcher_diagnostic() {
  local resolved_activity home_package launcher_pid foreground launcher_state
  resolved_activity="$(home_activity || true)"
  home_package="${resolved_activity%%/*}"
  launcher_pid=""
  launcher_state=""
  if [[ -n "$home_package" && "$home_package" != "$resolved_activity" ]]; then
    launcher_pid="$(adb_shell pidof "$home_package" 2>/dev/null || true)"
    launcher_state="$(adb_shell dumpsys activity processes "$home_package" 2>/dev/null || true)"
  fi
  foreground="$(current_foreground_package || true)"

  echo "DC API postflight launcher diagnostic: resolvedHome=${resolved_activity:-<missing>} package=${home_package:-<missing>} pid=${launcher_pid:-<missing>} foreground=${foreground:-<missing>}"
  echo "DC API postflight launcher process state:"
  printf '%s\n' "${launcher_state:-<missing>}"
}

record_gms_baseline() {
  local identity="$1"
  local code="$2"
  GMS_VERSION_BEFORE_TESTS="${identity%%|*}"
  GMS_VERSION_CODE_BEFORE_TESTS="$code"
  export GMS_VERSION_BEFORE_TESTS GMS_VERSION_CODE_BEFORE_TESTS
  echo "DC API device preflight: Google Play services stabilized at $GMS_VERSION_BEFORE_TESTS (versionCode=$GMS_VERSION_CODE_BEFORE_TESTS)"
}

prepare_android_dc_api_device() {
  local require_launcher_health="${1:-true}"
  adb_cmd wait-for-device

  local deadline=$((SECONDS + GMS_READY_TIMEOUT_SECONDS))
  local last_logged_version=""
  local stable_identity=""
  local stable_for_seconds=0

  echo "DC API device preflight: waiting for Android boot and package manager"
  while (( SECONDS < deadline )); do
    local boot_completed
    boot_completed="$(adb_shell getprop sys.boot_completed 2>/dev/null || true)"
    if [[ "$boot_completed" != "1" ]]; then
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    if ! adb_shell pm list packages >/dev/null 2>&1; then
      echo "DC API device preflight: package manager is not ready yet"
      sleep "$GMS_POLL_SECONDS"
      continue
    fi
    if ! adb_shell pm path com.google.android.gms >/dev/null 2>&1; then
      echo "DC API device preflight: package manager has no Google Play services yet"
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    local version code
    version="$(gms_version_name)"
    code="$(gms_version_code)"
    if [[ "$version" != "$last_logged_version" ]]; then
      echo "DC API device preflight: Google Play services version=$version versionCode=$code"
      last_logged_version="$version"
    fi

    if ! version_at_least "$version" "$MIN_GMS_VERSION"; then
      echo "DC API device preflight: waiting for Google Play services >= $MIN_GMS_VERSION (found $version)"
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    local identity
    identity="$(gms_version_identity)"
    if [[ "$identity" == *"|" ]]; then
      stable_identity=""
      stable_for_seconds=0
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    if [[ "$identity" == "$stable_identity" ]]; then
      stable_for_seconds=$((stable_for_seconds + GMS_POLL_SECONDS))
    else
      stable_identity="$identity"
      stable_for_seconds=0
    fi

    echo "DC API device preflight: GMS identity=$identity stableFor=${stable_for_seconds}s"
    if (( stable_for_seconds >= GMS_STABILITY_SECONDS )); then
      record_gms_baseline "$identity" "$code"
      log_android_dc_api_device_identity
      if [[ "$require_launcher_health" == "true" ]]; then
        assert_android_dc_api_launcher_health
      fi
      return 0
    fi
    sleep "$GMS_POLL_SECONDS"
  done

  local final_version final_code
  final_version="$(gms_version_name || true)"
  final_code="$(gms_version_code || true)"
  echo "::error::DC API environment did not stabilize: GMS=$final_version versionCode=$final_code required>=$MIN_GMS_VERSION" >&2
  return 1
}

validate_android_dc_api_device() {
  adb_cmd wait-for-device
  adb_cmd get-state >/dev/null

  local deadline=$((SECONDS + GMS_VALIDATION_TIMEOUT_SECONDS))
  local stable_identity=""
  local stable_for_seconds=0

  echo "DC API device validation: validating restored Android/GMS baseline"
  while (( SECONDS < deadline )); do
    local boot_completed
    boot_completed="$(adb_shell getprop sys.boot_completed 2>/dev/null || true)"
    if [[ "$boot_completed" != "1" ]]; then
      sleep "$GMS_POLL_SECONDS"
      continue
    fi

    if ! adb_shell pm list packages >/dev/null 2>&1; then
      sleep "$GMS_POLL_SECONDS"
      continue
    fi
    if ! adb_shell pm path com.google.android.gms >/dev/null 2>&1; then
      echo "::error::Restored DC API AVD has no Google Play services package" >&2
      log_android_dc_api_device_identity
      return 1
    fi

    local version code pid identity
    version="$(gms_version_name)"
    code="$(gms_version_code)"
    pid="$(gms_pid)"
    echo "DC API device validation: GMS version=$version versionCode=$code pid=${pid:-<missing>}"
    if [[ -z "$version" || -z "$code" ]]; then
      sleep "$GMS_POLL_SECONDS"
      continue
    fi
    if ! version_at_least "$version" "$MIN_GMS_VERSION"; then
      echo "::error::Restored DC API AVD has GMS $version/$code, required >= $MIN_GMS_VERSION" >&2
      log_android_dc_api_device_identity
      return 1
    fi
    identity="$(gms_version_identity)"
    if [[ "$identity" == *"|" ]]; then
      stable_identity=""
      stable_for_seconds=0
      sleep "$GMS_POLL_SECONDS"
      continue
    fi
    if [[ "$identity" == "$stable_identity" ]]; then
      stable_for_seconds=$((stable_for_seconds + GMS_POLL_SECONDS))
    else
      stable_identity="$identity"
      stable_for_seconds=0
    fi
    echo "DC API device validation: GMS identity=$identity stableFor=${stable_for_seconds}s"
    if (( stable_for_seconds >= GMS_STABILITY_SECONDS )); then
      assert_android_dc_api_baseline_manifest
      record_gms_baseline "$identity" "$code"
      log_android_dc_api_device_identity
      assert_android_dc_api_launcher_health
      return 0
    fi
    sleep "$GMS_POLL_SECONDS"
  done

  echo "::error::Restored DC API AVD did not reach a stable healthy baseline within ${GMS_VALIDATION_TIMEOUT_SECONDS}s" >&2
  log_android_dc_api_device_identity
  return 1
}

assert_android_dc_api_device_unchanged() {
  local after_version after_code after_pid
  after_version="$(gms_version_name || true)"
  after_code="$(gms_version_code || true)"
  after_pid="$(gms_pid || true)"
  echo "DC API device postflight: GMS version=$after_version versionCode=$after_code pid=${after_pid:-<missing>}"
  echo "GMS_VERSION_BEFORE_TESTS=${GMS_VERSION_BEFORE_TESTS:-<unset>}"
  echo "GMS_VERSION_CODE_BEFORE_TESTS=${GMS_VERSION_CODE_BEFORE_TESTS:-<unset>}"
  echo "GMS_VERSION_AFTER_TESTS=$after_version"
  echo "GMS_VERSION_CODE_AFTER_TESTS=$after_code"

  if [[ "${GMS_VERSION_BEFORE_TESTS:-}" != "$after_version" ||
    "${GMS_VERSION_CODE_BEFORE_TESTS:-}" != "$after_code"
  ]]; then
    echo "::error::Google Play services changed during the DC API test suite: before=${GMS_VERSION_BEFORE_TESTS:-<unset>}/${GMS_VERSION_CODE_BEFORE_TESTS:-<unset>} after=$after_version/$after_code" >&2
    return 1
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  prepare_android_dc_api_device
fi
