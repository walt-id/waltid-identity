import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCOPE = ROOT / ".github/actions/pr-change-scope/scope.sh"
DECIDE = ROOT / ".github/scripts/ci/decide-gradle-eligibility.sh"


class PrChangeScopeTest(unittest.TestCase):
    def test_complete_diff_includes_old_name_and_coordination_defaults_to_full(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            def git(*args):
                return subprocess.check_output(["git", *args], cwd=repo, text=True).strip()
            git("init", "-q")
            git("config", "user.name", "CI fixture")
            git("config", "user.email", "ci@example.invalid")
            (repo / "Source.kt").write_text("fixture")
            git("add", ".")
            git("commit", "-qm", "base")
            base = git("rev-parse", "HEAD")
            git("mv", "Source.kt", "README.md")
            git("commit", "-qm", "rename")
            gh = repo / "gh"
            gh.write_text('#!/bin/sh\ncat "$RUNNER_TEMP/response"\n')
            gh.chmod(0o700)
            env = dict(os.environ, PATH=f"{tmp}:{os.environ['PATH']}", RUNNER_TEMP=tmp, GH_TOKEN="fixture",
                       GITHUB_REPOSITORY="walt-id/waltid-identity", HEAD_BRANCH="topic",
                       BASE_SHA=base, HEAD_SHA=git("rev-parse", "HEAD"), GITHUB_OUTPUT=str(repo / "output"))
            repos = {name: {"name": name, "ref": None} for name in ("unified", "enterprise", "license", "waltid-identity")}
            repos["waltid-identity"]["ref"] = {"name": "topic"}
            for ref, expected in [(None, "false"), ({"name": "topic"}, "true")]:
                repos["enterprise"]["ref"] = ref
                (repo / "response").write_text(json.dumps({"data": repos}))
                (repo / "output").write_text("")
                subprocess.run(["bash", SCOPE], cwd=repo, env=env, check=True, capture_output=True)
                values = dict(line.split("=", 1) for line in (repo / "output").read_text().splitlines())
                self.assertEqual(values["full"], expected)
                self.assertEqual(set(Path(values["files"]).read_bytes().split(b"\0")[:-1]), {b"Source.kt", b"README.md"})

            for override in ({"GH_TOKEN": ""}, {"BASE_SHA": "unavailable-base"}):
                (repo / "output").write_text("")
                subprocess.run(["bash", SCOPE], cwd=repo, env={**env, **override}, check=True, capture_output=True)
                self.assertIn("full=true", (repo / "output").read_text().splitlines())

    def test_release_workflows_and_mixed_changes(self):
        release = ".github/workflows/release-candidate.yml"
        for files, full, gradle, focused in [
            ([release], False, False, True), ([".github/workflows/api-docker.yml", ".github/workflows/promote.yml"], False, False, True),
            ([release, "build.gradle.kts"], False, True, True), ([release], True, True, True),
            ([], True, True, True), (["src/Example.kt"], False, True, False),
        ]:
            with self.subTest(files=files, full=full), tempfile.TemporaryDirectory() as tmp:
                paths, output = Path(tmp) / "files", Path(tmp) / "out"
                paths.write_bytes(b"".join(p.encode() + b"\0" for p in files))
                env = dict(os.environ, EVENT_NAME="pull_request", CHANGED_PATHS=str(paths),
                           FULL_COVERAGE=str(full).lower(), GITHUB_OUTPUT=str(output))
                subprocess.run(["bash", DECIDE], env=env, check=True, capture_output=True)
                values = dict(line.split("=", 1) for line in output.read_text().splitlines())
                self.assertEqual(values["should-run"], str(gradle).lower())
                self.assertEqual(values["release"], str(focused).lower())
