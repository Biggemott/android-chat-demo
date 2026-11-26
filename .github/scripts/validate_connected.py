"""Validate AGP connected-test XML from a fresh result directory on one device."""
import argparse
from pathlib import Path
from xml.etree import ElementTree as ET


# Update this floor when adding or intentionally removing instrumented cases.
MINIMUM_TESTS = 35


def validate(report_directory, serial):
    reports = sorted((report_directory / "connected" / "debug").glob("TEST-*.xml"))
    if len(reports) != 1:
        raise ValueError(f"Expected one device XML report, found {len(reports)} in {report_directory}")

    root = ET.parse(reports[0]).getroot()
    if root.tag != "testsuites":
        raise ValueError(f"Unexpected XML root {root.tag!r}")

    executed = set()
    for suite in root.findall("testsuite"):
        devices = [prop.get("value") for prop in suite.findall("properties/property[@name='device']")]
        if devices != [serial]:
            raise ValueError(f"Suite {suite.get('name')} reports {devices}, expected {serial}")
        cases = suite.findall("testcase")
        check_totals(suite, len(cases))
        for case in cases:
            key = (case.get("classname"), case.get("name"))
            if not all(key) or key in executed:
                raise ValueError(f"Unnamed or duplicate test case {key}")
            if any(child.tag in ("failure", "error", "skipped") for child in case):
                raise ValueError(f"Unsuccessful test case {key}")
            executed.add(key)

    check_totals(root, len(executed))
    if len(executed) < MINIMUM_TESTS:
        raise ValueError(f"Only {len(executed)} tests executed, expected at least {MINIMUM_TESTS}")
    return len(executed)


def check_totals(element, count):
    if element.get("tests") != str(count):
        raise ValueError(f"{element.tag} reports {element.get('tests')} tests but contains {count}")
    for outcome in ("failures", "errors", "skipped"):
        if element.get(outcome) != "0":
            raise ValueError(f"{element.tag} reports {outcome}={element.get(outcome)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report_directory", type=Path)
    parser.add_argument("device_serial")
    args = parser.parse_args()
    try:
        count = validate(args.report_directory, args.device_serial)
    except (OSError, ValueError, ET.ParseError) as error:
        parser.exit(1, f"Connected test validation failed: {error}\n")
    print(f"{count} tests passed on {args.device_serial} with zero failures, errors or skips")


if __name__ == "__main__":
    main()
