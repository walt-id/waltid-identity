"""Require executed, accepted cases from a requested wallet/verifier CI role."""

import argparse
import json
from pathlib import Path


def check_results(path: Path) -> None:
    try:
        entries = json.loads(path.read_text())
    except (OSError, ValueError) as error:
        raise ValueError("Required conformance results are missing or unreadable") from error
    if not isinstance(entries, list) or not entries:
        raise ValueError("Required conformance results must be a nonempty list")

    executed = 0
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("Malformed conformance result")
        if entry.get("producer") == "suite-availability":
            raise ValueError("Required conformance suite was unavailable")
        if entry.get("accepted") is not True or entry.get("status") not in ("passed", "skipped"):
            raise ValueError("Conformance results contain an unsuccessful or malformed case")
        if entry["status"] == "passed":
            test_id = entry.get("testId")
            if not isinstance(test_id, str) or not test_id.strip() or test_id == "N/A":
                raise ValueError("Passed conformance case has no suite test ID")
            executed += 1
    if not executed:
        raise ValueError("Required conformance run did not execute any accepted cases")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    args = parser.parse_args()
    try:
        check_results(args.results)
    except ValueError as error:
        # Do not echo report contents: suite errors can contain replayable URLs.
        print(f"::error::{error}")
        return 1
    print("Required conformance results contain executed, accepted cases.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
