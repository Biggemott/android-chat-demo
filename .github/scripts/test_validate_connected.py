"""Exercise the validator as a command, with synthetic connected-test reports."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET


class ConnectedResultsTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)
        self.report = self.directory / "connected" / "debug" / "TEST-device.xml"
        self.report.parent.mkdir(parents=True)

    def write_report(self, count=35, serial="emulator-5554", outcome=None):
        totals = dict(tests=str(count), failures="0", errors="0", skipped="0")
        if outcome:
            totals[{"failure": "failures", "error": "errors", "skipped": "skipped"}[outcome]] = "1"
        root = ET.Element("testsuites", totals)
        suite = ET.SubElement(root, "testsuite", totals, name="example.ChatTest")
        props = ET.SubElement(suite, "properties")
        ET.SubElement(props, "property", name="device", value=serial)
        for index in range(count):
            case = ET.SubElement(suite, "testcase", name=f"test{index}", classname="example.ChatTest")
            if index == 0 and outcome:
                ET.SubElement(case, outcome)
        ET.ElementTree(root).write(self.report)

    def validate(self):
        script = Path(__file__).with_name("validate_connected.py")
        return subprocess.run(
            [sys.executable, str(script), str(self.directory), "emulator-5554"],
            capture_output=True, text=True, check=False,
        )

    def test_accepts_complete_report(self):
        result = self.validate_after_report()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("35 tests passed", result.stdout)

    def validate_after_report(self, **kwargs):
        self.write_report(**kwargs)
        return self.validate()

    def test_rejects_missing_results_even_if_old_report_exists_elsewhere(self):
        self.write_report()
        old_report = self.directory / "previous-run" / "connected" / "debug" / self.report.name
        old_report.parent.mkdir(parents=True)
        self.report.rename(old_report)
        self.assertNotEqual(self.validate().returncode, 0)

    def test_rejects_empty_and_malformed_xml(self):
        for invalid in ("", "<testsuites"):
            with self.subTest(invalid=invalid):
                self.report.write_text(invalid, encoding="utf-8")
                self.assertNotEqual(self.validate().returncode, 0)

    def test_rejects_zero_and_low_counts(self):
        for count in (0, 34):
            with self.subTest(count=count):
                self.assertNotEqual(self.validate_after_report(count=count).returncode, 0)

    def test_rejects_other_device(self):
        self.assertNotEqual(self.validate_after_report(serial="emulator-5556").returncode, 0)

    def test_rejects_failed_errored_or_skipped_tests(self):
        for outcome in ("failure", "error", "skipped"):
            with self.subTest(outcome=outcome):
                self.assertNotEqual(self.validate_after_report(outcome=outcome).returncode, 0)

    def test_rejects_duplicate_cases(self):
        self.write_report()
        root = ET.parse(self.report)
        cases = root.findall(".//testcase")
        cases[1].set("name", cases[0].get("name"))
        root.write(self.report)
        self.assertNotEqual(self.validate().returncode, 0)

    def test_rejects_success_summary_without_all_cases(self):
        self.write_report()
        root = ET.parse(self.report)
        suite = root.find("testsuite")
        suite.remove(suite.find("testcase"))
        root.write(self.report)
        self.assertNotEqual(self.validate().returncode, 0)


if __name__ == "__main__":
    unittest.main()
