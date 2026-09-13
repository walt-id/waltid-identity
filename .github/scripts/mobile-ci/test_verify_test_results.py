import tempfile
from pathlib import Path
import unittest

from verify_test_results import EvidenceError, verify


class TestEvidenceTests(unittest.TestCase):
    required = [{"class": "KMPProximityProjectionTests", "name": "testApprovedFields"}]

    def check(self, contents, **kwargs):
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder) / "results.xml"
            report.write_text(contents)
            return verify([report], self.required, **kwargs)

    def test_exact_test_is_required_even_when_other_tests_pass(self):
        with self.assertRaisesRegex(EvidenceError, "Expected test did not pass"):
            self.check('<testsuite><testcase classname="OtherTests" name="testApprovedFields"/></testsuite>')

    def test_xctest_method_parentheses_and_nested_suites(self):
        result = self.check('<testsuites><testsuite><testcase classname="KMPProximityProjectionTests" '
                            'name="testApprovedFields()"/></testsuite></testsuites>')
        self.assertEqual(result["requiredPassed"], 1)
        self.assertEqual(result["executed"], 1)

    def test_known_kmp_decorations_preserve_exact_selector_identity(self):
        for prefix, suffix in (("", "()[jvm]"), ("iosSimulatorArm64Test.", "[iosSimulatorArm64]"),
                               ("jsNodeTest.", "[js, node]")):
            result = self.check(f'<testsuite><testcase classname="{prefix}KMPProximityProjectionTests" '
                                f'name="testApprovedFields{suffix}"/></testsuite>')
            self.assertEqual(result["requiredPassed"], 1)
        with self.assertRaises(EvidenceError):
            self.check('<testsuite><testcase classname="KMPProximityProjectionTests" '
                       'name="testApprovedFields[unrelated-case]"/></testsuite>')
        with self.assertRaisesRegex(EvidenceError, "Physical test entered"):
            self.check('<testsuite><testcase classname="KMPProximityProjectionTests" name="testApprovedFields"/>'
                       '<testcase classname="iosSimulatorArm64Test.ProximityPhysicalDeviceTests" '
                       'name="testExchange[iosSimulatorArm64]"/></testsuite>',
                       forbidden_prefixes=["ProximityPhysicalDeviceTests"])

    def test_empty_missing_and_non_junit_reports_fail(self):
        with self.assertRaisesRegex(EvidenceError, "No JUnit"):
            verify([], self.required)
        for xml in ('<testsuite/>', '<testsuite tests="123"/>', '<html/>', '<testsuite>'):
            with self.subTest(xml=xml), self.assertRaises(EvidenceError):
                self.check(xml)

    def test_skipped_selected_tests_and_setup_errors_fail(self):
        for failure in ('<skipped/>', '<error/>', '<failure/>'):
            with self.subTest(failure=failure), self.assertRaises(EvidenceError):
                self.check('<testsuite><testcase classname="KMPProximityProjectionTests" '
                           f'name="testApprovedFields">{failure}</testcase></testsuite>')
        with self.assertRaisesRegex(EvidenceError, "Suite setup/teardown failed"):
            self.check('<testsuite><error/><testcase classname="KMPProximityProjectionTests" '
                       'name="testApprovedFields"/></testsuite>')

    def test_later_pass_cannot_hide_an_earlier_failure(self):
        with self.assertRaisesRegex(EvidenceError, "Failed test"):
            self.check('<testsuite><testcase classname="KMPProximityProjectionTests" '
                       'name="testApprovedFields"><failure/></testcase>'
                       '<testcase classname="KMPProximityProjectionTests" name="testApprovedFields"/></testsuite>')

    def test_physical_tests_are_rejected_even_if_they_pass(self):
        with self.assertRaisesRegex(EvidenceError, "Physical test entered"):
            self.check('<testsuite><testcase classname="KMPProximityProjectionTests" name="testApprovedFields"/>'
                       '<testcase classname="ProximityPhysicalDeviceTests" name="testExchange"/></testsuite>',
                       forbidden_prefixes=["ProximityPhysicalDeviceTests"])

    def test_unrelated_legacy_skip_is_counted_without_claiming_it_passed(self):
        result = self.check('<testsuite><testcase classname="KMPProximityProjectionTests" name="testApprovedFields"/>'
                            '<testcase classname="LegacyTests" name="unsupported"><skipped/></testcase></testsuite>')
        self.assertEqual(result["skipped"], 1)
        self.assertEqual(result["passed"], 1)


if __name__ == "__main__":
    unittest.main()
