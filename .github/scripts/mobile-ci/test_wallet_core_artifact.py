import importlib.util
from pathlib import Path
import plistlib
import shutil
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("artifact", Path(__file__).with_name("wallet-core-artifact.py"))
artifact = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifact)


class ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.framework = self.root / "release/WalletCore.xcframework"
        self.framework.mkdir(parents=True)
        libraries = []
        for variant, identifier in [("", "ios-arm64"), ("simulator", "ios-arm64-simulator")]:
            folder = self.framework / identifier / "WalletCore.framework"
            folder.mkdir(parents=True)
            (folder / "WalletCore").write_bytes(b"test framework contents")
            libraries.append({"LibraryIdentifier": identifier, "LibraryPath": "WalletCore.framework",
                              "SupportedPlatform": "ios", "SupportedPlatformVariant": variant,
                              "SupportedArchitectures": ["arm64"]})
        (self.framework / "Info.plist").write_bytes(plistlib.dumps({"AvailableLibraries": libraries}))
        self.directory = self.root / "artifact"
        self.identity = {"identity_sha": "a" * 40, "xcode": "Xcode test", "configuration": "release"}
        self.patches = [patch.object(artifact, "FRAMEWORK", self.framework),
                        patch.object(artifact, "identity", return_value=self.identity)]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)

    def run_operation(self, operation):
        with patch("sys.argv", ["artifact", operation, "--artifact-dir", str(self.directory)]):
            artifact.main()

    def test_pack_restore_and_verify_exact_contents(self):
        before = artifact.inventory(self.framework)
        self.run_operation("pack")
        shutil.rmtree(self.framework)
        self.run_operation("restore")
        self.assertEqual(before, artifact.inventory(self.framework))
        self.run_operation("verify")

    def test_reject_wrong_source_or_toolchain(self):
        self.run_operation("pack")
        for key, value in [("identity_sha", "b" * 40), ("xcode", "another Xcode")]:
            with self.subTest(key=key), patch.object(artifact, "identity", return_value={**self.identity, key: value}):
                with self.assertRaisesRegex(ValueError, "mismatch"):
                    self.run_operation("verify")

    def test_reject_changed_framework(self):
        self.run_operation("pack")
        (self.framework / "ios-arm64/WalletCore.framework/WalletCore").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "contents differ"):
            self.run_operation("verify")

    def test_reject_corrupt_archive(self):
        self.run_operation("pack")
        (self.directory / artifact.ARCHIVE).write_bytes(b"corrupt")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            self.run_operation("restore")

    def test_reject_incomplete_platform_coverage(self):
        plist = self.framework / "Info.plist"
        data = plistlib.loads(plist.read_bytes())
        data["AvailableLibraries"].pop()
        plist.write_bytes(plistlib.dumps(data))
        with self.assertRaisesRegex(ValueError, "both device and simulator"):
            self.run_operation("pack")

    def test_reject_missing_binary(self):
        (self.framework / "ios-arm64/WalletCore.framework/WalletCore").unlink()
        with self.assertRaisesRegex(ValueError, "Missing framework binary"):
            self.run_operation("pack")


if __name__ == "__main__":
    unittest.main()
