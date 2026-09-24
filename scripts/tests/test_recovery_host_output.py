"""Exercise the real host redirection with both Kotlin-style writes and C stdio."""
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest


class RecoveryHostOutputTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        directory = TemporaryDirectory()
        cls.addClassCleanup(directory.cleanup)
        cls.directory = Path(directory.name)
        cls.binary = cls.directory / "output-test"
        source = cls.directory / "output-test.c"
        source.write_text(r'''
#include "recovery-host-output.h"
#include <stdlib.h>
int main(int argc, char **argv) {
    if (argc != 3) return 2;
    int closed = atoi(argv[2]);
    for (int fd = 0; fd <= 2; ++fd) if (closed & (1 << fd)) close(fd);
    if (redirectRecoveryOutput(argv[1]) != 0) return 2;
    const char result[] = "[  PASSED  ] 1 tests.\n";
    const char diagnostic[] = "native diagnostic\n";
    if (write(STDOUT_FILENO, result, sizeof(result) - 1) != sizeof(result) - 1) return 3;
    if (write(STDERR_FILENO, diagnostic, sizeof(diagnostic) - 1) != sizeof(diagnostic) - 1) return 4;
    printf("RECOVERY_TEST_EXIT=0\n");
    return fflush(stdout) == 0 ? 0 : 5;
}
''')
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I", str(Path(__file__).resolve().parents[1]),
                        str(source), "-o", str(cls.binary)], check=True, capture_output=True, text=True)

    def test_stdio_and_descriptor_writes_share_the_log_even_with_closed_standard_descriptors(self):
        for closed in range(8):
            with self.subTest(closed=closed):
                log = self.directory / f"closed-{closed}.log"
                result = subprocess.run([self.binary, log, str(closed)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("", result.stdout)
                self.assertEqual("", result.stderr)
                self.assertEqual("[  PASSED  ] 1 tests.\nnative diagnostic\nRECOVERY_TEST_EXIT=0\n", log.read_text())

    def test_log_open_failure_does_not_report_completion(self):
        log = self.directory / "missing-parent/output.log"
        result = subprocess.run([self.binary, log, "0"], capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertNotIn("RECOVERY_TEST_EXIT=0", result.stdout + result.stderr)
        self.assertFalse(log.exists())
