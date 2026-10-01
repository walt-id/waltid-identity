#!/usr/bin/env bash
set -euo pipefail

# android-emulator-runner installs the latest emulator before each launch. Restore
# the qualified binary afterwards so both cache creation and reuse use the same host.
# Archive and SHA-256: https://developer.android.com/studio/emulator_archive
emulator_revision="37.1.11"
emulator_build="15917651"
emulator_sha256="95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266"
sdk_root="${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
  echo "::error::The DC API emulator archive requires Linux x86_64" >&2
  exit 1
fi

temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
archive="$temporary_dir/emulator.zip"
curl --fail --location --retry 3 --silent --show-error \
  --output "$archive" \
  "https://dl.google.com/android/repository/emulator-linux_x64-${emulator_build}.zip"
archive_checksum="$(sha256sum "$archive")"
if [[ "${archive_checksum%% *}" != "$emulator_sha256" ]]; then
  echo "::error::Downloaded emulator archive checksum does not match the qualified DC API baseline" >&2
  exit 1
fi
unzip -q "$archive" -d "$temporary_dir"

# Google's archived packages omit package.xml. Keep the SDK manager's metadata,
# but set its revision to the verified archive's revision, as the archive guide requires.
python3 - "$sdk_root/emulator/package.xml" "$temporary_dir/emulator" "$emulator_revision" <<'PY'
import pathlib
import sys
import xml.dom.minidom as DOM

metadata_path, extracted_path, expected_revision = sys.argv[1:]
extracted = pathlib.Path(extracted_path)
properties = dict(
    line.split("=", 1)
    for line in (extracted / "source.properties").read_text().splitlines()
    if "=" in line and not line.startswith("#")
)
if properties.get("Pkg.Revision") != expected_revision:
    raise SystemExit("Downloaded emulator revision does not match the qualified DC API baseline")

document = DOM.parse(metadata_path)
package = next(
    (node for node in document.getElementsByTagName("localPackage") if node.getAttribute("path") == "emulator"),
    None,
)
revision = next((node for node in package.childNodes if node.nodeName == "revision"), None) if package else None
if revision is None:
    raise SystemExit("Installed emulator package metadata has no revision")
while revision.firstChild:
    revision.removeChild(revision.firstChild)
for tag, value in zip(("major", "minor", "micro"), expected_revision.split(".")):
    element = document.createElement(tag)
    element.appendChild(document.createTextNode(value))
    revision.appendChild(element)
(extracted / "package.xml").write_bytes(document.toxml(encoding="UTF-8"))
PY

emulator_version="$("$temporary_dir/emulator/emulator" -version)"
if [[ "$emulator_version" != *"Android emulator version ${emulator_revision}.0 "* ]]; then
  echo "::error::Downloaded emulator binary does not report revision $emulator_revision" >&2
  exit 1
fi

rm -rf "$sdk_root/emulator"
mv "$temporary_dir/emulator" "$sdk_root/emulator"
printf '%s\n' "$emulator_version"
