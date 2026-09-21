#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${IOS_DISTRIBUTION_CERTIFICATE_P12:-}" || -z "${IOS_DISTRIBUTION_CERTIFICATE_PASSWORD:-}" ]]; then
  echo "::error::Set IOS_DISTRIBUTION_CERTIFICATE_P12 and IOS_DISTRIBUTION_CERTIFICATE_PASSWORD."
  exit 1
fi

workdir="${RUNNER_TEMP:-$(mktemp -d)}"
keychain_path="${workdir}/app-signing.keychain-db"
keychain_password="${IOS_KEYCHAIN_PASSWORD:-$(openssl rand -base64 24)}"
p12_path="${workdir}/distribution.p12"
profiles_dir="${HOME}/Library/MobileDevice/Provisioning Profiles"
names_file="${workdir}/profile-names.env"

mkdir -p "$profiles_dir"
umask 077
printf '%s' "$IOS_DISTRIBUTION_CERTIFICATE_P12" | base64 --decode > "$p12_path"

security create-keychain -p "$keychain_password" "$keychain_path"
security set-keychain-settings -lut 21600 "$keychain_path"
security unlock-keychain -p "$keychain_password" "$keychain_path"
security import "$p12_path" -k "$keychain_path" -P "$IOS_DISTRIBUTION_CERTIFICATE_PASSWORD" -A \
  -T /usr/bin/codesign -T /usr/bin/security -T /usr/bin/xcodebuild
existing_keychains="$(security list-keychains -d user | tr -d '"')"
security list-keychains -d user -s "$keychain_path" $existing_keychains
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain_path" >/dev/null

install_profile() {
  local env_name="$1"
  local contents="${2:-}"
  local path uuid name
  if [[ -z "$contents" ]]; then
    echo "${env_name}=" >> "$names_file"
    return 0
  fi
  path="${workdir}/${env_name}.mobileprovision"
  printf '%s' "$contents" | base64 --decode > "$path"
  uuid="$(security cms -D -i "$path" | plutil -extract UUID raw -)"
  name="$(security cms -D -i "$path" | plutil -extract Name raw -)"
  cp "$path" "${profiles_dir}/${uuid}.mobileprovision"
  echo "${env_name}=${name}" >> "$names_file"
  echo "Installed provisioning profile ${name}"
}

: > "$names_file"
install_profile APP_PROFILE_NAME "${IOS_PROFILE_APP:-}"
install_profile PROVIDER_PROFILE_NAME "${IOS_PROFILE_PROVIDER:-}"

echo "Imported Apple Distribution certificate."
while IFS= read -r line; do
  echo "$line" >> "$GITHUB_OUTPUT"
done < "$names_file"
