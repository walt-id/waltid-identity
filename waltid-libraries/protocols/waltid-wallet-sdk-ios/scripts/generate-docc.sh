#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_PATH="${1:-$PACKAGE_DIR/build/docc/WalletSDK.doccarchive}"
OUTPUT_PARENT="$(dirname "$OUTPUT_PATH")"
COVERAGE_FILE="$OUTPUT_PATH/documentation-coverage.json"

rm -rf "$OUTPUT_PATH"
mkdir -p "$OUTPUT_PARENT"

swift package \
  --package-path "$PACKAGE_DIR" \
  --allow-writing-to-directory "$OUTPUT_PARENT" \
  generate-documentation \
  --target WalletSDK \
  --output-path "$OUTPUT_PATH" \
  --warnings-as-errors \
  --analyze \
  --experimental-documentation-coverage \
  --coverage-summary-level brief

python3 - "$COVERAGE_FILE" <<'PY'
import json
import sys

coverage_path = sys.argv[1]
with open(coverage_path, encoding="utf-8") as coverage_file:
    entries = json.load(coverage_file)

ignored_kinds = {
    "CollectionGroup",
    "Operator",
}
ignored_titles = {
    "assertIsolated(_:file:line:)",
    "assumeIsolated(_:file:line:)",
    "hash(into:)",
    "hashValue",
    "init(rawValue:)",
    "localizedDescription",
    "preconditionIsolated(_:file:line:)",
    "withSerialExecutor(_:)",
}


def is_owned_symbol(entry):
    kind = entry.get("kind", {})
    if not kind.get("isSymbol", False):
        return False
    if kind.get("name") in ignored_kinds:
        return False
    title = entry.get("title", "")
    if title in ignored_titles:
        return False
    if title.startswith("withSerialExecutor("):
        return False
    reference_path = entry.get("referencePath", "")
    return reference_path.startswith("doc://WalletSDK/documentation/WalletSDK/")


def parameter_coverage(entry):
    kind_data = entry.get("kindSpecificData") or {}
    associated = kind_data.get("associatedValue")
    if not isinstance(associated, dict):
        return None
    total = associated.get("total", 0)
    documented = associated.get("documented", 0)
    if total == 0:
        return None
    return documented, total


missing_abstracts = []
missing_parameters = []

for entry in entries:
    if not is_owned_symbol(entry):
        continue

    title = entry.get("title", "<unknown>")
    kind = entry.get("kind", {}).get("name", "<unknown>")
    reference_path = entry.get("referencePath", "<unknown>")

    if not entry.get("hasAbstract", False):
        missing_abstracts.append(f"{kind}: {title} ({reference_path})")

    parameter_counts = parameter_coverage(entry)
    if parameter_counts:
        documented, total = parameter_counts
        if documented != total:
            missing_parameters.append(
                f"{kind}: {title} documents {documented}/{total} parameters ({reference_path})"
            )

if missing_abstracts or missing_parameters:
    print("DocC coverage gate failed for WalletSDK.", file=sys.stderr)
    if missing_abstracts:
        print("\nMissing symbol abstracts:", file=sys.stderr)
        for item in missing_abstracts:
            print(f"  - {item}", file=sys.stderr)
    if missing_parameters:
        print("\nMissing parameter documentation:", file=sys.stderr)
        for item in missing_parameters:
            print(f"  - {item}", file=sys.stderr)
    sys.exit(1)

print(f"DocC coverage gate passed: {coverage_path}")
PY
