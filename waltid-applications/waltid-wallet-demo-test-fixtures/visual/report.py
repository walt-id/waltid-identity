#!/usr/bin/env python3
"""Index real visual-test results; never record or accept a baseline."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CATALOGUE = json.loads((HERE / "catalogue.json").read_text())


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args]).decode().strip()


def fingerprint():
    digest = hashlib.sha256()
    paths = git("ls-files", "-z", "--cached", "--others", "--exclude-standard").split("\0")
    for name in sorted(set(paths)):
        if not (name.startswith("waltid-applications/waltid-wallet-demo-") or
                name in ("gradle.properties", "gradle/libs.versions.toml", "settings.gradle.kts")):
            continue
        path = ROOT / name
        digest.update(name.encode())
        digest.update(path.read_bytes() if path.is_file() else b"<deleted>")
    return digest.hexdigest()


def fresh(path, started):
    return path.is_file() and path.stat().st_mtime >= started


def native_tests(path, started, output):
    if path is None or not fresh(path, started):
        return {}, {}, "Native result missing or older than this run"
    data = json.loads(path.read_text())
    if data.get("schema") != "xcodebuildmcp.output.test-result" or str(data.get("schemaVersion")) != "3":
        return {}, {}, "Unsupported native test-result schema"
    tests = {t["test"].removesuffix("()"): t for t in data["data"].get("testCases", [])
             if t["suite"] == CATALOGUE["renderers"]["swiftui"]["suite"]}
    results = {}
    if any(case["status"] == "failed" for case in tests.values()):
        bundle = Path(data["data"].get("artifacts", {}).get("xcresultPath", "")).expanduser()
        if not bundle.is_dir():
            return tests, results, "Failed native run has no accessible xcresult bundle"
        export = output / "native-attachments"
        try:
            # SnapshotTesting attachments are test activities, not isAssociatedWithFailure items.
            subprocess.run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(bundle),
                            "--output-path", str(export), "--test-id", CATALOGUE["renderers"]["swiftui"]["suite"]],
                           check=True, capture_output=True, text=True)
            for case in json.loads((export / "manifest.json").read_text()):
                test = case["testIdentifier"].split("/")[-1].removesuffix("()")
                result = {}
                for attachment in case["attachments"]:
                    name = attachment["suggestedHumanReadableName"]
                    key = ("actual_file_path" if name.startswith("failure_") else
                           "compare_file_path" if name.startswith("difference_") else None)
                    if key:
                        if key in result:
                            return tests, results, f"Multiple native snapshots in {test}; catalogue needs separate test IDs"
                        result[key] = str(export / attachment["exportedFileName"])
                results[test] = result
        except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
            return tests, results, f"Could not export native comparison artifacts: {type(error).__name__}"
    return tests, results, None


def compose_tests(renderer, started):
    junit = ROOT / renderer["junit"]
    if not fresh(junit, started):
        return {}, {}, "JUnit missing or older than this run (force the test task to execute)"
    suite = ET.parse(junit).getroot()
    if suite.attrib.get("name") != renderer["suite"]:
        return {}, {}, "Unexpected JUnit suite"
    tests = {t.attrib["name"].split("[")[0]: {
        "status": "passed" if not any(t.find(tag) is not None for tag in ("failure", "error", "skipped")) else "failed",
        "durationMs": float(t.attrib.get("time", 0)) * 1000,
    } for t in suite.findall("testcase")}
    results = {}
    for path in sorted((ROOT / renderer["resultDirectory"]).glob("*.json"), key=lambda p: p.stat().st_mtime):
        if fresh(path, started):
            value = json.loads(path.read_text())
            results[Path(value["golden_file_path"]).name] = value
    return tests, results, None


def finish(args, run):
    errors = []
    if run["fingerprint"] != fingerprint() or run["sha"] != git("rev-parse", "HEAD"):
        errors.append("Source or baselines changed after begin; results cannot describe this source state")
    cells = []
    for name in run["renderers"]:
        renderer = CATALOGUE["renderers"][name]
        if name == "swiftui":
            tests, results, error = native_tests(args.native_results, run["started"], args.output)
        else:
            tests, results, error = compose_tests(renderer, run["started"])
        if error:
            errors.append(f"{name}: {error}")
        expected = set()
        for state in CATALOGUE["states"]:
            test = state.get("swiftTest" if name == "swiftui" else "composeTest")
            if test is None:
                continue
            filename = (state["id"].replace(".", "-") + renderer["suffix"] if name == "swiftui" else state["id"]) + ".png"
            expected.add(filename)
            baseline = ROOT / renderer["baselineDirectory"] / filename
            case = tests.get(test, {})
            result = results.get(test if name == "swiftui" else filename, {})
            status = "missing"
            if baseline.is_file() and case:
                status = "passed" if case["status"] == "passed" and (name == "swiftui" or result.get("type") == "unchanged") else "failed"
            links = {}
            for kind, source in [("expected", baseline), ("actual", result.get("actual_file_path")),
                                 ("diff", result.get("compare_file_path"))]:
                if source and Path(source).is_file():
                    relative = Path("images") / name / kind / filename
                    target = args.output / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
                    links[kind] = relative.as_posix()
            cells.append({"id": state["id"], "renderer": name, "status": status,
                          "requirements": state["requirements"], "note": state.get("note"),
                          "test": test, "durationMs": case.get("durationMs"),
                          "comparison": result.get("type", "native assertion" if case else "missing"),
                          "artifacts": links})
        orphans = {p.name for p in (ROOT / renderer["baselineDirectory"]).glob("*.png")} - expected
        if orphans:
            errors.append(f"{name}: unlisted baselines: {', '.join(sorted(orphans))}")
    counts = dict(Counter(cell["status"] for cell in cells))
    manifest = {**run, "finished": datetime.now(timezone.utc).isoformat(), "counts": counts, "errors": errors, "cells": cells}
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    render_html(args.output, manifest)
    print(json.dumps({"counts": counts, "errors": errors, "report": str(args.output / "index.html")}, indent=2))
    return 1 if errors or any(cell["status"] != "passed" for cell in cells) else 0


def render_html(output, manifest):
    escape = html.escape
    cards = []
    for cell in manifest["cells"]:
        images = " ".join(f'<a href="{escape(path)}">{escape(kind)}</a>' for kind, path in cell["artifacts"].items())
        thumbnail = cell["artifacts"].get("actual", cell["artifacts"].get("expected"))
        picture = f'<a href="{escape(thumbnail)}"><img loading="lazy" src="{escape(thumbnail)}" alt="{escape(cell["id"])}"></a>' if thumbnail else "No image"
        cards.append(f'<article data-status="{cell["status"]}"><h2>{escape(cell["id"])}</h2><p>{escape(cell["renderer"])} · <strong>{cell["status"]}</strong></p>{picture}<p>{images}</p><p>{escape(", ".join(cell["requirements"]))}</p><p>{escape(cell.get("note") or "")}</p></article>')
    environments = "".join(f'<li>{escape(name)}: {escape(CATALOGUE["renderers"][name]["environment"])}</li>' for name in manifest["renderers"])
    errors = "".join(f'<li>{escape(error)}</li>' for error in manifest["errors"])
    (output / "index.html").write_text(f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Wallet visual evidence</title>
<style>body{{font:16px system-ui;background:#f5f6f8;color:#162032;margin:32px}}h1{{margin-bottom:8px}}main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:24px}}article{{padding:16px;background:white;border-radius:16px;border:1px solid #d8dde4}}h2{{font-size:15px;overflow-wrap:anywhere}}img{{max-width:100%;max-height:480px;display:block;margin:auto}}[data-status=failed],[data-status=missing]{{border:3px solid #b42318}}a{{color:#175cd3}}code{{overflow-wrap:anywhere}}</style>
<h1>Wallet visual evidence</h1><p>Source <code>{escape(manifest["sha"])}</code> · {"uncommitted changes" if manifest["dirty"] else "clean checkout"}</p><p>{escape(str(manifest["counts"]))}</p><ul>{errors}</ul><ul>{environments}</ul><p>Deterministic content fixtures. This report does not establish live protocol, system UI, physical-device or conformance coverage. <a href="manifest.json">Machine-readable manifest</a></p><main>{"".join(cards)}</main></html>''')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["begin", "finish"])
    parser.add_argument("--output", type=Path, required=True, help="Ignored build directory outside the fixture sources")
    parser.add_argument("--renderers", nargs="+", choices=list(CATALOGUE["renderers"]), default=list(CATALOGUE["renderers"]))
    parser.add_argument("--native-results", type=Path, help="XcodeBuildMCP simulator test --output json result")
    args = parser.parse_args()
    args.output = args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=True)
    marker = args.output / "run.json"
    if args.phase == "begin":
        marker.write_text(json.dumps({"schemaVersion": 1, "started": time.time(), "sha": git("rev-parse", "HEAD"),
                                    "dirty": bool(git("status", "--porcelain")), "fingerprint": fingerprint(),
                                    "renderers": args.renderers}, indent=2) + "\n")
        print(f"Started evidence run: {marker}")
        return 0
    return finish(args, json.loads(marker.read_text()))


if __name__ == "__main__":
    raise SystemExit(main())
