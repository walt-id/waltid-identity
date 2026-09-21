"""Exercise bridge preparation with real artifact checks and a stub native compiler."""
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parent
RELEASE = "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework"


class BridgePreparationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.scripts = self.root / ".github/scripts/mobile-ci"
        self.scripts.mkdir(parents=True)
        # Run the actual preparation section, stopping before the Xcode test workspace is created.
        preparation = (SCRIPTS / "run-ios-wallet-sdk-tests.sh").read_text().split("# A workspace containing", 1)[0]
        self.runner = self.scripts / "run-ios-wallet-sdk-tests.sh"
        self.runner.write_text(preparation)
        shutil.copy2(SCRIPTS / "wallet-core-artifact.py", self.scripts)
        tools = self.root / "bin"
        tools.mkdir()
        self.env = {**os.environ, "PATH": str(tools) + os.pathsep + os.environ["PATH"]}
        for name, response in [("git", "a" * 40), ("xcodebuild", "Xcode test")]:
            command = tools / name
            command.write_text(f"#!/bin/sh\nprintf '%s\\n' '{response}'\n")
            command.chmod(0o755)
        gradle = self.root / "gradlew"
        gradle.write_text(f"#!{sys.executable}\n" + '''import json, os, pathlib, sys
root = pathlib.Path(__file__).parent
with (root / 'gradle-calls.jsonl').open('a') as stream:
    stream.write(json.dumps(sys.argv[1:]) + '\\n')
if os.environ.get('TAMPER_RELEASE'):
    next((root / 'waltid-libraries').rglob('WalletCore.framework/WalletCore')).write_bytes(b'changed')
sys.exit(int(os.environ.get('GRADLE_EXIT', '0')))
''')
        gradle.chmod(0o755)
        framework = self.root / RELEASE
        framework.mkdir(parents=True)
        libraries = []
        for variant, identifier in [("", "ios-arm64"), ("simulator", "ios-arm64-simulator")]:
            folder = framework / identifier / "WalletCore.framework"
            folder.mkdir(parents=True)
            (folder / "WalletCore").write_bytes(b"release binary")
            libraries.append({"LibraryIdentifier": identifier, "LibraryPath": "WalletCore.framework",
                              "SupportedPlatform": "ios", "SupportedPlatformVariant": variant,
                              "SupportedArchitectures": ["arm64"]})
        (framework / "Info.plist").write_bytes(plistlib.dumps({"AvailableLibraries": libraries}))
        self.artifact = self.root / "shared release"
        subprocess.run([sys.executable, str(self.scripts / "wallet-core-artifact.py"), "pack",
                        "--artifact-dir", str(self.artifact)], cwd=self.root, env=self.env,
                       check=True, capture_output=True, text=True, timeout=10)

    def prepare(self, shared=True, **env):
        arguments = ["bash", str(self.runner), "platform=iOS Simulator,name=Test"]
        if shared:
            arguments.append(str(self.artifact))
        result = subprocess.run(arguments, cwd=self.root.parent, env={**self.env, **env},
                                capture_output=True, text=True, timeout=10)
        log = self.root / "gradle-calls.jsonl"
        calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
        tasks = [next(arg.rsplit(":", 1)[-1] for arg in call if arg.startswith(":")) for call in calls]
        for call in calls:
            self.assertIn("--max-workers=1", call)
        return result, tasks

    def test_shared_release_is_verified_and_only_fixture_is_built(self):
        result, tasks = self.prepare()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(tasks, ["assembleWalletCoreBridgeFixturesReleaseXCFramework"])
        self.assertEqual(result.stdout.count("Verified full release WalletCore"), 2)

    def test_local_fallback_builds_release_before_fixture(self):
        result, tasks = self.prepare(shared=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(tasks, ["assembleWalletCoreReleaseXCFramework",
                                 "assembleWalletCoreBridgeFixturesReleaseXCFramework"])

    def test_wrong_revision_fails_before_native_build(self):
        path = self.artifact / "wallet-core.json"
        manifest = json.loads(path.read_text())
        manifest["identity_sha"] = "b" * 40
        path.write_text(json.dumps(manifest))
        result, tasks = self.prepare()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("identity_sha mismatch", result.stderr)
        self.assertEqual(tasks, [])

    def test_missing_artifact_fails_without_rebuild_fallback(self):
        shutil.rmtree(self.artifact)
        result, tasks = self.prepare()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(tasks, [])

    def test_fixture_cannot_replace_the_release(self):
        result, tasks = self.prepare(TAMPER_RELEASE="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("contents differ", result.stderr)
        self.assertEqual(len(tasks), 1)

    def test_native_build_failure_is_propagated(self):
        result, tasks = self.prepare(GRADLE_EXIT="17")
        self.assertEqual(result.returncode, 17, result.stderr)
        self.assertEqual(len(tasks), 1)


if __name__ == "__main__":
    unittest.main()
