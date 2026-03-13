#!/usr/bin/env python3
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTENT_DIR = ROOT / "src/content/1.iso-mdoc-credentials"
DATA_ROOT = ROOT / "src/data/iso-mdoc-doctypes"
INPUT_DIRS = [
    DATA_ROOT / "multipaz-doctypes",
    DATA_ROOT / "asit-doctypes",
    DATA_ROOT / "waltid-doctypes",
]
SOURCE_MAP = {
    "multipaz-doctypes": "https://github.com/openwallet-foundation/multipaz",
    "asit-doctypes": "https://github.com/a-sit-plus",
    "waltid-doctypes": "https://github.com/walt-id/",
}


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")


def main() -> None:
    # remove previously generated files
    for file in CONTENT_DIR.glob("*.md"):
        if file.name.startswith(("multipaz-", "asit-", "waltid-")):
            file.unlink()

    generated = 0

    for input_dir in INPUT_DIRS:
        if not input_dir.exists():
            continue

        prefix = input_dir.name.replace("-doctypes", "")
        source = SOURCE_MAP[input_dir.name]

        for json_file in sorted(input_dir.glob("*.json")):
            data = json.loads(json_file.read_text())
            doc_type = data.get("docType", json_file.stem)
            name = data.get("name", doc_type)
            fields = data.get("fields", [])

            grouped = {}
            for field in fields:
                field_name = field.get("name")
                if not field_name:
                    continue
                namespace = field.get("namespace") or "unknown"
                grouped.setdefault(namespace, {})[field_name] = field.get("sampleValue", None)

            lines = [
                f"# {name}",
                "",
                "## Source",
                "",
                f"- Dataset source: {source}",
                "",
                "## Document Type",
                "",
                f"- `docType`: `{doc_type}`",
                f"- `fields`: `{len(fields)}`",
                "",
                "## Example JSON (from sample values)",
                "",
                "```json",
                json.dumps({"docType": doc_type, "credentialSubject": grouped}, indent=2, ensure_ascii=False),
                "```",
                "",
                "## Fields",
                "",
            ]

            for field in fields:
                lines.extend(
                    [
                        f"- **{field.get('name')}**",
                        f"  - namespace: `{field.get('namespace')}`",
                        f"  - mandatory: `{field.get('mandatory')}`",
                        f"  - sampleValue: `{json.dumps(field.get('sampleValue'), ensure_ascii=False)}`",
                    ]
                )

            out_file = CONTENT_DIR / f"{prefix}-{slug(doc_type)}.md"
            out_file.write_text("\n".join(lines) + "\n")
            generated += 1

    print(f"Generated {generated} markdown files in {CONTENT_DIR}")


if __name__ == "__main__":
    main()
