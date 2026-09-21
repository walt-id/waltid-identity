#!/usr/bin/env python3
"""Render ITB-profile JUnit results without converting failures or missing coverage into passes."""

import argparse
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET


# Update this inventory when intentionally adding or removing checks.
REQUIRED_COUNTS = {
    "id.walt.itb.IssuanceProfileTest": 1,
    "id.walt.itb.PresentationProfileTest": 5,
    "id.walt.itb.DigitalCredentialsProfileTest": 1,
    "id.walt.itb.ResponseEncryptionProfileTest": 2,
    "id.walt.itb.PaymentProfileTest": 1,
}


def read_results(directory: Path):
    results = []
    seen = set()
    for path in sorted(directory.glob("TEST-*.xml")):
        for case in ET.parse(path).getroot().iter("testcase"):
            identity = (case.attrib["classname"], case.attrib["name"])
            if identity in seen:
                raise ValueError(f"Duplicate test result: {identity}")
            seen.add(identity)
            status = next(
                (label for element, label in [("error", "ERROR"), ("failure", "FAIL"), ("skipped", "SKIPPED")]
                 if case.find(element) is not None), "PASS"
            )
            results.append((*identity, status))
    actual_counts = Counter(result[0] for result in results)
    if actual_counts != REQUIRED_COUNTS:
        raise ValueError(f"Profile inventory differs: expected {REQUIRED_COUNTS}, received {dict(actual_counts)}")
    if any(result[2] == "SKIPPED" for result in results):
        raise ValueError("Profile checks were skipped; this is not a complete result set")
    return results


def render(results):
    counts = {status: sum(result[2] == status for result in results) for status in ["PASS", "FAIL", "ERROR"]}
    lines = [
        "## WeBuild wallet profile checks",
        "",
        f"{counts['PASS']} passed; {counts['FAIL']} failed; {counts['ERROR']} errors. No skipped checks.",
        "",
        "These are local protocol checks, not hosted ITB results or native platform qualification.",
        "Report-only mode preserves failed assertions; it does not establish conformance.",
        "",
        "| Check | Result |", "| --- | --- |",
    ]
    for _, name, status in results:
        safe_name = name.replace("|", "\\|").replace("\n", " ").replace("<", "&lt;").replace(">", "&gt;")
        lines.append(f"| {safe_name} | {status} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        print(render(read_results(args.directory)), end="")
    except (ValueError, KeyError, ET.ParseError, OSError) as error:
        print("## ITB report unavailable\n\nResult collection failed: " + str(error))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
