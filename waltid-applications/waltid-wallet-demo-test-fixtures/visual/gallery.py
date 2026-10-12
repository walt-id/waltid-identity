#!/usr/bin/env python3
"""Build a SHA-pinned PR gallery from catalogue changes. This command never publishes."""
import argparse
from collections import Counter, defaultdict
import hashlib
import html
import json
import posixpath
from pathlib import Path, PurePosixPath
import re
import subprocess
from urllib.parse import quote

import report

CATALOGUE_PATH = "waltid-applications/waltid-wallet-demo-test-fixtures/visual/catalogue.json"
BROWSER_CATALOGUE_PATH = "waltid-applications/waltid-wallet-demo-compose/webApp/visual/catalogue.json"
START = "<!-- wallet-visual-gallery:start -->"
END = "<!-- wallet-visual-gallery:end -->"
INDEX_PATH = "waltid-applications/waltid-wallet-demo-test-fixtures/visual/catalogue.md"
BODY_BUDGET = 60_000
LABELS = {"android": "Compose Android", "compose-ios": "Compose iOS", "swiftui": "Native iOS", "browser": "Browser"}
MOBILE = ("android", "compose-ios", "swiftui")


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
        return json.loads(blob(root, sha, path) if sha else (root / path).read_bytes())
    except (subprocess.CalledProcessError, FileNotFoundError):
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
    for key in ("galleryPreview", "galleryVariants"):
        selected = catalogue.get(key, [])
        if not isinstance(selected, list) or any(identifier not in ids for identifier in selected) or len(set(selected)) != len(selected):
            raise ValueError(f"{key} must contain distinct catalogue IDs")
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
    if raw:
        return f"https://raw.githubusercontent.com/{repository}/{sha}/{quote(path)}"
    return f"https://github.com/{repository}/blob/{sha}/{quote(path)}"


def titles(catalogues):
    return {state["id"]: state.get("title", state["id"].replace(".", " · ").replace("_", " "))
            for catalogue in catalogues.values() for state in catalogue["states"]}


def anchor(identifier):
    # Hyphens cannot occur in catalogue IDs, so this mapping stays unique.
    return identifier.replace(".", "-")


def configuration_label(cell):
    config = cell["configuration"]
    parts = []
    if config.get("fontScale", 1) != 1:
        parts.append(f"{config['fontScale']}× text")
    if config.get("textSize", "large") != "large":
        parts.append(config["textSize"])
    if config.get("theme", "light") != "light":
        parts.append(config["theme"])
    if config.get("layoutDirection", "ltr") != "ltr":
        parts.append(config["layoutDirection"].upper())
    if parts and config.get("viewport"):
        parts.insert(0, "×".join(map(str, config["viewport"])))
    return ", ".join(parts)


def catalogue_markdown(root, sha=None):
    cells, catalogues = inventory(root, sha)
    labels, rows = titles(catalogues), defaultdict(dict)
    for cell in cells.values():
        rows[cell["id"]][cell["renderer"]] = cell
    lines = ["# Wallet visual catalogue", "", "<!-- Generated by gallery.py --write-index. Do not edit. -->", "",
        "Committed expected appearance from production UI with synthetic data. These images do not establish a fresh comparison or a completed app journey.", "",
        "Open an image for its full size. Compact, large-text, dark and RTL captures are labelled explicitly. A missing renderer means that this catalogue has no reference for it.", ""]
    for identifier, renderers in rows.items():
        lines += [f"## {anchor(identifier)}", "", f"**{html.escape(labels[identifier])}**", ""]
        names = ("browser",) if "browser" in renderers else MOBILE
        lines += ["| " + " | ".join(LABELS[name] for name in names) + " |", "| " + " | ".join("---" for _ in names) + " |"]
        pictures = []
        for name in names:
            cell = renderers.get(name)
            if not cell:
                pictures.append("No reference")
                continue
            path = quote(posixpath.relpath(cell["path"], str(PurePosixPath(INDEX_PATH).parent)))
            label = configuration_label(cell)
            pictures.append(f'<img src="{path}" alt="{html.escape(labels[identifier])}, {LABELS[name]}" width="240">' + (f"<br>{html.escape(label)}" if label else ""))
        lines += ["| " + " | ".join(pictures) + " |", ""]
    return "\n".join(lines)


