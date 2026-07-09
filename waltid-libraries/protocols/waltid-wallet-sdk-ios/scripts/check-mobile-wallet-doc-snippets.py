#!/usr/bin/env python3
"""Checks that mobile wallet docs embed the compiled persistence snippets."""

from __future__ import annotations

import difflib
import re
import sys
import textwrap
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[4]


@dataclass(frozen=True)
class Snippet:
    name: str
    source: Path
    docs: tuple[Path, ...]


SNIPPETS = (
    Snippet(
        "kotlin-default-persistence",
        REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonTest/kotlin/id/walt/wallet2/mobile/MobileWalletPersistenceSnippetsTest.kt",
        (REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md",),
    ),
    Snippet(
        "kotlin-provided-database-key",
        REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonTest/kotlin/id/walt/wallet2/mobile/MobileWalletPersistenceSnippetsTest.kt",
        (REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md",),
    ),
    Snippet(
        "kotlin-custom-credential-store",
        REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonTest/kotlin/id/walt/wallet2/mobile/MobileWalletPersistenceSnippetsTest.kt",
        (REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md",),
    ),
    Snippet(
        "kotlin-full-store-overrides",
        REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonTest/kotlin/id/walt/wallet2/mobile/MobileWalletPersistenceSnippetsTest.kt",
        (REPO_ROOT / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/README.md",),
    ),
    Snippet(
        "swift-provided-database-key",
        REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Tests/WalletSDKTests/WalletPersistenceSnippetsTests.swift",
        (
            REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md",
            REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/Documentation.docc/WalletSDK.md",
        ),
    ),
    Snippet(
        "swift-custom-credential-store",
        REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Tests/WalletSDKTests/WalletPersistenceSnippetsTests.swift",
        (REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md",),
    ),
    Snippet(
        "swift-full-store-overrides",
        REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Tests/WalletSDKTests/WalletPersistenceSnippetsTests.swift",
        (
            REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md",
            REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/Documentation.docc/WalletSDK.md",
            REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/Documentation.docc/GettingStarted.md",
        ),
    ),
    Snippet(
        "swift-combined-persistence",
        REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Tests/WalletSDKTests/WalletPersistenceSnippetsTests.swift",
        (REPO_ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/README.md",),
    ),
)


def normalize(value: str) -> str:
    return textwrap.dedent(value).strip("\n").rstrip()


def rel(path: Path) -> str:
    return str(path.relative_to(REPO_ROOT))


def extract_source(path: Path, name: str) -> str:
    text = path.read_text()
    pattern = re.compile(
        rf"^[ \t]*// doc-snippet:start {re.escape(name)}[ \t]*\n"
        rf"(?P<body>.*?)"
        rf"^[ \t]*// doc-snippet:end {re.escape(name)}[ \t]*$",
        re.MULTILINE | re.DOTALL,
    )
    match = pattern.search(text)
    if not match:
        raise ValueError(f"{rel(path)} is missing source snippet {name}")
    return normalize(match.group("body"))


def extract_doc(path: Path, name: str) -> str:
    text = path.read_text()
    marker = re.compile(
        rf"<!-- doc-snippet:start {re.escape(name)} -->"
        rf"(?P<body>.*?)"
        rf"<!-- doc-snippet:end {re.escape(name)} -->",
        re.DOTALL,
    )
    match = marker.search(text)
    if not match:
        raise ValueError(f"{rel(path)} is missing doc snippet {name}")

    code_blocks = re.findall(r"```[A-Za-z0-9_-]*\n(?P<body>.*?)\n```", match.group("body"), re.DOTALL)
    if len(code_blocks) != 1:
        raise ValueError(f"{rel(path)} doc snippet {name} must contain exactly one fenced code block")
    return normalize(code_blocks[0])


def compare(name: str, source_path: Path, doc_path: Path) -> str | None:
    source = extract_source(source_path, name)
    doc = extract_doc(doc_path, name)
    if source == doc:
        return None
    return "\n".join(
        difflib.unified_diff(
            source.splitlines(),
            doc.splitlines(),
            fromfile=f"{rel(source_path)}:{name}",
            tofile=f"{rel(doc_path)}:{name}",
            lineterm="",
        )
    )


def main() -> int:
    failures = []
    for snippet in SNIPPETS:
        for doc in snippet.docs:
            try:
                diff = compare(snippet.name, snippet.source, doc)
            except ValueError as error:
                failures.append(str(error))
                continue
            if diff:
                failures.append(diff)

    if failures:
        print("Mobile wallet doc snippets are out of date:", file=sys.stderr)
        print("\n\n".join(failures), file=sys.stderr)
        return 1

    print("Mobile wallet doc snippets are up to date.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
