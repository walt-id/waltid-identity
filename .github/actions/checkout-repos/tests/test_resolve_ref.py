import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class ResolveRefTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.remote = self.directory / "remote.git"
        self.output = self.directory / "output"
        self.git = shutil.which("git")
        subprocess.run([self.git, "init", "--bare", str(self.remote)], check=True, capture_output=True)
        tree = self.run_git("mktree", input="").stdout.strip()
        self.commit = self.run_git(
            "-c", "user.name=CI fixture", "-c", "user.email=ci@example.invalid",
            "commit-tree", tree, input="Ref selection fixture\n"
        ).stdout.strip()
        self.add_ref("main")
        self.add_ref("release/v0.23.x")

        # Replace only the network transport; real git performs ref matching and exit statuses.
        binaries = self.directory / "bin"
        binaries.mkdir()
        shim = binaries / "git"
        shim.write_text(
            f"#!{sys.executable}\n"
            "import os, subprocess, sys\n"
            "from urllib.parse import urlsplit\n"
            "assert sys.argv[1:3] == ['ls-remote', '--exit-code']\n"
            "remote = urlsplit(sys.argv[3])\n"
            "if remote.username != 'x-access-token' or remote.password != 'fixture-token':\n"
            "    sys.exit(128)\n"
            "sys.exit(subprocess.run([os.environ['REAL_GIT'], 'ls-remote', '--exit-code', "
            "os.environ['FIXTURE_REMOTE'], *sys.argv[4:]]).returncode)\n"
        )
        shim.chmod(0o755)
        self.environment = dict(
            os.environ,
            PATH=str(binaries) + os.pathsep + os.environ["PATH"],
            REAL_GIT=self.git,
            FIXTURE_REMOTE=str(self.remote),
            CHECKOUT_REPOSITORY="walt-id/waltid-identity-enterprise",
            CHECKOUT_TOKEN="fixture-token",
            FALLBACK_REF="release/v0.23.x",
            GITHUB_OUTPUT=str(self.output),
        )

    def run_git(self, *arguments, input=None):
        return subprocess.run(
            [self.git, "--git-dir", str(self.remote), *arguments],
            input=input, text=True, capture_output=True, check=True
        )

    def add_ref(self, name):
        self.run_git("update-ref", "refs/heads/" + name, self.commit)

    def resolve(self, requested, **overrides):
        script = Path(__file__).resolve().parents[1] / "resolve-ref.sh"
        result = subprocess.run(
            ["bash", str(script)],
            env=dict(self.environment, REQUESTED_REF=requested, **overrides),
            text=True, capture_output=True
        )
        self.assertNotIn("fixture-token", result.stdout + result.stderr)
        return result

    def test_existing_ref_in_private_repository_is_used(self):
        self.add_ref("fix/wal-1799-v0.23.x")
        result = self.resolve("fix/wal-1799-v0.23.x")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("ref=fix/wal-1799-v0.23.x\n", self.output.read_text())

    def test_missing_ref_uses_release_instead_of_main(self):
        result = self.resolve("fix/wal-1799-v0.23.x")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("ref=release/v0.23.x\n", self.output.read_text())

    def test_empty_requested_ref_uses_release(self):
        result = self.resolve("")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("ref=release/v0.23.x\n", self.output.read_text())

    def test_explicit_fallback_is_respected(self):
        result = self.resolve("missing", FALLBACK_REF="main")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("ref=main\n", self.output.read_text())

    def test_repository_access_error_does_not_silently_select_a_fallback(self):
        result = self.resolve("missing", FIXTURE_REMOTE=str(self.directory / "inaccessible.git"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Unable to inspect refs", result.stderr)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
