import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

from report import REQUIRED_COUNTS, read_results, render


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name)
        self.suite = ET.Element("testsuite")
        for classname, count in REQUIRED_COUNTS.items():
            for number in range(count):
                ET.SubElement(self.suite, "testcase", classname=classname, name=f"check {number}")

    def write(self):
        ET.ElementTree(self.suite).write(self.path / "TEST-profiles.xml")

    def test_preserves_failures_and_errors_without_printing_payloads(self):
        ET.SubElement(self.suite[0], "failure", message="private request payload")
        ET.SubElement(self.suite[1], "error", message="private error payload")
        self.write()
        summary = render(read_results(self.path))
        self.assertIn("8 passed; 1 failed; 1 errors", summary)
        self.assertIn("| FAIL |", summary)
        self.assertIn("| ERROR |", summary)
        self.assertNotIn("private", summary)

    def test_rejects_missing_results(self):
        with self.assertRaisesRegex(ValueError, "inventory differs"):
            read_results(self.path)

    def test_rejects_a_partial_class(self):
        self.suite.remove(self.suite[2])
        self.write()
        with self.assertRaisesRegex(ValueError, "inventory differs"):
            read_results(self.path)

    def test_rejects_duplicate_results(self):
        self.suite.append(self.suite[0])
        self.write()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            read_results(self.path)

    def test_rejects_skipped_checks(self):
        ET.SubElement(self.suite[0], "skipped")
        self.write()
        with self.assertRaisesRegex(ValueError, "skipped"):
            read_results(self.path)

    def test_rejects_malformed_xml(self):
        (self.path / "TEST-profiles.xml").write_text("<testsuite>")
        with self.assertRaises(ET.ParseError):
            read_results(self.path)

    def test_renders_all_passing_results_and_escapes_names(self):
        self.suite[0].set("name", "check | <markup>\nnext")
        self.write()
        summary = render(read_results(self.path))
        self.assertIn("10 passed; 0 failed; 0 errors", summary)
        self.assertIn("check \\| &lt;markup&gt; next", summary)
        self.assertIn("not hosted ITB results", summary)


if __name__ == "__main__":
    unittest.main()
