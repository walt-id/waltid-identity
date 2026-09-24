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
xcode_profiles_dir="${HOME}/Library/Developer/Xcode/UserData/Provisioning Profiles"
names_file="${workdir}/profile-names.env"

mkdir -p "$profiles_dir" "$xcode_profiles_dir"
umask 077

python3 - "$p12_path" <<'PY'
import base64
import os
import pathlib
import sys

dest = pathlib.Path(sys.argv[1])
secret = os.environ["IOS_DISTRIBUTION_CERTIFICATE_P12"]
stripped = "".join(secret.split())


def looks_pkcs12(data: bytes) -> bool:
    return len(data) > 4 and data[0] == 0x30


def looks_pem(data: bytes) -> bool:
    return data.lstrip().startswith(b"-----BEGIN")


candidates: list[bytes] = []
if looks_pkcs12(secret.encode("latin1", "ignore")):
    candidates.append(secret.encode("latin1", "ignore"))
if looks_pem(secret.encode()):
    candidates.append(secret.encode())

decoded = None
try:
    decoded = base64.b64decode(stripped, validate=False)
    candidates.append(decoded)
    if looks_pem(decoded):
        pem_once = decoded
    else:
        pem_once = None
except Exception:
    pem_once = None

if pem_once is not None:
    try:
        inner = "".join(pem_once.decode().split())
        candidates.append(base64.b64decode(inner, validate=False))
    except Exception:
        pass

chosen = next((item for item in candidates if looks_pkcs12(item) or looks_pem(item)), None)
if chosen is None:
    raise SystemExit(
        "IOS_DISTRIBUTION_CERTIFICATE_P12 is not a PKCS#12 or PEM after base64 decode."
    )
dest.write_bytes(chosen)
print(f"Decoded signing material ({len(chosen)} bytes).")
PY

openssl_bin="${OPENSSL_BIN:-openssl}"
pem_path="${workdir}/distribution.pem"
compat_p12="${workdir}/distribution-compat.p12"
openssl_err="${workdir}/openssl.err"

material_is_pem=false
if grep -q -- "-----BEGIN" "$p12_path"; then
  material_is_pem=true
  cp "$p12_path" "$pem_path"
fi

if [[ "$material_is_pem" == false ]]; then
  if ! "$openssl_bin" pkcs12 -in "$p12_path" -nodes -passin "pass:${IOS_DISTRIBUTION_CERTIFICATE_PASSWORD}" \
      -out "$pem_path" 2>"$openssl_err"; then
    echo "::error::Could not unlock IOS_DISTRIBUTION_CERTIFICATE_P12 as a PKCS#12. $(tr '\n' ' ' < "$openssl_err")"
    echo "file(1): $(file "$p12_path")"
    exit 1
  fi
fi

if ! grep -q -- "BEGIN" "$pem_path"; then
  echo "::error::Signing material did not contain a certificate or private key."
  exit 1
fi

export_p12() {
  local extra=("$@")
  "$openssl_bin" pkcs12 -export \
    -in "$pem_path" \
    -out "$compat_p12" \
    -name "walt.id CI Apple Distribution" \
    -passout "pass:${IOS_DISTRIBUTION_CERTIFICATE_PASSWORD}" \
    "${extra[@]}"
}

if ! export_p12 -legacy 2>/dev/null; then
  if ! export_p12 -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 2>/dev/null; then
    export_p12
  fi
fi
p12_path="$compat_p12"

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
  printf '%s' "$contents" | tr -d '[:space:]' | base64 --decode > "$path"
  uuid="$(security cms -D -i "$path" | plutil -extract UUID raw -)"
  name="$(security cms -D -i "$path" | plutil -extract Name raw -)"
  cp "$path" "${profiles_dir}/${uuid}.mobileprovision"
  cp "$path" "${xcode_profiles_dir}/${uuid}.mobileprovision"
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
