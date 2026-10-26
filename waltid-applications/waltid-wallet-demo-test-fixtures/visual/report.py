#!/usr/bin/env python3
"""Index fresh visual comparisons; never record or accept a baseline."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import html
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CATALOGUE = json.loads((HERE / "catalogue.json").read_text())


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def image_info(path):
    from PIL import Image, ImageCms
    if not isinstance(path, bytes) and Path(path).stat().st_size > 10 * 1024 * 1024:
        raise ValueError("Visual evidence exceeds 10 MiB")
    data = path if isinstance(path, bytes) else Path(path).read_bytes()
    if len(data) > 10 * 1024 * 1024:
        raise ValueError("Visual evidence exceeds 10 MiB")
    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.format != "PNG":
                raise ValueError("Visual evidence must be PNG")
            if image.width * image.height > 16_000_000:
                raise ValueError("Visual evidence exceeds the supported image size")
            image.load()
            profile = image.info.get("icc_profile")
            pixels = (ImageCms.profileToProfile(image, ImageCms.ImageCmsProfile(io.BytesIO(profile)),
                      ImageCms.createProfile("sRGB"), outputMode="RGBA") if profile else image.convert("RGBA"))
            return {"digest": hashlib.sha256(data).hexdigest(), "pixels": hashlib.sha256(pixels.tobytes()).hexdigest(),
                    "dimensions": list(image.size)}
    except (OSError, Image.DecompressionBombError, ImageCms.PyCMSError) as error:
        raise ValueError(f"Invalid visual PNG: {error}") from error


def source_inputs(gradle_root):
    """Retain the complete input inventory behind the source fingerprint, without file contents."""
    value = hashlib.sha256()
    repositories = []
    for root in dict.fromkeys((ROOT, Path(gradle_root))):
        value.update(b"\0repository\0")
        files = []
        paths = subprocess.check_output(
            ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"], text=True)
        for name in sorted(set(filter(None, paths.split("\0")))):
            path = root / name
            kind = "file" if path.is_file() else "directory" if path.is_dir() else "missing"
            content_digest = hashlib.sha256(path.read_bytes() if kind == "file" else b"<deleted>")
            value.update(name.encode() + b"\0")
            value.update(content_digest.digest())
            files.append({"path": name, "kind": kind, "sha256": content_digest.hexdigest()})
        repositories.append({"root": str(root), "files": files})
    return value.hexdigest(), repositories


def fingerprint(gradle_root):
    """Bind evidence to source, SDKs and build inputs, excluding ignored generated files."""
    return source_inputs(gradle_root)[0]


def fresh(path, started):
    return path is not None and Path(path).is_file() and Path(path).stat().st_mtime >= started


def runner_env(run):
    env = {"E2E_USE_MOCK_WALLET": "1", "WALLET_VISUAL_RECORD": "0", "TZ": "UTC",
           "WALLET_VISUAL_RUN_ID": run["runId"], "WALLET_VISUAL_SHA": run["sha"],
           "WALLET_VISUAL_FINGERPRINT": run["fingerprint"]}
    if any(key in os.environ for key in ("CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION")):
        env["CI"] = "true"
    return env


def native_attachments(export, tests):
    """Receipts and captures are associated by xcresult test activity, never by a guessed filename."""
    results = {}
    for case in json.loads((export / "manifest.json").read_text()):
        test = case["testIdentifier"].split("/")[-1].removesuffix("()")
        if test not in tests:
            raise ValueError(f"Attachment for an unexpected test: {test}")
        receipts, actuals, diagnostics, differences, readiness = [], {}, [], [], {}
        for item in case["attachments"]:
            path = (export / item["exportedFileName"]).resolve()
            if not path.is_relative_to(export.resolve()) or not path.is_file():
                raise ValueError("Invalid native attachment path")
            name = item["suggestedHumanReadableName"]
            if name.startswith("wallet-visual-receipt-"):
                receipt = json.loads(path.read_text())
                receipts.append({**receipt, "receipt_file_path": str(path)})
            elif name.startswith("wallet-visual-readiness-"):
                value = json.loads(path.read_text())
                readiness[value["id"]] = value
            elif name.startswith("wallet-visual-actual-"):
                actuals[digest(path)] = str(path)
            elif name.startswith("readiness-"):
                diagnostics.append(str(path))
            elif name.startswith("difference_"):
                differences.append(str(path))
        for receipt in receipts:
            if receipt["id"] in results:
                raise ValueError(f"Multiple comparisons for {receipt['id']}; use distinct catalogue cells")
            results[receipt["id"]] = {**receipt, "test": test,
                "actual_file_path": actuals.get(receipt.get("actualDigest")),
                "readiness": readiness.get(receipt["id"]),
                "compare_file_path": differences[0] if len(differences) == 1 else None}
        if diagnostics:
            # A readiness failure can occur before a receipt exists. Retain the phase and image.
            results[f"readiness:{test}"] = {"diagnostic_file_path": diagnostics[0],
                "diagnostic": tests[test].get("failureMessage", "Readiness failed before comparison"),
                "readiness": next(iter(readiness.values()), None)}
    return results


def native_test_cases(data, suite):
    """Use the complete xcresult tree; CLI summaries can omit executed cases."""
    tests = {}

    def visit(nodes):
        for node in nodes:
            identifier = node.get("nodeIdentifier", "")
            if node["nodeType"] == "Test Case" and identifier.startswith(suite + "/"):
                test = identifier.removeprefix(suite + "/").removesuffix("()")
                if not test or "/" in test or test in tests:
                    raise ValueError(f"Invalid or duplicate native test: {identifier}")
                status = {"Passed": "passed", "Failed": "failed", "Skipped": "skipped"}.get(node.get("result"))
                if status is None:
                    raise ValueError(f"Unsupported native test result: {node.get('result')}")
                tests[test] = {"status": status, "durationMs": node.get("durationInSeconds", 0) * 1000}
                failures = [child["name"] for child in node.get("children", [])
                            if child["nodeType"] == "Failure Message"]
                if failures:
                    tests[test]["failureMessage"] = "\n".join(failures)
            visit(node.get("children", []))

    visit(data["testNodes"])
    return tests


def native_tests(path, run, output):
    if not fresh(path, run["started"]):
        return {}, {}, "Native result missing or older than this run"
    try:
        data = json.loads(path.read_text())
        if data.get("schema") != "xcodebuildmcp.output.test-result" or str(data.get("schemaVersion")) != "3":
            return {}, {}, "Unsupported native test-result schema"
        suite = CATALOGUE["renderers"]["swiftui"]["suite"]
        bundle = Path(data["data"].get("artifacts", {}).get("xcresultPath", "")).expanduser()
        if not bundle.is_dir():
            return {}, {}, "Native run has no accessible xcresult bundle"
        result = subprocess.run(["xcrun", "xcresulttool", "get", "test-results", "tests", "--path", str(bundle)],
                                check=True, capture_output=True, text=True)
        (output / "native-tests.json").write_text(result.stdout)
        tests = native_test_cases(json.loads(result.stdout), suite)
        export = output / "native-attachments" / run["runId"]
        subprocess.run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(bundle),
                        "--output-path", str(export), "--test-id", suite], check=True, capture_output=True, text=True)
        return tests, native_attachments(export, tests), None
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        return {}, {}, f"Could not read native comparison artifacts: {error}"


def compose_tests(renderer, started):
    junit = ROOT / renderer["junit"]
    if not fresh(junit, started):
        return {}, {}, "JUnit missing or older than this run (force the test task to execute)"
    suite = ET.parse(junit).getroot()
    if suite.attrib.get("name") != renderer["suite"]:
        return {}, {}, "Unexpected JUnit suite"
    tests = {t.attrib["name"].split("[")[0]: {
        "status": "skipped" if t.find("skipped") is not None else
                  "failed" if any(t.find(tag) is not None for tag in ("failure", "error")) else "passed",
        "durationMs": float(t.attrib.get("time", 0)) * 1000,
    } for t in suite.findall("testcase")}
    results = {}
    for path in (ROOT / renderer["resultDirectory"]).glob("*.json"):
        if fresh(path, started):
            value = json.loads(path.read_text())
            filename = Path(value["golden_file_path"]).name
            if filename in results:
                return tests, results, f"Multiple fresh results for {filename}"
            if Path(value["golden_file_path"]).resolve() != (ROOT / renderer["baselineDirectory"] / filename).resolve():
                return tests, results, f"Comparison used a different baseline directory: {filename}"
            results[filename] = value
    return tests, results, None


def configuration(renderer, state):
    return {**renderer.get("configuration", {}), **state.get("configuration", {}).get(renderer["id"], {})}


def assess_native(state, case, result, baseline, run, config):
    if not case:
        return "missing", "test", "Native test result is missing"
    if case.get("status") == "skipped":
        return "skipped", "test", "Native test skipped"
    if not baseline.is_file():
        return "missing-baseline", "comparison", "Reviewed baseline is missing"
    if not result:
        return "missing", "comparison", "No comparison receipt"
    if result.get("mode") == "record" or result.get("result") == "recorded":
        return "recorded", "comparison", "Recording is not verification"
    if any(result.get(key) != run[key] for key in ("runId", "sha", "fingerprint")):
        return "stale", "provenance", "Receipt does not describe this source and run"
    if type(result.get("schemaVersion")) is not int or result["schemaVersion"] != 1 or result.get("id") != state["id"] or result.get("test") != state["swiftTest"] or result.get("renderer") != "swiftui" or result.get("kind") != "deterministic" or result.get("mode") != "verify":
        return "invalid", "provenance", "Unsupported or mismatched comparison receipt"
    if result.get("baselineDigest") != digest(baseline):
        return "stale", "provenance", "Receipt baseline digest differs from the reviewed reference"
    try:
        actual = image_info(result["actual_file_path"])
        reference = image_info(baseline)
        if actual["digest"] != result["actualDigest"] or actual["dimensions"] != reference["dimensions"]:
            raise ValueError("Compared capture digest or dimensions do not match")
        viewport = result["viewport"]
        if actual["dimensions"] != [round(v * result["scale"]) for v in viewport]:
            raise ValueError("Capture dimensions do not match its viewport and scale")
        for key in ("viewport", "scale", "theme", "textSize", "locale", "runtime", "layoutDirection"):
            if key in config and result.get(key) != config[key]:
                raise ValueError(f"Capture configuration differs: {key}")
        if result.get("timezone") not in ("UTC", "GMT", "Etc/UTC"):
            raise ValueError("Capture timezone must be UTC")
    except (OSError, ValueError, KeyError, TypeError) as error:
        return "invalid", "provenance", str(error)
    if result.get("compared") is not True:
        return "failed", "capture", result.get("diagnostic") or "Snapshot never reached pixel comparison"
    if result.get("result") != "passed" or case.get("status") != "passed":
        return "failed", "comparison", result.get("diagnostic") or "Native test or comparison failed"
    return "passed", "comparison", ""


def assess_compose(state, renderer, case, result, baseline, run, config):
    if case.get("status") == "skipped":
        return "skipped", "test", "Compose test skipped"
    if not baseline.is_file():
        return "missing-baseline", "comparison", "Reviewed baseline is missing"
    if not case or not result:
        return "missing", "comparison", "Fresh test or comparison result is missing"
    if result.get("type") == "recorded":
        return "recorded", "comparison", "Recording is not verification"
    metadata = result.get("context_data", {})
    if any(metadata.get(key) != run[key] for key in ("runId", "sha", "fingerprint")):
        return "stale", "provenance", "Comparison does not describe this source and run"
    if metadata.get("id") != state["id"] or metadata.get("renderer") != renderer or metadata.get("kind") != "deterministic":
        return "invalid", "provenance", "Comparison belongs to a different catalogue cell"
    actual_config = {**metadata, "viewport": [metadata.get("viewportWidth"), metadata.get("viewportHeight")]}
    if any(actual_config.get(key) != value for key, value in config.items()):
        return "invalid", "provenance", "Comparison configuration differs from the catalogue"
    if case.get("status") != "passed" or result.get("type") != "unchanged":
        return "failed", "comparison", "Compose test or pixel comparison failed"
    return "passed", "comparison", ""


def finish(args, run):
    errors, cells = [], []
    try:
        stale_inventory = run.get("sourceInputsDigest") != digest(args.output / "source-inputs.json")
    except OSError:
        stale_inventory = True
    if stale_inventory:
        errors.append("Source input manifest is missing or changed after begin")
    stale_source = (stale_inventory or run.get("schemaVersion") != 4 or run["sha"] != git("rev-parse", "HEAD") or
                    run["fingerprint"] != fingerprint(run["gradleRoot"]))
    if stale_source:
        errors.append("Source or baselines changed after begin; results cannot describe this source state")
    for name in run["renderers"]:
        renderer = {"id": name, **CATALOGUE["renderers"][name]}
        try:
            tests, results, error = (native_tests(args.native_results, run, args.output) if name == "swiftui"
                                     else compose_tests(renderer, run["started"]))
        except (OSError, ValueError, KeyError, ET.ParseError) as error:
            tests, results, error = {}, {}, str(error)
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
            result = results.get(state["id"] if name == "swiftui" else filename, {})
            config = configuration(renderer, state)
            phase, diagnostic = "comparison", ""
            if stale_source:
                status, phase = "stale", "provenance"
            elif name == "swiftui":
                status, phase, diagnostic = assess_native(state, case, result, baseline, run, config)
                readiness = results.get(f"readiness:{test}", {})
                if readiness and not result:
                    result = readiness
                    status, phase, diagnostic = "failed", "readiness", readiness["diagnostic"]
            else:
                status, phase, diagnostic = assess_compose(state, name, case, result, baseline, run, config)
            links, images = {}, {}
            for kind, source in [("expected", baseline), ("actual", result.get("actual_file_path")),
                                 ("diff", result.get("compare_file_path")), ("readiness", result.get("diagnostic_file_path"))]:
                if source and Path(source).is_file():
                    try:
                        info = image_info(source)
                    except (OSError, ValueError) as error:
                        status, phase, diagnostic = "invalid", "provenance", str(error)
                        continue
                    relative = Path("images") / name / kind / filename
                    target = args.output / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
                    links[kind], images[kind] = relative.as_posix(), info
            cells.append({"id": state["id"], "renderer": name, "kind": "deterministic", "status": status,
                          "phase": phase, "diagnostic": diagnostic, "configuration": config,
                          "requirements": state["requirements"], "note": state.get("note"),
                          "test": test, "durationMs": case.get("durationMs"), "receipt": result if name == "swiftui" else None, "capture": result.get("context_data"),
                          "comparison": result.get("result", result.get("type", "missing")), "artifacts": links, "images": images})
        orphans = {p.name for p in (ROOT / renderer["baselineDirectory"]).glob("*.png")} - expected
        if orphans:
            errors.append(f"{name}: unlisted baselines: {', '.join(sorted(orphans))}")
    manifest = {**run, "finished": datetime.now(timezone.utc).isoformat(),
                "counts": dict(Counter(cell["status"] for cell in cells)), "errors": errors, "cells": cells}
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    render_html(args.output, manifest)
    print(json.dumps({"counts": manifest["counts"], "errors": errors, "report": str(args.output / "index.html")}, indent=2))
    return 1 if errors or not cells or any(cell["status"] != "passed" for cell in cells) else 0


def render_html(output, manifest):
    escape, cards = html.escape, []
    for cell in manifest["cells"]:
        images = " ".join(f'<a href="{escape(path)}">{escape(kind)}</a>' for kind, path in cell["artifacts"].items())
        thumbnail = cell["artifacts"].get("readiness", cell["artifacts"].get("actual", cell["artifacts"].get("expected")))
        picture = f'<a href="{escape(thumbnail)}"><img loading="lazy" src="{escape(thumbnail)}" alt="{escape(cell["id"])}"></a>' if thumbnail else "No image"
        cards.append(f'<article data-status="{cell["status"]}" data-renderer="{cell["renderer"]}" data-kind="{cell["kind"]}"><h2>{escape(cell["id"])}</h2><p>{escape(cell["renderer"])} · <strong>{cell["status"]}</strong> · {cell["phase"]}</p>{picture}<p>{images}</p><p>{escape(cell["diagnostic"])}</p><p>{escape(json.dumps(cell["configuration"]))}</p><p>{escape(", ".join(cell["requirements"]))}</p><p>{escape(cell.get("note") or "")}</p></article>')
    def options(values):
        return '<option value="">All</option>' + ''.join(f'<option>{escape(v)}</option>' for v in sorted(values))
    filters = ''.join(f'<label>{label} <select id="{key}">{options({c[key] for c in manifest["cells"]})}</select></label> ' for key, label in [("renderer", "Platform"), ("status", "Status"), ("kind", "Evidence")])
    errors = ''.join(f'<li>{escape(error)}</li>' for error in manifest["errors"])
    (output / "index.html").write_text(f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Wallet visual evidence</title>
<style>body{{font:16px system-ui;background:#f5f6f8;color:#162032;margin:32px}}main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:24px}}article{{padding:16px;background:white;border-radius:16px;border:1px solid #d8dde4}}h2{{font-size:15px;overflow-wrap:anywhere}}img{{max-width:100%;max-height:480px;display:block;margin:auto}}article:not([data-status=passed]){{border:3px solid #b42318}}a{{color:#175cd3}}code,p{{overflow-wrap:anywhere}}[hidden]{{display:none}}</style>
<h1>Wallet visual evidence</h1><p>Tested commit <code>{escape(manifest["sha"])}</code> · {"uncommitted changes" if manifest["dirty"] else "clean checkout"} · run <code>{manifest["runId"]}</code></p><p>{escape(str(manifest["counts"]))}</p><ul>{errors}</ul>
<p>Deterministic fixtures do not establish backend, system UI, physical-device or conformance coverage. <a href="manifest.json">Full provenance</a></p>
<p><label>Scenario <input id="scenario" type="search"></label> {filters}</p><main>{"".join(cards)}</main>
<script>document.querySelectorAll('select,input').forEach(control=>control.addEventListener('input',()=>document.querySelectorAll('article').forEach(card=>{{card.hidden=!card.querySelector('h2').textContent.includes(document.querySelector('#scenario').value)||['renderer','status','kind'].some(key=>document.getElementById(key).value&&card.dataset[key]!==document.getElementById(key).value)}})))</script></html>''')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["begin", "finish"])
    parser.add_argument("--output", type=Path, required=True, help="Ignored build directory outside fixture sources")
    parser.add_argument("--renderers", nargs="+", choices=list(CATALOGUE["renderers"]), default=list(CATALOGUE["renderers"]))
    parser.add_argument("--native-results", type=Path)
    parser.add_argument("--gradle-root", type=Path, default=ROOT, help="Identity or coordinated build root used for this run")
    args = parser.parse_args()
    args.gradle_root = args.gradle_root.resolve()
    if args.gradle_root != ROOT and (args.gradle_root / "waltid-identity").resolve() != ROOT:
        parser.error("The build root must use this Identity checkout")
    args.output = args.output.resolve()
    if args.output == HERE or HERE in args.output.parents:
        parser.error("Reports must stay outside fixture sources")
    args.output.mkdir(parents=True, exist_ok=True)
    marker = args.output / "run.json"
    if args.phase == "begin":
        input_fingerprint, repositories = source_inputs(args.gradle_root)
        run = {"schemaVersion": 4, "runId": str(uuid.uuid4()), "started": time.time(), "sha": git("rev-parse", "HEAD"),
               "dirty": bool(git("status", "--porcelain")), "gradleRoot": str(args.gradle_root),
               "fingerprint": input_fingerprint, "renderers": args.renderers,
               "fixtureDigest": digest(HERE.parent / "resources/files/wallet-visual-data.json"),
               "ci": {key: os.environ[key] for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_REPOSITORY", "GITHUB_EVENT_NAME") if key in os.environ}}
        inputs = args.output / "source-inputs.json"
        inputs.write_text(json.dumps({"schemaVersion": 1, "runId": run["runId"], "sha": run["sha"],
                                     "fingerprint": input_fingerprint, "repositories": repositories}, indent=2) + "\n")
        run["sourceInputsDigest"] = digest(inputs)
        marker.write_text(json.dumps(run, indent=2) + "\n")
        (args.output / "native-runner-env.json").write_text(json.dumps({"testRunnerEnv": runner_env(run)}) + "\n")
        print(f"Started evidence run: {marker}")
        return 0
    return finish(args, json.loads(marker.read_text()))


if __name__ == "__main__":
    raise SystemExit(main())
