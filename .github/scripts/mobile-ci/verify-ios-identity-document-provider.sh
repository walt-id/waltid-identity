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
expected_doctypes=()
build_settings=()

while (($#)); do
  case "$1" in
    --project-dir) project_dir="$2"; shift 2 ;;
    --destination) destination="$2"; shift 2 ;;
    --derived-data) derived_data="$2"; shift 2 ;;
    --app-group) expected_app_group="$2"; shift 2 ;;
    --keychain-group-suffix) expected_keychain_suffix="$2"; shift 2 ;;
    --doctype) expected_doctypes+=("$2"); shift 2 ;;
    --build-setting) build_settings+=("$2"); shift 2 ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

[[ -n "$project_dir" && -n "$destination" && -n "$derived_data" ]] || usage
[[ -n "$expected_app_group" && -n "$expected_keychain_suffix" ]] || usage
if ((${#expected_doctypes[@]} == 0)); then
  expected_doctypes=("org.iso.18013.5.1.mDL" "eu.europa.ec.eudi.pid.1")
fi

failures=0
fail() {
  echo "::error::$*" >&2
  failures=$((failures + 1))
}

cd "$project_dir"

# Build the extension on its own first so a provider that cannot compile against IdentityDocumentServices
# or WalletSDK is reported as an extension failure rather than as a host-app failure.
for scheme in IdentityDocumentProvider iosApp; do
  echo "==> Building scheme $scheme in $project_dir"
  xcodebuild build \
    -project iosApp.xcodeproj \
    -scheme "$scheme" \
    -destination "$destination" \
    -derivedDataPath "$derived_data" \
    -quiet \
    "${build_settings[@]}"
done

products="$derived_data/Build/Products/Debug-iphonesimulator"
app="$products/iosApp.app"
appex="$app/Extensions/IdentityDocumentProvider.appex"
intermediates="$derived_data/Build/Intermediates.noindex/iosApp.build/Debug-iphonesimulator"
app_xcent="$intermediates/iosApp.build/iosApp.app-Simulated.xcent"
appex_xcent="$intermediates/IdentityDocumentProvider.build/IdentityDocumentProvider.appex-Simulated.xcent"

# B22: the extension must ship inside the app. An unembedded .appex builds cleanly and is simply never
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

# B13: the shared Keychain group reaches runtime through the Info.plist, because only the Info.plist
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
  App Group:                $app_group
  Default Keychain group:   $app_keychain
  Advertised doctypes:      $(echo "$expected_doctype_list" | tr '\n' ' ')
  Embedded extension:       ${appex#"$products"/}
SUMMARY
