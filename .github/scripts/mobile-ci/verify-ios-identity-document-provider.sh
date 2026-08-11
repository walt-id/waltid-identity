#!/usr/bin/env bash
# Builds one iOS demo together with its IdentityDocument provider extension and asserts, on the built
# products, the invariants that decide whether iOS treats app and extension as one wallet.
#
# Source .entitlements files are not enough: the interesting values contain $(AppIdentifierPrefix) and
# INFOPLIST_KEY_ settings, both of which are resolved by the build and can silently resolve to
# nothing. A mismatch here does not fail a build - it fails a presentation on a device.
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: verify-ios-identity-document-provider.sh \
  --project-dir <dir containing iosApp.xcodeproj> \
  --destination <xcodebuild destination> \
  --derived-data <path> \
  --app-group <group.identifier> \
  --keychain-group-suffix <suffix without team prefix> \
  --app-bundle-id <host bundle identifier> \
  --appex-bundle-id <provider bundle identifier> \
  --development-team <Team ID> \
  [--doctype <doctype>]... \
  [--build-setting KEY=VALUE]...
USAGE
  exit 2
}

project_dir=""
destination=""
derived_data=""
expected_app_group=""
expected_keychain_suffix=""
expected_app_bundle_id=""
expected_appex_bundle_id=""
expected_development_team=""
expected_doctypes=()
build_settings=()

while (($#)); do
  case "$1" in
    --project-dir) project_dir="$2"; shift 2 ;;
    --destination) destination="$2"; shift 2 ;;
    --derived-data) derived_data="$2"; shift 2 ;;
    --app-group) expected_app_group="$2"; shift 2 ;;
    --keychain-group-suffix) expected_keychain_suffix="$2"; shift 2 ;;
    --app-bundle-id) expected_app_bundle_id="$2"; shift 2 ;;
    --appex-bundle-id) expected_appex_bundle_id="$2"; shift 2 ;;
    --development-team) expected_development_team="$2"; shift 2 ;;
    --doctype) expected_doctypes+=("$2"); shift 2 ;;
    --build-setting) build_settings+=("$2"); shift 2 ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