def reference_index(root, sha, repository):
    try:
        data = blob(root, sha, INDEX_PATH)
    except subprocess.CalledProcessError:
        return None
    if data.decode() != catalogue_markdown(root, sha):
        raise ValueError("Committed visual catalogue is stale; run gallery.py --write-index and commit it")
    return {"path": INDEX_PATH, "digest": hashlib.sha256(data).hexdigest(), "url": image_url(repository, sha, INDEX_PATH)}


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
            "titles": {**titles(old_catalogue), **titles(new_catalogue)},
            "preview": [identifier for catalogue in new_catalogue.values() for identifier in catalogue.get("galleryPreview", [])],
            "variants": [identifier for catalogue in new_catalogue.values() for identifier in catalogue.get("galleryVariants", [])],
            "referenceIndexes": {"before": reference_index(root, base, repository),
                "after": reference_index(root, head, head_repository or repository)},
            "changedCatalogues": sorted(path for path in old_catalogue.keys() | new_catalogue.keys()
                if old_catalogue.get(path) != new_catalogue.get(path))}
    return result


def inventory_rows(gallery):
    rows = defaultdict(list)
    for cell in gallery["changes"]:
        rows[cell["id"]].append(cell)
    for identifier, cells in rows.items():
        links = []
        for side in ("before", "after"):
            available = [cell for cell in cells if cell.get(side)]
            if not available:
                continue
            index = gallery.get("referenceIndexes", {}).get(side)
            if index:
                for source_id in dict.fromkeys(cell[side]["id"] for cell in available):
                    links.append(f"[{side}]({index['url']}#{anchor(source_id)})")
            else:
                links.extend(f"[{LABELS[cell['renderer']]} {side}]({cell[side + 'Url']})" for cell in available)
        platforms = "; ".join(f"{LABELS[cell['renderer']]}: {cell['status']}" for cell in cells)
        yield f"| {identifier} | {platforms} | {' / '.join(links)} |"


def preview_table(gallery, rows, identifiers, renderers):
    lines = ["| " + " | ".join(LABELS[name] for name in renderers) + " |",
             "| " + " | ".join("---" for _ in renderers) + " |"]
    for identifier in identifiers:
        title = html.escape(gallery.get("titles", {}).get(identifier, identifier)).replace("|", "&#124;")
        cells = {cell["renderer"]: cell for cell in rows[identifier]}
        pictures = []
        for renderer in renderers:
            cell = cells.get(renderer)
            if cell is None:
                pictures.append(f"**{title}**<br>No changed reference")
                continue
            side = "after" if cell.get("after") else "before"
            label = configuration_label(cell[side])
            repository = gallery["headRepository"] if side == "after" else gallery["repository"]
            sha = gallery["head"] if side == "after" else gallery["base"]
            url = html.escape(image_url(repository, sha, cell[side]["path"], raw=True))
            picture = f'**{title}**<br><img src="{url}" alt="{title}, {LABELS[renderer]}, {side}" width="240">'
            if label:
                picture += "<br>" + html.escape(label)
            if side == "before":
                picture += "<br>Removed reference"
            elif cell.get("beforeUrl"):
                picture += f"<br>[Before]({cell['beforeUrl']})"
            pictures.append(picture)
        lines.append("| " + " | ".join(pictures) + " |")
    return lines + [""]


