import tempfile
from pathlib import Path
import unittest

from verify_test_results import EvidenceError, verify


class TestEvidenceTests(unittest.TestCase):
    def check(self, contents, **kwargs):
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder) / "results.xml"
            report.write_text(contents)
            return verify([report], **kwargs)

    def test_missing_ui_target_cannot_be_hidden_by_passing_unit_tests(self):
        for ui_suite in ('', '<testsuite name="iosAppUITests.xctest" tests="123"/>'):
            with self.subTest(ui_suite=ui_suite), self.assertRaisesRegex(EvidenceError, "Expected suite did not execute"):
                self.check('<testsuites><testsuite name="iosAppTests.xctest">'
                           '<testcase classname="UnitTests" name="passes"/></testsuite>'
                           f'{ui_suite}</testsuites>',
                           required_suites=["iosAppTests.xctest", "iosAppUITests.xctest"])

    def test_new_classes_and_methods_need_no_configuration(self):
        result = self.check('<testsuites><testsuite name="WalletSDKTests.xctest">'
                            '<testcase classname="ExistingTests" name="renamedTest()"/>'
                            '<testcase classname="NewTests" name="newTest()"/>'
                            '</testsuite></testsuites>', required_suites=["WalletSDKTests.xctest"])
        self.assertEqual(result["executed"], 2)
        self.assertEqual(result["requiredSuitesPassed"], 1)

    def test_missing_conditional_bridge_class_fails(self):
        with self.assertRaisesRegex(EvidenceError, "Expected class did not execute"):
            self.check('<testsuite><testcase classname="PortableTests" name="passes"/></testsuite>',
                       required_classes=["KMPProximityProjectionTests"])

    def test_environment_canary_is_required_even_when_other_tests_pass(self):
        with self.assertRaisesRegex(EvidenceError, "Expected test did not pass"):
            self.check('<testsuite><testcase classname="BrowserTests" name="ordinaryTest"/></testsuite>',
                       required_tests=["BrowserTests#browserOptIn"])

    def test_xctest_method_parentheses_and_nested_suites(self):
        result = self.check('<testsuites><testsuite name="target"><testsuite name="nested">'
                            '<testcase classname="KMPProximityProjectionTests" name="testApprovedFields()"/>'
                            '</testsuite></testsuite></testsuites>',
                            required_suites=["target", "nested"],
                            required_tests=["KMPProximityProjectionTests#testApprovedFields"])
        self.assertEqual(result["requiredTestsPassed"], 1)
        self.assertEqual(result["executed"], 1)

    def test_known_kmp_decorations_preserve_exact_selector_identity(self):
        for prefix, suffix in (("", "()[jvm]"), ("iosSimulatorArm64Test.", "[iosSimulatorArm64]"),
                               ("jsNodeTest.", "[js, node]")):
            result = self.check(f'<testsuite><testcase classname="{prefix}KMPProximityProjectionTests" '
                                f'name="testApprovedFields{suffix}"/></testsuite>',
                                required_classes=["KMPProximityProjectionTests"],
                                required_tests=["KMPProximityProjectionTests#testApprovedFields"])
            self.assertEqual(result["requiredTestsPassed"], 1)
        with self.assertRaises(EvidenceError):
            self.check('<testsuite><testcase classname="KMPProximityProjectionTests" '
                       'name="testApprovedFields[unrelated-case]"/></testsuite>',
                       required_tests=["KMPProximityProjectionTests#testApprovedFields"])
        with self.assertRaisesRegex(EvidenceError, "Physical test entered"):
            self.check('<testsuite><testcase classname="KMPProximityProjectionTests" name="testApprovedFields"/>'
                       '<testcase classname="iosSimulatorArm64Test.ProximityPhysicalDeviceTests" '
                       'name="testExchange[iosSimulatorArm64]"/></testsuite>',
                       forbidden_prefixes=["ProximityPhysicalDeviceTests"])

    def test_empty_missing_and_non_junit_reports_fail(self):
        with self.assertRaisesRegex(EvidenceError, "No JUnit"):
            verify([])
        for xml in ('<testsuite/>', '<testsuite tests="123"/>', '<html/>', '<testsuite>'):
            with self.subTest(xml=xml), self.assertRaises(EvidenceError):
                self.check(xml)

    def test_skipped_selected_tests_and_setup_errors_fail(self):
        for failure in ('<skipped/>', '<error/>', '<failure/>'):
            with self.subTest(failure=failure), self.assertRaises(EvidenceError):
                self.check('<testsuite><testcase classname="KMPProximityProjectionTests" '
                           f'name="testApprovedFields">{failure}</testcase></testsuite>')
        for setup_error in ('<error/>', '<failure/>'):
            with self.subTest(setup_error=setup_error), self.assertRaisesRegex(EvidenceError, "Suite setup/teardown failed"):
                self.check(f'<testsuite>{setup_error}<testcase classname="Tests" name="passes"/></testsuite>')
        with self.assertRaisesRegex(EvidenceError, "Suite setup/teardown failed"):
            self.check('<testsuites errors="1"><testsuite><testcase classname="Tests" name="passes"/>'
                       '</testsuite></testsuites>')

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

    def test_new_class_cannot_introduce_an_unexpected_skip(self):
        for skipped in ('<testcase classname="NewTests" name="skips"><skipped/></testcase>',
                        '<testcase classname="NewTests" name="skips" status="notrun"/>',
                        '<testcase classname="NewTests" name="skips" status="disabled"/>'):
            with self.subTest(skipped=skipped), self.assertRaisesRegex(EvidenceError, "Unexpected skipped test"):
                self.check('<testsuite><testcase classname="Tests" name="passes"/>' + skipped + '</testsuite>')

    def test_only_explicit_skips_are_allowed_and_never_count_as_passes(self):
        result = self.check('<testsuite><testcase classname="Tests" name="passes"/>'
                            '<testcase classname="DeviceTests" name="physical()"><skipped/></testcase></testsuite>',
                            allowed_skips=["DeviceTests#physical"])
        self.assertEqual(result["skipped"], 1)
        self.assertEqual(result["passed"], 1)
        with self.assertRaisesRegex(EvidenceError, "Expected class did not execute"):
            self.check('<testsuite><testcase classname="Tests" name="passes"/>'
                       '<testcase classname="DeviceTests" name="physical"><skipped/></testcase></testsuite>',
                       required_classes=["DeviceTests"], allowed_skips=["DeviceTests#physical"])

    def test_allowing_a_skip_does_not_hide_a_failure(self):
        with self.assertRaisesRegex(EvidenceError, "Failed test"):
            self.check('<testsuite><testcase classname="Tests" name="fails"><skipped/><failure/></testcase></testsuite>',
                       allowed_skips=["Tests#fails"])

    def test_empty_report_cannot_be_hidden_by_another_report(self):
        with tempfile.TemporaryDirectory() as folder:
            reports = [Path(folder) / name for name in ("passed.xml", "empty.xml")]
            reports[0].write_text('<testsuite><testcase classname="Tests" name="passes"/></testsuite>')
            reports[1].write_text('<testsuite tests="10"/>')
            with self.assertRaisesRegex(EvidenceError, "Empty JUnit report"):
                verify(reports)


if __name__ == "__main__":
    unittest.main()