[[ -n "$project_dir" && -n "$destination" && -n "$derived_data" ]] || usage
[[ -n "$expected_app_group" && -n "$expected_keychain_suffix" ]] || usage
[[ -n "$expected_app_bundle_id" && -n "$expected_appex_bundle_id" ]] || usage
[[ -n "$expected_development_team" ]] || usage
if ((${#expected_doctypes[@]} == 0)); then
  expected_doctypes=("org.iso.18013.5.1.mDL" "eu.europa.ec.eudi.pid.1")
fi

failures=0
fail() {
  echo "::error::$*" >&2
  failures=$((failures + 1))
}

cd "$project_dir"

# The team is asserted on resolved build settings rather than on project.pbxproj, because it may be
# inherited from the project level and a grep cannot tell which configurations it actually reaches.
#
# This proves the Xcode configuration resolves the Walt team. It cannot prove that Apple issued a
# matching physical-device profile, nor what AppIdentifierPrefix that profile carries - both are
# settled by inspecting a device-signed product.
resolved_build_setting() {
  xcodebuild -showBuildSettings \
    -project iosApp.xcodeproj \
    -target "$1" \
    -configuration "$2" \
    -destination "$destination" \
    ${build_settings[@]+"${build_settings[@]}"} 2>/dev/null |
    awk -v key="$3" '$1 == key && $2 == "=" { $1=""; $2=""; sub(/^ +/, ""); print; exit }'
}

for target in iosApp IdentityDocumentProvider; do
  for configuration in Debug Release; do
    resolved_team="$(resolved_build_setting "$target" "$configuration" DEVELOPMENT_TEAM)"
    [[ "$resolved_team" == "$expected_development_team" ]] ||
      fail "$target/$configuration resolves DEVELOPMENT_TEAM '$resolved_team', expected '$expected_development_team'"
    resolved_style="$(resolved_build_setting "$target" "$configuration" CODE_SIGN_STYLE)"
    [[ "$resolved_style" == "Automatic" ]] ||
      fail "$target/$configuration resolves CODE_SIGN_STYLE '$resolved_style', expected Automatic"
  done
done
((failures == 0)) || exit 1

# The iosApp target depends on the IdentityDocumentProvider target and embeds its .appex, so this one
# build compiles the provider too.
#
# The build settings expand through the `[@]+` form because macOS ships bash 3.2, where `"${array[@]}"`
# on an empty array is an unbound-variable error under `set -u` - the Compose demo passes none.
echo "==> Building scheme iosApp in $project_dir"
xcodebuild build \
  -project iosApp.xcodeproj \
  -scheme iosApp \
  -destination "$destination" \
  -derivedDataPath "$derived_data" \
  -quiet \
  ${build_settings[@]+"${build_settings[@]}"}

products="$derived_data/Build/Products/Debug-iphonesimulator"
app="$products/iosApp.app"
appex="$app/Extensions/IdentityDocumentProvider.appex"
intermediates="$derived_data/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator"
app_xcent="$intermediates/iosApp.build/iosApp.app-Simulated.xcent"
appex_xcent="$intermediates/IdentityDocumentProvider.build/IdentityDocumentProvider.appex-Simulated.xcent"

# The extension must ship inside the app. An unembedded .appex builds cleanly and is simply never
# offered by iOS.
[[ -d "$appex" ]] || fail "IdentityDocumentProvider.appex is not embedded at $appex"

# codesign -d --entitlements reports an empty dict for simulator builds, so read the entitlements the
# build actually applied instead.
for xcent in "$app_xcent" "$appex_xcent"; do
  [[ -f "$xcent" ]] || fail "Missing built entitlements: $xcent"
done
((failures == 0)) || exit 1

# plutil key paths are dot-separated, so reverse-DNS entitlement keys are unreachable with -extract.
# Read the plists directly instead; this also copes with the binary Info.plists inside built bundles.
plist_value() {
  python3 - "$1" "$2" <<'PY'
import plistlib
import sys

path, key = sys.argv[1], sys.argv[2]
with open(path, "rb") as handle:
    value = plistlib.load(handle)
for component in key.split("/"):
    if isinstance(value, dict):
        value = value.get(component)
    elif isinstance(value, list) and component.isdigit() and int(component) < len(value):
        value = value[int(component)]
    else:
        value = None
    if value is None:
        break
if isinstance(value, list):
    print("\n".join(str(item) for item in value))
elif value is not None:
    print(value)
PY
}

app_group_of() { plist_value "$1" "com.apple.security.application-groups/0"; }
first_keychain_group_of() { plist_value "$1" "keychain-access-groups/0"; }
doctypes_of() {
  plist_value "$1" "com.apple.developer.identity-document-services.document-provider.mobile-document-types"
}

# Read from the built Info.plists, not from project.pbxproj: PRODUCT_BUNDLE_IDENTIFIER is what the
# provisioning profile has to match, and only the built bundle shows what it expanded to.
app_bundle_id="$(plist_value "$app/Info.plist" CFBundleIdentifier)"
appex_bundle_id="$(plist_value "$appex/Info.plist" CFBundleIdentifier)"
[[ "$app_bundle_id" == "$expected_app_bundle_id" ]] ||
  fail "App bundle identifier is '$app_bundle_id', expected '$expected_app_bundle_id'"
[[ "$appex_bundle_id" == "$expected_appex_bundle_id" ]] ||
  fail "Extension bundle identifier is '$appex_bundle_id', expected '$expected_appex_bundle_id'"
# Apple only offers an extension whose identifier is prefixed by its host's.
[[ "$appex_bundle_id" == "$app_bundle_id."* ]] ||
  fail "Extension identifier '$appex_bundle_id' is not nested under the host's '$app_bundle_id'"

app_group="$(app_group_of "$app_xcent")"
appex_group="$(app_group_of "$appex_xcent")"
[[ "$app_group" == "$expected_app_group" ]] ||
  fail "App carries App Group '$app_group', expected '$expected_app_group'"
[[ "$appex_group" == "$app_group" ]] ||
  fail "Extension App Group '$appex_group' differs from the app's '$app_group'"

# The first keychain-access-groups entry is the process default group, and Signum's Keychain provider
# does not set kSecAttrAccessGroup, so the signing key lands in whichever group is listed first.
app_keychain="$(first_keychain_group_of "$app_xcent")"
appex_keychain="$(first_keychain_group_of "$appex_xcent")"
[[ "$appex_keychain" == "$app_keychain" ]] ||
  fail "Extension default Keychain group '$appex_keychain' differs from the app's '$app_keychain'"
# The Team ID prefix is environment-specific, so match on the suffix only.
[[ "$app_keychain" == *".$expected_keychain_suffix" ]] ||
  fail "Default Keychain group '$app_keychain' does not end in '.$expected_keychain_suffix'"

expected_doctype_list="$(printf '%s\n' "${expected_doctypes[@]}" | sort)"
for xcent in "$app_xcent" "$appex_xcent"; do
  actual="$(doctypes_of "$xcent" | sort)"
  [[ -n "$actual" ]] ||
    fail "$(basename "$xcent") is missing the Mobile Document Provider capability"
  [[ "$actual" == "$expected_doctype_list" ]] ||
    fail "$(basename "$xcent") advertises doctypes [$(echo "$actual" | tr '\n' ' ')], expected [$(echo "$expected_doctype_list" | tr '\n' ' ')]"
done

# The shared Keychain group reaches runtime through the Info.plist, because only the Info.plist
# processor expands $(AppIdentifierPrefix). As an INFOPLIST_KEY_ build setting it resolves to the bare
# suffix and the key never reaches the built plist at all.
app_info_group="$(plist_value "$app/Info.plist" WALTKeychainAccessGroup)"
appex_info_group="$(plist_value "$appex/Info.plist" WALTKeychainAccessGroup)"
[[ "$app_info_group" == "$app_keychain" ]] ||
  fail "App Info.plist WALTKeychainAccessGroup '$app_info_group' does not match its entitlement '$app_keychain'"
[[ "$appex_info_group" == "$app_info_group" ]] ||
  fail "Extension Info.plist WALTKeychainAccessGroup '$appex_info_group' differs from the app's '$app_info_group'"

extension_point="$(plist_value "$appex/Info.plist" EXAppExtensionAttributes/EXExtensionPointIdentifier)"
[[ "$extension_point" == "com.apple.identity-document-services.document-provider-ui" ]] ||
  fail "Extension point identifier is '$extension_point', expected com.apple.identity-document-services.document-provider-ui"

if ((failures > 0)); then
  echo "::error::$failures identity-document provider configuration check(s) failed in $project_dir" >&2
  exit 1
fi

cat <<SUMMARY
Identity document provider configuration verified in $project_dir
  Host bundle id:           $app_bundle_id
  Provider bundle id:       $appex_bundle_id
  Development team:         $expected_development_team
  App Group:                $app_group
  Default Keychain group:   $app_keychain
  Advertised doctypes:      $(echo "$expected_doctype_list" | tr '\n' ' ')
  Embedded extension:       ${appex#"$products"/}
SUMMARY
