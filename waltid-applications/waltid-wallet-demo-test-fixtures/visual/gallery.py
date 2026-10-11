#!/usr/bin/env python3
"""Build a SHA-pinned PR gallery from catalogue changes. This command never publishes."""
import argparse
from collections import Counter, defaultdict
import hashlib
import html
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
from urllib.parse import quote

import report

CATALOGUE_PATH = "waltid-applications/waltid-wallet-demo-test-fixtures/visual/catalogue.json"
BROWSER_CATALOGUE_PATH = "waltid-applications/waltid-wallet-demo-compose/webApp/visual/catalogue.json"
START = "<!-- wallet-visual-gallery:start -->"
END = "<!-- wallet-visual-gallery:end -->"
INVENTORY_LINKS = "<!-- wallet-visual-inventory-links -->"
BODY_BUDGET = 60_000
LABELS = {"android": "Compose Android", "compose-ios": "Compose iOS", "swiftui": "SwiftUI", "browser": "Browser"}


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)


def commit(root, ref):
    return git(root, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}").decode().strip()


def blob(root, sha, path):
    if int(git(root, "cat-file", "-s", f"{sha}:{path}")) > 10 * 1024 * 1024:
        raise ValueError(f"Visual input exceeds 10 MiB: {path}")
    return git(root, "show", f"{sha}:{path}")


def read_catalogue(root, sha, path):
    try:
        return json.loads(blob(root, sha, path))
    except subprocess.CalledProcessError:
        return None


def inventory(root, sha):
    mobile = read_catalogue(root, sha, CATALOGUE_PATH)
    browser = read_catalogue(root, sha, BROWSER_CATALOGUE_PATH)
    catalogue = mobile or {"states": [], "renderers": {}}
    cells, ids = {}, set()
    for state in catalogue["states"]:
        if not re.fullmatch(r"[a-z0-9_]+(?:\.[a-z0-9_]+)*", state["id"]) or state["id"] in ids:
            raise ValueError("Catalogue IDs must be unique semantic identifiers")
        ids.add(state["id"])
        for name, renderer in catalogue["renderers"].items():
            test = state.get("swiftTest" if name == "swiftui" else "browserTest" if name == "browser" else "composeTest")
            if not test:
                continue
            directory = PurePosixPath(renderer["baselineDirectory"])
            if directory.is_absolute() or ".." in directory.parts or not str(directory).startswith("waltid-applications/waltid-wallet-demo-"):
                raise ValueError("Baseline directory must be inside a wallet demo")
            filename = state.get("baseline", {}).get(name) or ((state["id"].replace(".", "-") + renderer["suffix"] if name == "swiftui" else state["id"]) + ".png")
            if PurePosixPath(filename).name != filename or not filename.endswith(".png"):
                raise ValueError("Invalid baseline filename")
            path = str(directory / filename)
            if path in cells:
                raise ValueError(f"Multiple catalogue cells own {path}")
            config = {**renderer.get("configuration", {}), **state.get("configuration", {}).get(name, {})}
            cells[path] = {"id": state["id"], "renderer": name, "path": path, "test": test,
                           "requirements": state["requirements"], "configuration": config,
                           "environment": renderer.get("environment"), "note": state.get("note")}
    if browser:
        ids = set()
        for state in browser["states"]:
            identifier = state["id"]
            if not re.fullmatch(r"[a-z0-9_]+(?:\.[a-z0-9_]+)*", identifier) or identifier in ids:
                raise ValueError("Browser catalogue IDs must be unique semantic identifiers")
            ids.add(identifier)
            path = str(PurePosixPath(BROWSER_CATALOGUE_PATH).parent / "snapshots" / (identifier.replace(".", "-") + ".png"))
            cells[path] = {"id": identifier, "renderer": "browser", "path": path,
                "test": f"account.spec.cjs::{identifier}", "requirements": browser["requirements"],
                "configuration": {"viewport": [state["width"], state["height"]]},
                "environment": browser["environment"], "note": "Production Wasm host with synthetic HTTP responses"}
    return cells, {path: value for path, value in ((CATALOGUE_PATH, mobile), (BROWSER_CATALOGUE_PATH, browser)) if value is not None}


def renames(root, base, head):
    parts = git(root, "diff", "--name-status", "-z", "--find-renames", base, head).decode().split("\0")
    result, index = {}, 0
    while index < len(parts) and parts[index]:
        status, index = parts[index], index + 1
        if status.startswith(("R", "C")):
            before, after = parts[index:index + 2]
            index += 2
            if status.startswith("R"):
                result[after] = before
        else:
            index += 1
    return result


