import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "sync-ts12-payment-schema.py"
spec = importlib.util.spec_from_file_location("ts12_schema_sync", SCRIPT)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class SchemaDriftCheckTest(unittest.TestCase):
    def run_check(self, remote):
        with patch.object(sys, "argv", [str(SCRIPT), "--check-upstream"]), patch.object(sync, "read_url", return_value=remote) as read:
            with contextlib.redirect_stdout(io.StringIO()):
                sync.main()
            self.assertEqual(1, read.call_count)

    def test_current_upstream_passes_with_one_request(self):
        text = sync.OUTPUT.read_text()
        import re
        raw = re.search(r'private const val DEFINITION = """\n(.*?)"""', text, re.S).group(1).replace(sync.KOTLIN_DOLLAR, "$").encode()
        self.run_check(raw)
        self.assertIn(sync.KOTLIN_DOLLAR + "schema", text)

    def test_upstream_change_fails_with_review_command(self):
        with self.assertRaisesRegex(SystemExit, "--update --ref <reviewed-commit>"):
            self.run_check(b'{"type":"object","changed":true}')

    def test_corrupt_local_snapshot_fails_before_network(self):
        manifest = json.loads(sync.MANIFEST.read_text())
        manifest["sourceSha256"] = "invalid"
        with patch.object(sys, "argv", [str(SCRIPT), "--check-upstream"]), patch.object(sync, "MANIFEST") as manifest_path, patch.object(sync, "read_url") as read:
            manifest_path.read_text.return_value = json.dumps(manifest)
            with self.assertRaisesRegex(SystemExit, "source pin"):
                sync.main()
            read.assert_not_called()


if __name__ == "__main__":
    unittest.main()
