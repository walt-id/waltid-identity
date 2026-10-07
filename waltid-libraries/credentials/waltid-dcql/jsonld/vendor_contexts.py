#!/usr/bin/env python3
"""Refresh the vendored JSON-LD contexts and their provenance record.

Run from this directory, or pass no arguments from anywhere:

    python3 jsonld/vendor_contexts.py
    python3 jsonld/vendor_contexts.py --check

The build task generateBundledJsonLdContexts reads these files. It fails when a
file digest does not match provenance.json.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import urllib.request
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CONTEXTS = ROOT / "contexts"
PROVENANCE = ROOT / "provenance.json"
NOTE = ROOT / "PROVENANCE.md"

ACCEPT = {"Accept": "application/ld+json, application/json"}


def load() -> dict:
    return json.loads(PROVENANCE.read_text())


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fetch(document: dict) -> None:
    request = urllib.request.Request(document["source"], headers=ACCEPT)
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()
    path = CONTEXTS / document["file"]
    path.write_bytes(data)
    document["sha256"] = sha256(data)
    document["retrieved"] = date.today().isoformat()
    parsed = json.loads(data)
    comments = parsed.get("comments") if isinstance(parsed, dict) else None
    if isinstance(comments, dict) and comments.get("generation_date"):
        document["generationDate"] = comments["generation_date"]


def check(document: dict) -> None:
    path = CONTEXTS / document["file"]
    actual = sha256(path.read_bytes())
    if actual != document["sha256"]:
        raise SystemExit(f"{path.name} sha256 {actual} does not match provenance {document['sha256']}")


def render(provenance: dict) -> None:
    lines = [
        "# Vendored JSON-LD contexts",
        "",
        "Type expansion reads these snapshots instead of fetching context URLs during presentation.",
        "`THIRD-PARTY-NOTICE.md` lists Gradle dependencies only, so it does not cover these documents.",
        "",
        "Refresh them with `python3 jsonld/vendor_contexts.py` from `waltid-dcql`.",
        "The `generateBundledJsonLdContexts` build task checks each file against the digest in `provenance.json`.",
        "",
    ]
    for document in provenance["documents"]:
        lines.append(f"## {document['file']}")
        lines.append("")
        lines.append(f"- Source: {document['source']}")
        for alias in document.get("aliases") or []:
            lines.append(f"- Also served as: {alias}")
        lines.append(f"- Retrieved: {document['retrieved']}")
        if document.get("generationDate"):
            lines.append(f"- Document generation date: {document['generationDate']}")
        lines.append(f"- SHA-256: `{document['sha256']}`")
        licence = document["licence"]
        if document.get("licenceUrl"):
            licence = f"[{licence}]({document['licenceUrl']})"
        lines.append(f"- Licence: {licence}")
        lines.append("")
    NOTE.write_text("\n".join(lines))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify digests without fetching")
    args = parser.parse_args()
    provenance = load()
    for document in provenance["documents"]:
        if args.check:
            check(document)
        else:
            fetch(document)
    if not args.check:
        PROVENANCE.write_text(json.dumps(provenance, indent=2) + "\n")
        render(provenance)


if __name__ == "__main__":
    main()