def image_url(repository, sha, path, raw=False):
    return f"https://github.com/{repository}/blob/{sha}/{quote(path)}" + ("?raw=true" if raw else "")


def build(root, base_ref, head_ref, repository, head_repository=None):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", head_repository or repository):
        raise ValueError("Use owner/repository identifiers, without credentials or URLs")
    target, head = commit(root, base_ref), commit(root, head_ref)
    base = git(root, "merge-base", target, head).decode().strip()
    old, old_catalogue = inventory(root, base)
    new, new_catalogue = inventory(root, head)
    moved, consumed, changes, encoding = renames(root, base, head), set(), [], []
    for path in sorted(set(old) | set(new)):
        if path not in new:
            continue
        previous = moved.get(path, path)
        before, after = old.get(previous), new[path]
        if before:
            consumed.add(previous)
        left = report.image_info(blob(root, base, previous)) if before else None
        right = report.image_info(blob(root, head, path))
        status = "added" if not before else "renamed" if previous != path else "changed"
        metadata_changed = before != after
        pixels_changed = left is None or left["dimensions"] != right["dimensions"] or left["pixels"] != right["pixels"]
        if before and not pixels_changed and not metadata_changed:
            if left["digest"] != right["digest"]:
                encoding.append({"id": after["id"], "renderer": after["renderer"], "path": path})
            continue
        if not pixels_changed and status == "changed":
            status = "metadata"
        changes.append({"id": after["id"], "renderer": after["renderer"], "status": status,
            "pixelsChanged": pixels_changed, "before": before, "after": after,
            "beforeImage": left, "afterImage": right,
            "beforeUrl": image_url(repository, base, previous) if before else None,
            "afterUrl": image_url(head_repository or repository, head, path)})
    for path in sorted(set(old) - consumed):
        before = old[path]
        changes.append({"id": before["id"], "renderer": before["renderer"], "status": "removed",
            "pixelsChanged": True, "before": before, "after": None,
            "beforeImage": report.image_info(blob(root, base, path)), "afterImage": None,
            "beforeUrl": image_url(repository, base, path), "afterUrl": None})
    result = {"schemaVersion": 1, "kind": "committed-baseline", "base": base, "target": target, "head": head,
            "repository": repository, "headRepository": head_repository or repository,
            "counts": dict(Counter(c["status"] for c in changes)), "changes": changes, "encodingOnly": encoding,
            "catalogueChanged": old_catalogue != new_catalogue,
            "changedCatalogues": sorted(path for path in old_catalogue.keys() | new_catalogue.keys()
                if old_catalogue.get(path) != new_catalogue.get(path))}
    if len(markdown(result, check_budget=False).encode()) > BODY_BUDGET:
        result["inventoryPages"] = inventory_pages(result)
    return result


def inventory_rows(gallery):
    rows = defaultdict(list)
    for cell in gallery["changes"]:
        rows[cell["id"]].append(cell)
    head_ref = gallery["head"] if gallery["headRepository"] == gallery["repository"] else gallery["headRepository"].split("/")[0] + ":" + gallery["head"]
    comparison = f"/{gallery['repository']}/compare/{gallery['base']}...{head_ref}"
    for identifier, cells in rows.items():
        links = []
        for cell in sorted(cells, key=lambda c: c["renderer"]):
            path = (cell["after"] or cell["before"])["path"]
            anchor = hashlib.sha256(path.encode()).hexdigest()
            # Catalogue-only changes have no PNG diff anchor.
            url = cell["afterUrl"] if cell["status"] == "metadata" else f"{comparison}#diff-{anchor}"
            links.append(f"[{LABELS[cell['renderer']]} · {cell['status']}]({url})")
        yield f"| {identifier} | {' · '.join(links)} |"


def inventory_pages(gallery):
    pages, lines, size = [], [], 0
    for line in inventory_rows(gallery):
        if lines and size + len(line.encode()) > 48_000:
            pages.append(lines)
            lines, size = [], 0
        lines.append(line)
        size += len(line.encode()) + 1
    if lines:
        pages.append(lines)
    result = []
    for number, page in enumerate(pages, 1):
        result.append("\n".join([
            f"<!-- wallet-visual-inventory:{number}:start -->", "<details>",
            f"<summary>Changed visual references — part {number} of {len(pages)}</summary>", "",
            f"Committed references at `{gallery['base']}` → `{gallery['head']}`.", "",
            "| Scenario | Platform references |", "|---|---|", *page, "", "</details>",
            f"<!-- wallet-visual-inventory:{number}:end -->", ""]))
    return result


