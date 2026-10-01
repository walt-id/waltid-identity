import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.dom.minidom as DOM
import zipfile


INSTALLER = Path(__file__).with_name("install-android-dc-api-emulator.sh")
OFFICIAL_SHA256 = "95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266"
METADATA = """<r:repository xmlns:r="urn:repository" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:g="urn:generic">
<localPackage path="emulator"><type-details xsi:type="g:genericDetailsType"/>
<revision><major>37</major><minor>2</minor><micro>12</micro></revision>
<dependencies><dependency path="platform-tools"><min-revision><major>30</major></min-revision></dependency></dependencies>
</localPackage></r:repository>"""


class InstallDcApiEmulatorTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.sdk = self.root / "SDK with spaces"
        self.emulator = self.sdk / "emulator"
        self.emulator.mkdir(parents=True)
        (self.emulator / "package.xml").write_text(METADATA)
        (self.emulator / "old-binary").touch()
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.write_command("uname", 'case "$1" in -s) echo "${TEST_OS:-Linux}";; -m) echo x86_64;; esac')
        self.write_command("curl", 'while [ "$1" != "--output" ]; do shift; done; cp "$TEST_ARCHIVE" "$2"')
        self.archive = self.root / "fixture.zip"

    def write_command(self, name, body):
        command = self.bin / name
        command.write_text("#!/bin/sh\nset -eu\n" + body + "\n")
        command.chmod(0o755)

    def run_installer(self, revision="37.1.11", binary_revision="37.1.11", corrupt=False, **extra_env):
        # Substitute only the trusted hash for a tiny offline archive. All installer
        # parsing, extraction, executable validation and SDK replacement remain real.
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.writestr("emulator/source.properties", f"Pkg.Revision={revision}\n")
            binary = zipfile.ZipInfo("emulator/emulator")
            binary.external_attr = 0o100755 << 16
            archive.writestr(binary, f'#!/bin/sh\necho "Android emulator version {binary_revision}.0 (build_id 15917651)"\n')
        checksum = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        script = self.root / "installer.sh"
        script.write_text(INSTALLER.read_text().replace(OFFICIAL_SHA256, checksum))
        if corrupt:
            with self.archive.open("ab") as archive:
                archive.write(b"corrupted")
        env = {**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
               "ANDROID_HOME": str(self.sdk), "TEST_ARCHIVE": str(self.archive), **extra_env}
        return subprocess.run(["bash", str(script)], env=env, capture_output=True, text=True, timeout=10)

    def assert_sdk_preserved(self):
        self.assertTrue((self.emulator / "old-binary").exists())
        self.assertEqual((self.emulator / "package.xml").read_text(), METADATA)

    def test_installs_archive_and_preserves_namespace_and_dependency_metadata(self):
        result = self.run_installer()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.emulator / "old-binary").exists())
        document = DOM.parse(str(self.emulator / "package.xml"))
        revision = document.getElementsByTagName("revision")[0]
        self.assertEqual([node.firstChild.data for node in revision.childNodes], ["37", "1", "11"])
        self.assertEqual(document.documentElement.getAttribute("xmlns:g"), "urn:generic")
        self.assertEqual(document.getElementsByTagName("type-details")[0].getAttribute("xsi:type"), "g:genericDetailsType")
        self.assertEqual(document.getElementsByTagName("min-revision")[0].firstChild.firstChild.data, "30")

    def test_checksum_mismatch_keeps_existing_sdk(self):
        result = self.run_installer(corrupt=True)
        self.assertNotEqual(result.returncode, 0)
        self.assert_sdk_preserved()

    def test_wrong_archive_revision_keeps_existing_sdk(self):
        result = self.run_installer(revision="37.2.12")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Downloaded emulator revision", result.stderr)
        self.assert_sdk_preserved()

    def test_binary_revision_mismatch_keeps_existing_sdk(self):
        result = self.run_installer(binary_revision="37.2.12")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Downloaded emulator binary", result.stderr)
        self.assert_sdk_preserved()

    def test_missing_package_metadata_keeps_existing_binary(self):
        (self.emulator / "package.xml").unlink()
        result = self.run_installer()
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.emulator / "old-binary").exists())

    def test_unsupported_host_keeps_existing_sdk(self):
        result = self.run_installer(TEST_OS="Darwin")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("requires Linux x86_64", result.stderr)
        self.assert_sdk_preserved()


if __name__ == "__main__":
    unittest.main()
