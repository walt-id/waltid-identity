import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ACTION = Path(__file__).resolve().parents[2] / "actions/checkout-repos/action.yml"


class CheckoutRefsTest(unittest.TestCase):
    def resolve(self, refs, identity_sha=""):
        step = ACTION.read_text().split("      id: refs\n", 1)[1]
        script = textwrap.dedent(step.split("      run: |\n", 1)[1].split("\n    - name:", 1)[0])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output, calls = root / "output", root / "calls"
            output.touch()
            # Ref discovery is deterministic and offline. Exact revisions must bypass it.
            git = root / "git"
            git.write_text('#!/bin/sh\necho called >> "$GIT_CALL_LOG"\ncase "$3" in *waltid-identity.git) exit 0;; *) exit 2;; esac\n')
            git.chmod(0o755)
            env = {**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                   "GITHUB_OUTPUT": str(output), "GIT_CALL_LOG": str(calls),
                   "TOKEN": "test-token", "REF": "topic", "RESOLVED_REFS": refs, "IDENTITY_SHA": identity_sha}
            result = subprocess.run(["bash", "-euo", "pipefail", "-c", script], env=env,
                                    capture_output=True, text=True, timeout=10)
            return result, dict(line.split("=", 1) for line in output.read_text().splitlines()), calls.exists()

    def test_exact_revisions_bypass_branch_discovery(self):
        expected = dict(zip(["unified", "identity", "enterprise", "enterprise_license"],
                            [character * 40 for character in "abcd"]))
        result, actual, discovered = self.resolve(json.dumps(expected))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(actual, expected)
        self.assertFalse(discovered)

    def test_incomplete_or_invalid_revisions_do_not_fall_back(self):
        for refs in ["not json", "{}", '{"unified":"main"}', '{"unified":"' + "a" * 40 + '"}']:
            with self.subTest(refs=refs):
                result, _, discovered = self.resolve(refs)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(discovered)

    def test_event_commit_pins_identity_and_preserves_companion_fallback(self):
        result, refs, _ = self.resolve("", "e" * 40)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(refs, {"unified": "main", "identity": "e" * 40, "enterprise": "main", "enterprise_license": "main"})
        result, _, _ = self.resolve("", "invalid")
        self.assertNotEqual(result.returncode, 0)

    def test_resolved_revisions_take_precedence_over_event_commit(self):
        expected = {key: "a" * 40 for key in ["unified", "identity", "enterprise", "enterprise_license"]}
        result, refs, _ = self.resolve(json.dumps(expected), "e" * 40)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(refs, expected)

    def test_default_keeps_per_repository_fallback(self):
        result, refs, discovered = self.resolve("")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(discovered)
        self.assertEqual(refs, {"unified": "main", "identity": "topic", "enterprise": "main", "enterprise_license": "main"})


if __name__ == "__main__":
    unittest.main()
