#!/usr/bin/env python3
"""Export the last conformance test run into a committable markdown snapshot.

Works for any role that shares ConformanceReportWriter's flat Entry schema (verifier, VCI wallet,
VP wallet - not the issuer role, which nests modules per variant; see export-issuer-results.py for
that one). Reads a results.json written by ConformanceReportWriter and writes a markdown snapshot
so results are tracked in git instead of only living in the gitignored build/ directory.

Entries whose name follows the verifier convention ("<variant> / <module>") are grouped by
variant/profile; entries whose name is "<producer>/<module>" (the VP-Wallet matrix convention,
where `producer` is itself a report field) are grouped by producer instead; entries with a flat
name (e.g. the other wallet roles' "<description>#<n>") get a single flat table. Originally written
for VerifierConformanceTests hence the filename and the VP-Verifier defaults - pass
--results/--output/--title to point it at a different role.

Usage:
    ./export-verifier-results.py [--results PATH] [--output PATH] [--title TITLE] [--note "context"]
"""

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

MODULE_ROOT = Path(__file__).resolve().parent
DEFAULT_RESULTS = MODULE_ROOT / "build/reports/openid-conformance/vp-verifier/results.json"
DEFAULT_OUTPUT = MODULE_ROOT / "docs/VP-VERIFIER-RESULTS.md"

STATUS_EMOJI = {"passed": "✅", "failed": "❌", "skipped": "⏭️"}


def git(*args: str) -> str | None:
    try:
        return subprocess.check_output(
            ["git", "-C", str(MODULE_ROOT), *args], text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def load_results(path: Path) -> list[dict]:
    if not path.is_file():
        sys.exit(
            f"error: {path} not found - run the suite first:\n"
            '  ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test '
            '--tests "VerifierConformanceTests" --rerun'
        )
    return json.loads(path.read_text())


def profile_label(producer: str) -> str:
    """Turn a VP-Wallet matrix producer id into a short, readable profile heading.

    `producer` looks like "oid4vp-1final-wallet-test-plan/client_id_prefix=redirect_uri,
    credential_format=sd_jwt_vc,...". The plan name only distinguishes HAIP from plain VP, and the
    param names are implied by their position, so keep just those two things.
    """
    plan, _, params = producer.partition("/")
    kind = "HAIP" if "haip" in plan else "plain VP"
    values = [kv.partition("=")[2] for kv in params.split(",") if "=" in kv]
    return f"{kind}: " + " · ".join(values) if values else producer


def group_by_profile(entries: list[dict]) -> tuple[dict[str, list[dict]], list[dict], list[dict]]:
    """Split into (variant/module groups, flat-named test cases, pure housekeeping entries).

    Three name conventions in use across roles:
    - Verifier: "<variant> / <module>" (space-slash-space) - group by variant.
    - VP-Wallet: "<producer>/<module>" where `producer` is itself an entry field identifying the
      matrix point - group by producer, using [profile_label] for a readable heading.
    - Other wallet roles: flat "<description>#<n>" - no separate profile axis, single flat table.
    The single "conformance-suite" availability entry ConformanceReportWriter always emits is kept
    out of all three (see writeSkippedIfEmpty) - it's a fixed housekeeping marker, not a real
    result.
    """
    profiles: dict[str, list[dict]] = {}
    flat: list[dict] = []
    housekeeping: list[dict] = []
    for entry in entries:
        name = entry.get("name", "")
        producer = entry.get("producer", "")
        if name == "conformance-suite" and producer == "suite-availability":
            housekeeping.append(entry)
        elif " / " in name:
            profile, case = name.split(" / ", 1)
            profiles.setdefault(profile, []).append({**entry, "case": case})
        elif producer and name.startswith(producer + "/"):
            case = name[len(producer) + 1:]
            profiles.setdefault(profile_label(producer), []).append({**entry, "case": case})
        else:
            flat.append(entry)
    return profiles, flat, housekeeping


def render(entries: list[dict], note: str | None, title: str) -> str:
    profiles, flat, housekeeping = group_by_profile(entries)

    grouped_cases = [c for cases in profiles.values() for c in cases]
    all_cases = grouped_cases + flat
    total = len(all_cases)
    passed = sum(1 for c in all_cases if c["status"] == "passed")
    failed = sum(1 for c in all_cases if c["status"] == "failed")
    skipped = total - passed - failed

    branch = git("rev-parse", "--abbrev-ref", "HEAD") or "unknown"
    commit = git("rev-parse", "--short", "HEAD") or "unknown"
    dirty = bool(git("status", "--porcelain", "."))  # scoped to this module, not the whole monorepo
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    lines = [
        f"# {title}",
        "",
        "Auto-generated by `export-verifier-results.py` - do not hand-edit, rerun the script instead.",
        "",
        f"- Generated: {generated}",
        f"- Branch: `{branch}` @ `{commit}`" + (" (module has uncommitted changes)" if dirty else ""),
    ]
    if note:
        lines.append(f"- Note: {note}")
    profile_note = f" across {len(profiles)} profiles" if profiles else ""
    lines += [
        "",
        f"**Summary:** {passed} passed, {failed} failed, {skipped} skipped out of {total} test "
        f"cases{profile_note}.",
        "",
    ]

    for profile, cases in profiles.items():
        p_passed = sum(1 for c in cases if c["status"] == "passed")
        lines.append(f"## {profile} ({p_passed}/{len(cases)} passed)")
        lines.append("")
        lines.append("| Test Case | Status | Suite Result | Log |")
        lines.append("|---|---|---|---|")
        for c in cases:
            emoji = STATUS_EMOJI.get(c["status"], "?")
            suite_result = c.get("suiteResult", "-")
            log_url = c.get("logUrl")
            log_cell = f"[log]({log_url})" if log_url else "-"
            lines.append(f"| `{c['case']}` | {emoji} {c['status']} | {suite_result} | {log_cell} |")
        lines.append("")

    if flat:
        lines.append("## Test Cases")
        lines.append("")
        lines.append("| Test Case | Status | Suite Result | Log | Error |")
        lines.append("|---|---|---|---|---|")
        for c in flat:
            emoji = STATUS_EMOJI.get(c["status"], "?")
            suite_result = c.get("suiteResult", "-")
            log_url = c.get("logUrl")
            log_cell = f"[log]({log_url})" if log_url else "-"
            error = (c.get("error") or "").replace("\n", " ").replace("|", "\\|")
            lines.append(f"| `{c['name']}` | {emoji} {c['status']} | {suite_result} | {log_cell} | {error} |")
        lines.append("")

    if housekeeping:
        lines.append("<details><summary>Other report entries</summary>")
        lines.append("")
        for entry in housekeeping:
            lines.append(f"- `{entry.get('name')}`: {entry.get('status')} - {entry.get('error', '')}")
        lines.append("")
        lines.append("</details>")
        lines.append("")

    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS, help="path to results.json")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="markdown file to write")
    parser.add_argument("--title", default="VP-Verifier Conformance Results", help="H1 title for the generated doc")
    parser.add_argument("--note", help="one-line context for this run, e.g. what changed since the last export")
    args = parser.parse_args()

    entries = load_results(args.results)
    markdown = render(entries, args.note, args.title)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown + "\n")
    print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