def markdown(gallery, limit=6, check_budget=True):
    rows = defaultdict(list)
    for cell in gallery["changes"]:
        rows[cell["id"]].append(cell)
    if not rows and not gallery["catalogueChanged"] and not gallery["encodingOnly"]:
        return ""
    repository, base, head = gallery["repository"], gallery["base"], gallery["head"]
    selected, families = [], set()
    for identifier, cells in rows.items():
        if len(selected) >= limit:
            break
        family = identifier.split(".")[0]
        if family not in families and any(c["pixelsChanged"] for c in cells):
            selected.append(identifier)
            families.add(family)
        if len(selected) == limit:
            break
    for identifier, cells in rows.items():
        if len(selected) >= limit:
            break
        if identifier not in selected and any(c["pixelsChanged"] for c in cells):
            selected.append(identifier)
    lines = [START, "<details>", f"<summary>Visual references — {len(gallery['changes'])} changed platform cells, {len(rows)} scenarios</summary>", "",
        f"Expected appearance at [{base[:12]}](https://github.com/{repository}/commit/{base}) → [{head[:12]}](https://github.com/{gallery['headRepository']}/commit/{head}). These committed references are separate from fresh comparison and E2E evidence.", "",
        f"Showing {len(selected)} representative scenarios. The complete changed-reference inventory follows.", ""]
    for identifier in selected:
        lines += [f"**{identifier}**", "", "| Renderer | Before | After |", "|---|---|---|"]
        for cell in sorted(rows[identifier], key=lambda c: c["renderer"]):
            pictures = []
            for side in ("before", "after"):
                url = cell[side + "Url"]
                pictures.append(f'<a href="{html.escape(url)}"><img src="{html.escape(url + "?raw=true")}" alt="{identifier}, {LABELS[cell["renderer"]]}, {side}" width="220"></a>' if url else ("Added" if side == "before" else "Removed"))
            lines.append(f"| {LABELS[cell['renderer']]} · {cell['status']} | {pictures[0]} | {pictures[1]} |")
        lines.append("")
    if gallery.get("inventoryPages"):
        lines += [f"Every changed reference: {INVENTORY_LINKS}", ""]
    else:
        lines += ["<details>", "<summary>Every changed reference</summary>", "", "| Scenario | Platform references |", "|---|---|",
                  *inventory_rows(gallery), "", "</details>", ""]
    if gallery["encodingOnly"]:
        lines.append(f"{len(gallery['encodingOnly'])} encoding-only updates have identical decoded pixels; excluded from the image preview.")
    if gallery["catalogueChanged"]:
        links = [f"[{PurePosixPath(path).parent.name} catalogue]({image_url(gallery['headRepository'], head, path)})"
                 for path in gallery["changedCatalogues"]]
        lines.append("Catalogue and configuration changed: " + ", ".join(links) + ".")
    lines += ["", "Selected app-journey evidence belongs in a separate, clearly labelled gallery.", "", "</details>", END]
    value = "\n".join(lines) + "\n"
    if check_budget and len(value.encode()) > BODY_BUDGET:
        if limit:
            return markdown(gallery, limit - 1)
        raise ValueError("Gallery exceeds the PR-body budget even without inline previews")
    return value


def replace_generated(body, generated):
    if body.count(START) != body.count(END) or body.count(START) > 1:
        raise ValueError("Ambiguous generated gallery boundaries")
    if START in body:
        before, remaining = body.split(START, 1)
        _, after = remaining.split(END, 1)
        return before.rstrip() + ("\n\n" + generated.strip() if generated else "") + after
    return body.rstrip() + ("\n\n" + generated.strip() if generated else "") + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Actual PR target branch or commit; its merge-base is resolved")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--repository", required=True, help="Base owner/repository")
    parser.add_argument("--head-repository", help="Fork owner/repository, if different")
    parser.add_argument("--root", type=Path, default=report.ROOT)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--inline-scenarios", type=int, choices=range(0, 9), default=6)
    parser.add_argument("--body", type=Path, help="Refresh a local body file, preserving authored prose")
    args = parser.parse_args()
    gallery = build(args.root, args.base, args.head, args.repository, args.head_repository)
    generated = markdown(gallery, args.inline_scenarios)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "gallery.json").write_text(json.dumps(gallery, indent=2) + "\n")
    (args.output / "gallery.md").write_text(generated)
    for number, page in enumerate(gallery.get("inventoryPages", []), 1):
        (args.output / f"inventory-{number}.md").write_text(page)
    if args.body:
        args.body.write_text(replace_generated(args.body.read_text(), generated))
    print(json.dumps({"counts": gallery["counts"], "encodingOnly": len(gallery["encodingOnly"]), "gallery": str(args.output / "gallery.md")}, indent=2))


if __name__ == "__main__":
    raise SystemExit(main())
