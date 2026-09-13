#!/usr/bin/env python3
"""Require named production-path tests in JUnit results, independent of runner exit status."""

import argparse
import glob
import json
from pathlib import Path
import xml.etree.ElementTree as ET


class EvidenceError(ValueError):
    pass


def test_name(value):
    # Strip only known runner decorations, never arbitrary parameterized case IDs.
    for suffix in ("[jvm]", "[android]", "[iosSimulatorArm64]", "[js, node]"):
        value = value.removesuffix(suffix)
    return value.removesuffix("()")


def test_class(value):
    for prefix in ("iosSimulatorArm64Test.", "jsNodeTest."):
        value = value.removeprefix(prefix)
    return value


def verify(reports, required, forbidden_prefixes=()):
    if not reports:
        raise EvidenceError("No JUnit reports found")
    if not required:
        raise EvidenceError("At least one expected test ID is required")
    expected = {(item["class"], test_name(item["name"])) for item in required}
    if len(expected) != len(required):
        raise EvidenceError("Duplicate expected test ID")
    owned_classes = {item[0] for item in expected}
    passed = set()
    counts = dict(executed=0, passed=0, skipped=0, failed=0)
    problems = []
    for report in sorted(set(map(Path, reports))):
        try:
            root = ET.parse(report).getroot()
        except (OSError, ET.ParseError) as error:
            raise EvidenceError(f"Unreadable JUnit report: {report}: {error}") from error
        if root.tag not in {"testsuite", "testsuites"}:
            raise EvidenceError(f"Not a JUnit report: {report}")
        for case in root.iter("testcase"):
            identity = (test_class(case.get("classname", "")), test_name(case.get("name", "")))
            if not all(identity):
                raise EvidenceError(f"Unnamed test case in {report}")
            if any(identity[0].startswith(prefix) for prefix in forbidden_prefixes):
                problems.append(f"Physical test entered a CI-safe suite: {identity}")
            if case.find("skipped") is not None or case.get("status") in {"notrun", "disabled"}:
                counts["skipped"] += 1
                if identity[0] in owned_classes:
                    problems.append(f"Unexpected skipped test: {identity}")
            else:
                counts["executed"] += 1
                if case.find("failure") is not None or case.find("error") is not None:
                    counts["failed"] += 1
                    problems.append(f"Failed test: {identity}")
                else:
                    counts["passed"] += 1
                    passed.add(identity)
        # A runner/setup error may exist at suite level without a test case.
        for suite in root.iter("testsuite"):
            if (suite.find("error") is not None or suite.find("failure") is not None
                    or any(int(suite.get(attribute, "0")) > 0 for attribute in ("errors", "failures"))):
                problems.append(f"Suite setup/teardown failed: {suite.get('name', report.name)}")
    if counts["executed"] == 0:
        problems.append("No tests executed")
    problems.extend(f"Expected test did not pass: {identity}" for identity in sorted(expected - passed))
    if problems:
        raise EvidenceError("\n".join(problems))
    return counts | {"requiredPassed": len(expected), "reports": len(set(map(Path, reports)))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--suite", required=True)
    parser.add_argument("--reports", nargs="+", required=True, help="JUnit files or quoted glob patterns")
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text())
        suite = manifest["suites"][args.suite]
        reports = [path for pattern in args.reports for path in glob.glob(pattern, recursive=True)]
        result = verify(reports, suite["requiredTests"], manifest["physicalClassPrefixes"])
    except (EvidenceError, OSError, ValueError, KeyError) as error:
        parser.exit(1, f"Test evidence rejected: {error}\n")
    print(json.dumps({"suite": args.suite, **result}, sort_keys=True))


if __name__ == "__main__":
    main()