def markdown(gallery, limit=8, check_budget=True):
    rows = defaultdict(list)
    for cell in gallery["changes"]:
        rows[cell["id"]].append(cell)
    if not rows and not gallery["catalogueChanged"] and not gallery["encodingOnly"]:
        return ""
    changed = {identifier for identifier, cells in rows.items() if any(cell["pixelsChanged"] for cell in cells)}
    browser = [identifier for identifier in rows if identifier in changed and all(cell["renderer"] == "browser" for cell in rows[identifier])]
    browser = [identifier for identifier in gallery.get("preview", []) if identifier in browser] + [identifier for identifier in browser if identifier not in gallery.get("preview", [])]
    variants = [identifier for identifier in rows if identifier in changed and identifier not in browser
                and any(configuration_label(cell.get("after") or cell["before"]) for cell in rows[identifier])]
    normal = [identifier for identifier in rows if identifier in changed and identifier not in browser + variants]
    selected = [identifier for identifier in gallery.get("preview", []) if identifier in normal][:limit]
    families = {identifier.split(".")[0] for identifier in selected}
    for identifier in normal:
        if len(selected) >= limit:
            break
        if identifier not in selected and identifier.split(".")[0] not in families:
            selected.append(identifier)
            families.add(identifier.split(".")[0])
    selected += [identifier for identifier in normal if identifier not in selected][:max(0, limit - len(selected))]
    variants = ([identifier for identifier in gallery.get("variants", []) if identifier in variants]
                or variants)[:min(2, limit)]
    repository, base, head = gallery["repository"], gallery["base"], gallery["head"]
    lines = [START, "<details>", f"<summary>Visual changes — {len(rows)} scenarios across {len(gallery['changes'])} platform references</summary>", "",
        f"Committed expected appearance: [{base[:12]}](https://github.com/{repository}/commit/{base}) → [{head[:12]}](https://github.com/{gallery['headRepository']}/commit/{head}). App-journey captures are labelled separately.", ""]
    if selected:
        lines += ["**Main flows — default text size**", "", *preview_table(gallery, rows, selected, MOBILE)]
    if variants:
        lines += ["<details>", "<summary>Accessibility and layout variants</summary>", "",
                  "These deliberately use the text size, theme and direction shown below each image.", "",
                  *preview_table(gallery, rows, variants, MOBILE), "</details>", ""]
    if browser and limit:
        lines += ["<details>", "<summary>Browser account states</summary>", "",
                  "Production Wasm UI with synthetic HTTP responses.", "",
                  *preview_table(gallery, rows, browser[:2], ("browser",)), "</details>", ""]
    lines += ["<details>", "<summary>Every changed reference</summary>", "",
              "Each catalogue link opens that scenario with all its platform images. The status column identifies which platforms changed; before and after remain pinned to this comparison.", "",
              "| Scenario | Changed platforms | References |", "| --- | --- | --- |",
              *inventory_rows(gallery), "", "</details>", ""]
    if gallery["encodingOnly"]:
        lines.append(f"{len(gallery['encodingOnly'])} encoding-only updates have identical decoded pixels and are excluded.")
    lines += ["", "</details>", END]
    value = "\n".join(lines) + "\n"
    if check_budget and len(value.encode()) > BODY_BUDGET:
        if limit:
            return markdown(gallery, limit - 1)
        raise ValueError("Changed-reference inventory exceeds the description budget; keep the committed catalogue current and narrow the PR. No comments were created.")
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
    parser.add_argument("--base", help="Actual PR target branch or commit; its merge-base is resolved")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--repository", help="Base owner/repository")
    parser.add_argument("--head-repository", help="Fork owner/repository, if different")
    parser.add_argument("--root", type=Path, default=report.ROOT)
    parser.add_argument("--output", type=Path)
    index_mode = parser.add_mutually_exclusive_group()
    index_mode.add_argument("--write-index", action="store_true", help="Regenerate the versioned catalogue from working-tree metadata")
    index_mode.add_argument("--check-index", action="store_true", help="Check that the working-tree catalogue is current")
    parser.add_argument("--inline-scenarios", type=int, choices=range(0, 9), default=8)
    parser.add_argument("--body", type=Path, help="Refresh a local body file, preserving authored prose")
    args = parser.parse_args()
    if args.write_index or args.check_index:
        index = args.root / INDEX_PATH
        expected = catalogue_markdown(args.root)
        if args.write_index:
            index.write_text(expected)
        elif not index.is_file() or index.read_text() != expected:
            parser.error("Visual catalogue is stale; run gallery.py --write-index")
        return
    if not args.base or not args.repository or not args.output:
        parser.error("Gallery generation requires --base, --repository and --output")
    gallery = build(args.root, args.base, args.head, args.repository, args.head_repository)
    generated = markdown(gallery, args.inline_scenarios)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "gallery.json").write_text(json.dumps(gallery, indent=2) + "\n")
    (args.output / "gallery.md").write_text(generated)
    if args.body:
        args.body.write_text(replace_generated(args.body.read_text(), generated))
    print(json.dumps({"counts": gallery["counts"], "encodingOnly": len(gallery["encodingOnly"]), "gallery": str(args.output / "gallery.md")}, indent=2))


if __name__ == "__main__":
    raise SystemExit(main())
