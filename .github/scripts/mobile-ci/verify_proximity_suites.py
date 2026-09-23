#!/usr/bin/env python3
"""Verify every named proximity suite assigned to one platform in this source revision."""

import argparse
import json
from pathlib import Path

from verify_test_results import EvidenceError, verify


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", required=True,
                        choices=["jvm", "android-host", "ios-simulator", "js-node"])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[3])
    args = parser.parse_args()
    manifest = json.loads((Path(__file__).parent / "proximity-test-suites.json").read_text())
    results, errors = [], []
    for name, suite in manifest["suites"].items():
        pattern = suite.get("reportsByPlatform", {}).get(args.platform)
        if pattern is None:
            continue
        try:
            counts = verify(list(args.root.glob(pattern)), suite["requiredTests"], manifest["physicalClassPrefixes"])
            results.append({"suite": name, "platform": args.platform, **counts})
        except EvidenceError as error:
            errors.append(f"{name}: {error}")
    if errors or not results:
        parser.exit(1, "Test evidence rejected:\n" + "\n".join(errors or ["No suite assigned to platform"]) + "\n")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
