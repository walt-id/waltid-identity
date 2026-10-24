import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("prepare-android-dc-api-device.sh")
IMAGE_DIRECTORY = "system-images/android-37.0/google_apis/x86_64/"
IMAGE_XML = """<repository><localPackage>
<revision><major>8</major></revision>
<dependencies><dependency path="emulator"><min-revision>
<major>37</major><minor>2</minor><micro>11</micro>
</min-revision></dependency></dependencies>
</localPackage></repository>"""


class AndroidDcApiBaselineTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.env = {
            key: value for key, value in os.environ.items()
            if not key.startswith(("ANDROID_", "DC_API_", "GMS_VERSION_", "MIN_GMS_VERSION"))
        }
        self.env.update(HOME=str(self.root), ANDROID_HOME=str(self.root / "sdk"), ADB_BIN="false")

    def run_shell(self, command, *args, **env):
        return subprocess.run(
            ["bash", "-euo", "pipefail", "-c", 'source "$1"; shift; ' + command, "test", str(SCRIPT), *map(str, args)],
            env={**self.env, **env}, capture_output=True, text=True, timeout=10,
        )

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
        return target

    def test_package_revision_ignores_dependencies_across_xml_layouts(self):
        cases = [
            (IMAGE_XML, "8"),
            (IMAGE_XML.replace("\n", ""), "8"),
            (IMAGE_XML.replace("<repository>", '<repository xmlns="urn:android:sdk">'), "8"),
            ('<sdk:repository xmlns:sdk="urn:android:sdk"><sdk:localPackage><sdk:revision>'
             '<sdk:major>8</sdk:major></sdk:revision></sdk:localPackage></sdk:repository>', "8"),
            (IMAGE_XML.replace("<major>8</major>", "<major>37</major><minor>2</minor><micro>12</micro>"), "37.2.12"),
            (IMAGE_XML.replace("<major>8</major>", "<major>37</major><minor>0</minor><micro>1</micro>"), "37.0.1"),
            (IMAGE_XML.replace("<major>8</major>", "<major>37</major><micro>1</micro>"), "37.0.1"),
        ]
        for xml, expected in cases:
            with self.subTest(xml=xml, expected=expected):
                result = self.run_shell('package_revision "$1"', self.write("package.xml", xml))
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, expected + "\n")

    def test_package_revision_rejects_unusable_metadata(self):
        cases = [
            None, "<repository>", "<repository/>",
            IMAGE_XML.replace("</localPackage>", "</localPackage><localPackage/>"),
            IMAGE_XML.replace("<revision><major>8</major></revision>", ""),
            IMAGE_XML.replace("</revision>", "</revision><revision><major>8</major></revision>"),
            IMAGE_XML.replace("<major>8</major>", "<minor>8</minor>"),
            IMAGE_XML.replace("<major>8</major>", "<major>8</major><major>9</major>"),
            IMAGE_XML.replace("<major>8</major>", "<major>8</major><preview>1</preview>"),
            *[IMAGE_XML.replace("<major>8</major>", f"<major>{value}</major>")
              for value in ["", "-1", "eight", "٨"]],
        ]
        for xml in cases:
            with self.subTest(xml=xml):
                path = self.root / "missing.xml" if xml is None else self.write("package.xml", xml)
                result = self.run_shell('package_revision "$1"', path)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")

    def prepare_baseline(self):
        self.write("sdk/emulator/package.xml", IMAGE_XML.replace("<major>8</major>", "<major>37</major><minor>2</minor><micro>12</micro>"))
        self.write("sdk/platform-tools/package.xml", IMAGE_XML.replace("<major>8</major>", "<major>37</major><minor>0</minor><micro>1</micro>"))
        self.write("sdk/" + IMAGE_DIRECTORY + "package.xml", IMAGE_XML.replace("\n", ""))
        self.write(".android/avd/dc-api-api37-pixel7-google-apis.avd/config.ini",
                   f"image.sysdir.1={IMAGE_DIRECTORY}\ndisk.dataPartition.size=6G\nhw.device.name=pixel_7\n")
        adb = self.write("adb", '''#!/bin/sh
case "$*" in
  "shell getprop ro.build.version.sdk") echo 37 ;;
  "shell getprop ro.build.fingerprint") echo "${FIXTURE_FINGERPRINT:-fixture-fingerprint}" ;;
  "shell getconf PAGESIZE") echo 4096 ;;
  "shell dumpsys package com.google.android.gms") printf 'versionName=26.11.36\\nversionCode=261136038\\n' ;;
  *) exit 91 ;;
esac
''')
        adb.chmod(0o755)
        self.env["ADB_BIN"] = str(adb)

    def test_manifest_keeps_exact_selected_image_boundary(self):
        self.prepare_baseline()
        result = self.run_shell("android_dc_api_manifest_current_values")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, """schema=3
avd_name=dc-api-api37-pixel7-google-apis
emulator_package_revision=37.2.12
platform_tools_package_revision=37.0.1
system_image_package=system-images;android-37.0;google_apis;x86_64
system_image_revision=8
avd_image_sysdir=system-images/android-37.0/google_apis/x86_64/
avd_data_partition_size=6G
avd_device_profile=pixel_7
device_api=37
device_fingerprint=fixture-fingerprint
device_page_size=4096
gms_version=26.11.36
gms_version_code=261136038
""")
        mismatch = self.run_shell("android_dc_api_manifest_current_values", DC_API_SYSTEM_IMAGE_REVISION="7")
        self.assertNotEqual(mismatch.returncode, 0)
        self.assertIn("outside the selected substrate", mismatch.stderr)
        self.assertEqual(mismatch.stdout, "")

    def test_cached_manifest_rejects_changed_device_identity(self):
        self.prepare_baseline()
        result = self.run_shell("write_android_dc_api_baseline_manifest; assert_android_dc_api_baseline_manifest")
        self.assertEqual(result.returncode, 0, result.stderr)
        changed = self.run_shell("assert_android_dc_api_baseline_manifest", FIXTURE_FINGERPRINT="changed-fingerprint")
        self.assertNotEqual(changed.returncode, 0)
        self.assertIn("does not match its immutable baseline manifest", changed.stderr)


if __name__ == "__main__":
    unittest.main()
