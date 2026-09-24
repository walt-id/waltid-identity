#!/usr/bin/env bash
set -euo pipefail

project_dir="${PROJECT_DIR:?PROJECT_DIR is required}"
scheme="${SCHEME:-iosApp}"
configuration="${CONFIGURATION:-Release}"
version_name="${VERSION_NAME:?VERSION_NAME is required}"
version_code="${VERSION_CODE:?VERSION_CODE is required}"
archive_path="${ARCHIVE_PATH:?ARCHIVE_PATH is required}"
export_path="${EXPORT_PATH:?EXPORT_PATH is required}"
team_id="${DEVELOPMENT_TEAM:-9BB78CFW7Y}"
app_bundle_id="${APP_BUNDLE_ID:?APP_BUNDLE_ID is required}"
provider_bundle_id="${PROVIDER_BUNDLE_ID:?PROVIDER_BUNDLE_ID is required}"
app_profile_name="${APP_PROFILE_NAME:-}"
provider_profile_name="${PROVIDER_PROFILE_NAME:-}"

cd "$project_dir"
mkdir -p "$(dirname "$archive_path")" "$export_path"

auth_args=()
if [[ -n "${APP_STORE_CONNECT_KEY_PATH:-}" && -n "${APP_STORE_CONNECT_KEY_ID:-}" && -n "${APP_STORE_CONNECT_ISSUER_ID:-}" ]]; then
  auth_args=(
    -allowProvisioningUpdates
    -authenticationKeyPath "$APP_STORE_CONNECT_KEY_PATH"
    -authenticationKeyID "$APP_STORE_CONNECT_KEY_ID"
    -authenticationKeyIssuerID "$APP_STORE_CONNECT_ISSUER_ID"
  )
fi

sign_args=(
  DEVELOPMENT_TEAM="$team_id"
  MARKETING_VERSION="$version_name"
  CURRENT_PROJECT_VERSION="$version_code"
)
export_plist="${export_path}/ExportOptions.plist"

if [[ -n "$app_profile_name" && -n "$provider_profile_name" ]]; then
  python3 - "iosApp.xcodeproj/project.pbxproj" "$app_bundle_id" "$app_profile_name" "$provider_bundle_id" "$provider_profile_name" "$team_id" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
assignments = {
    sys.argv[2]: sys.argv[3],
    sys.argv[4]: sys.argv[5],
}
team_id = sys.argv[6]


def pbx_token(value: str) -> str:
    if re.fullmatch(r"[A-Za-z0-9._/]+", value):
        return value
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def upsert(settings: str, key: str, value: str) -> str:
    replacement = f"{pbx_token(key)} = {pbx_token(value)};"
    quoted_key = re.escape(key)
    if re.search(rf"^[ \t]*(\"{quoted_key}\"|{quoted_key}) = ", settings, flags=re.M):
        return re.sub(
            rf"^([ \t]*)(\"{quoted_key}\"|{quoted_key}) = [^;]*;",
            rf"\1{replacement}",
            settings,
            count=1,
            flags=re.M,
        )
    return re.sub(r"(buildSettings = \{)", rf"\1\n\t\t\t\t{replacement}", settings, count=1)


def patch_settings(settings: str) -> str:
    match = re.search(r"PRODUCT_BUNDLE_IDENTIFIER = ([^;]+);", settings)
    if not match:
        return settings
    bundle_id = match.group(1).strip().strip('"')
    profile = assignments.get(bundle_id)
    if not profile:
        return settings
    settings = upsert(settings, "CODE_SIGN_STYLE", "Manual")
    settings = upsert(settings, "CODE_SIGN_IDENTITY", "Apple Distribution")
    settings = upsert(settings, "DEVELOPMENT_TEAM", team_id)
    settings = upsert(settings, "PROVISIONING_PROFILE_SPECIFIER", profile)
    return settings


updated = re.sub(
    r"buildSettings = \{[^{}]*\}",
    lambda m: patch_settings(m.group(0)),
    text,
    flags=re.S,
)
if updated == text:
    raise SystemExit("Could not apply per-target provisioning profile specifiers.")
path.write_text(updated, encoding="utf-8")
print(f"Set PROVISIONING_PROFILE_SPECIFIER for {', '.join(assignments)}")
PY
  sign_args+=(
    CODE_SIGN_STYLE=Manual
    CODE_SIGN_IDENTITY="Apple Distribution"
  )
  cat > "$export_plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>app-store-connect</string>
	<key>teamID</key>
	<string>${team_id}</string>
	<key>signingStyle</key>
	<string>manual</string>
	<key>signingCertificate</key>
	<string>Apple Distribution</string>
	<key>provisioningProfiles</key>
	<dict>
		<key>${app_bundle_id}</key>
		<string>${app_profile_name}</string>
		<key>${provider_bundle_id}</key>
		<string>${provider_profile_name}</string>
	</dict>
</dict>
</plist>
EOF
else
  sign_args+=(CODE_SIGN_STYLE=Automatic)
  cat > "$export_plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>app-store-connect</string>
	<key>teamID</key>
	<string>${team_id}</string>
	<key>signingStyle</key>
	<string>automatic</string>
</dict>
</plist>
EOF
fi

xcodebuild archive \
  -project iosApp.xcodeproj \
  -scheme "$scheme" \
  -configuration "$configuration" \
  -destination 'generic/platform=iOS' \
  -archivePath "$archive_path" \
  "${auth_args[@]}" \
  "${sign_args[@]}"

xcodebuild -exportArchive \
  -archivePath "$archive_path" \
  -exportPath "$export_path" \
  -exportOptionsPlist "$export_plist" \
  "${auth_args[@]}"

ipa="$(find "$export_path" -name '*.ipa' | head -n 1)"
if [[ -z "$ipa" ]]; then
  echo "::error::No IPA produced"
  exit 1
fi
echo "ipa_path=${ipa}" >> "$GITHUB_OUTPUT"
