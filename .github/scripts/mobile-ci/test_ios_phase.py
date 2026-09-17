import json
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).with_name("run-ios-phase.py")


class IosPhaseTest(unittest.TestCase):
    def run_command(self, directory, *command):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--output-dir", directory, "test", *command],
            capture_output=True, text=True, timeout=20,
        )

    def test_preserves_output_and_exit_status(self):
        for exit_code in (0, 17):
            with self.subTest(exit_code=exit_code), tempfile.TemporaryDirectory() as directory:
                result = self.run_command(
                    directory, sys.executable, "-c",
                    f"import sys; print('stdout'); print('stderr', file=sys.stderr); sys.exit({exit_code})",
                )
                self.assertEqual(result.returncode, exit_code, result.stderr)
                output = Path(directory)
                self.assertIn("stdout", (output / "test.log").read_text())
                self.assertIn("stderr", (output / "test.log").read_text())
                metadata = json.loads((output / "test.json").read_text())
                self.assertEqual(metadata["exit_code"], exit_code)
                self.assertEqual(metadata["status"], "completed")
                self.assertGreaterEqual(metadata["elapsed_seconds"], 0)

    def test_command_cannot_start_is_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_command(directory, str(Path(directory) / "missing"))
            self.assertEqual(result.returncode, 1)
            self.assertEqual(json.loads((Path(directory) / "test.json").read_text())["exit_code"], 1)

    def test_cancellation_stays_failed_even_if_command_exits_successfully(self):
        with tempfile.TemporaryDirectory() as directory:
            ready = Path(directory) / "ready"
            code = (
                "import signal,time,pathlib; "
                "signal.signal(signal.SIGTERM, lambda *_: exit(0)); "
                f"pathlib.Path({str(ready)!r}).touch(); time.sleep(60)"
            )
            process = subprocess.Popen(
                [sys.executable, str(SCRIPT), "--output-dir", directory, "test", sys.executable, "-c", code],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            )
            try:
                deadline = time.monotonic() + 10
                while not ready.exists() and time.monotonic() < deadline:
                    time.sleep(0.05)
                self.assertTrue(ready.exists())
                process.send_signal(signal.SIGTERM)
                _, stderr = process.communicate(timeout=20)
                self.assertEqual(process.returncode, 143, stderr)
                metadata = json.loads((Path(directory) / "test.json").read_text())
                self.assertEqual(metadata["status"], "cancelled")
                self.assertEqual(metadata["exit_code"], 143)
                self.assertTrue((Path(directory) / "test.resources.jsonl").exists())
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()

    def test_phase_cannot_escape_output_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--output-dir", directory, "../escape", "true"],
                capture_output=True, timeout=10,
            )
            self.assertEqual(result.returncode, 2)

    def test_cancellation_bounds_an_uncooperative_command(self):
        with tempfile.TemporaryDirectory() as directory:
            ready = Path(directory) / "ready"
            code = (
                "import signal,time,pathlib; signal.signal(signal.SIGTERM, signal.SIG_IGN); "
                f"pathlib.Path({str(ready)!r}).touch(); time.sleep(60)"
            )
            process = subprocess.Popen(
                [sys.executable, str(SCRIPT), "--output-dir", directory, "test", sys.executable, "-c", code],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            )
            try:
                deadline = time.monotonic() + 10
                while not ready.exists() and time.monotonic() < deadline:
                    time.sleep(0.05)
                self.assertTrue(ready.exists())
                process.send_signal(signal.SIGTERM)
                _, stderr = process.communicate(timeout=20)
                self.assertEqual(process.returncode, 143, stderr)
                self.assertEqual(json.loads((Path(directory) / "test.json").read_text())["status"], "cancelled")
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()


if __name__ == "__main__":
    unittest.main()
