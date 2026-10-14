#!/usr/bin/env python3
"""Reject missing suites, failures and unexpected skips in JUnit results."""

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


def test_id(value):
    classname, separator, name = value.partition("#")
    if not separator or not classname or not name:
        raise EvidenceError(f"Expected Class#method, got: {value}")
    return test_class(classname), test_name(name)


def verify(reports, *, required_suites=(), required_classes=(),
           allowed_skips=(), forbidden_prefixes=()):
    reports = sorted(set(map(Path, reports)))
    if not reports:
        raise EvidenceError("No JUnit reports found")
    allowed_skips = {test_id(value) for value in allowed_skips}
    passed_classes, passed_suites = set(), set()
    counts = dict(executed=0, passed=0, skipped=0, failed=0)
    problems = []
    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except (OSError, ET.ParseError) as error:
            raise EvidenceError(f"Unreadable JUnit report: {report}: {error}") from error
        if root.tag not in {"testsuite", "testsuites"}:
            raise EvidenceError(f"Not a JUnit report: {report}")
        cases = list(root.iter("testcase"))
        if not cases:
            problems.append(f"Empty JUnit report: {report}")
        passed_cases = set()
        for case in cases:
            identity = (test_class(case.get("classname", "")), test_name(case.get("name", "")))
            if not all(identity):
                raise EvidenceError(f"Unnamed test case in {report}")
            if any(identity[0].startswith(prefix) for prefix in forbidden_prefixes):
                problems.append(f"Physical test entered a CI-safe suite: {identity}")
            # A skip or a later retry must never hide a failure.
            if case.find("failure") is not None or case.find("error") is not None:
                counts["executed"] += 1
                counts["failed"] += 1
                problems.append(f"Failed test: {identity}")
            elif case.find("skipped") is not None or case.get("status") in {"notrun", "disabled", "skipped"}:
                counts["skipped"] += 1
                if identity not in allowed_skips:
                    problems.append(f"Unexpected skipped test: {identity}")
            else:
                counts["executed"] += 1
                counts["passed"] += 1
                passed_classes.add(identity[0])
                passed_cases.add(case)
        # A runner/setup error may exist at suite level without a test case.
        for suite in root.iter():
            if suite.tag not in {"testsuite", "testsuites"}:
                continue
            if (suite.find("error") is not None or suite.find("failure") is not None
                    or any(int(suite.get(attribute, "0")) > 0 for attribute in ("errors", "failures"))):
                problems.append(f"Suite setup/teardown failed: {suite.get('name', report.name)}")
            if any(case in passed_cases for case in suite.iter("testcase")):
                passed_suites.add(suite.get("name", ""))
    if counts["executed"] == 0:
        problems.append("No tests executed")
    problems.extend(f"Expected suite did not execute: {name}" for name in sorted(set(required_suites) - passed_suites))
    problems.extend(f"Expected class did not execute: {name}" for name in sorted(set(required_classes) - passed_classes))
    if problems:
        raise EvidenceError("\n".join(problems))
    return counts | {"requiredSuitesPassed": len(set(required_suites)),
                     "requiredClassesPassed": len(set(required_classes)),
                     "reports": len(reports)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", nargs="+", required=True, help="JUnit files or quoted glob patterns")
    parser.add_argument("--require-suite", nargs="+", default=[], help="Each target/suite must contain a passing case")
    parser.add_argument("--require-class", nargs="+", default=[], help="Each critical class must contain a passing case")
    parser.add_argument("--allow-skipped", nargs="+", default=[], help="Class#method for intentional skips only")
    args = parser.parse_args()
    try:
        reports = [path for pattern in args.reports for path in glob.glob(pattern, recursive=True)]
        result = verify(reports, required_suites=args.require_suite, required_classes=args.require_class,
                        allowed_skips=args.allow_skipped)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Test evidence rejected: {error}\n")
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
