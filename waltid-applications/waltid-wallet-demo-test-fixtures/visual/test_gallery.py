"""Gallery behavior at real Git boundaries, including stacks, removals and encoding-only changes."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

from PIL import Image, PngImagePlugin
import gallery


class GalleryTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.command("init", "-b", "main")
        self.command("config", "commit.gpgsign", "false")
        self.command("config", "user.name", "Visual contract test")
        self.command("config", "user.email", "visual-test@example.invalid")
        self.catalogue = self.root / gallery.CATALOGUE_PATH
        self.catalogue.parent.mkdir(parents=True)
        self.baselines = "waltid-applications/waltid-wallet-demo-compose/sharedUI/src/visualTest/snapshots/test"
        self.data = {"schemaVersion": 2, "renderers": {"android": {"baselineDirectory": self.baselines, "configuration": {"theme": "light"}}}, "states": []}
        self.save()
        (self.root / gallery.INDEX_PATH).write_text(gallery.catalogue_markdown(self.root))
        self.command("add", ".")
        self.command("commit", "-m", "empty catalogue")
        self.empty = self.head()

    def command(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.PIPE).decode().strip()

    def head(self):
        return self.command("rev-parse", "HEAD")

    def save(self):
        self.catalogue.write_text(json.dumps(self.data))

    def checkpoint(self):
        self.save()
        (self.root / gallery.INDEX_PATH).write_text(gallery.catalogue_markdown(self.root))
        self.command("add", ".")
        self.command("commit", "-m", "visual change")
        return self.head()

    def add(self, identifier, color="red"):
        self.data["states"].append({"id": identifier, "composeTest": "testScreen", "requirements": ["Readable account feedback"]})
        path = self.root / self.baselines / (identifier + ".png")
        path.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (2, 3), color).save(path)
        return path

    def build(self, base=None):
        return gallery.build(self.root, base or self.empty, "HEAD", "example/wallet")

    def test_added_state_has_pinned_head_and_no_before_image(self):
        self.add("wallet.empty")
        head = self.checkpoint()
        result = self.build()
        self.assertEqual(result["counts"], {"added": 1})
        self.assertIsNone(result["changes"][0]["beforeUrl"])
        self.assertIn(head, result["changes"][0]["afterUrl"])

    def test_removed_reference_keeps_the_pinned_before_link(self):
        path = self.add("wallet.empty")
        before = self.checkpoint()
        path.unlink()
        self.data["states"] = []
        self.checkpoint()
        result = self.build(before)
        self.assertEqual(result["counts"], {"removed": 1})
        self.assertIn(before, result["changes"][0]["beforeUrl"])
        self.assertIsNone(result["changes"][0]["afterUrl"])
        self.assertIn(f'src="https://raw.githubusercontent.com/example/wallet/{before}/{self.baselines}/wallet.empty.png"',
                      gallery.markdown(result))

    def test_pixel_change_is_distinct_from_encoding_change(self):
        path = self.add("wallet.empty")
        before = self.checkpoint()
        metadata = PngImagePlugin.PngInfo()
        metadata.add_text("comment", "Changed encoding")
        Image.new("RGB", (2, 3), "red").save(path, pnginfo=metadata, compress_level=0)
        self.checkpoint()
        self.assertEqual(self.build(before)["counts"], {})
        self.assertEqual(len(self.build(before)["encodingOnly"]), 1)
        Image.new("RGB", (2, 3), "blue").save(path)
        self.checkpoint()
        self.assertEqual(self.build(before)["counts"], {"changed": 1})

    def test_configuration_change_survives_identical_pixels(self):
        self.add("wallet.empty")
        before = self.checkpoint()
        self.data["renderers"]["android"]["configuration"]["theme"] = "dark"
        self.checkpoint()
        result = self.build(before)
        self.assertEqual(result["counts"], {"metadata": 1})
        body = gallery.markdown(result)
        self.assertIn(f"[after]({result['referenceIndexes']['after']['url']}#wallet-empty)", body)
        self.assertNotIn("#diff-", body)

    def test_rename_retains_both_reference_paths(self):
        path = self.add("wallet.empty")
        before = self.checkpoint()
        path.rename(path.with_name("wallet.new.png"))
        self.data["states"][0]["id"] = "wallet.new"
        self.checkpoint()
        result = self.build(before)
        self.assertEqual(result["counts"], {"renamed": 1})
        self.assertTrue(result["changes"][0]["beforeUrl"].endswith("wallet.empty.png"))
        self.assertTrue(result["changes"][0]["afterUrl"].endswith("wallet.new.png"))

    def test_stacked_target_excludes_its_existing_reference(self):
        self.add("wallet.parent")
        parent = self.checkpoint()
        self.add("wallet.child")
        self.checkpoint()
        result = self.build(parent)
        self.assertEqual(result["base"], parent)
        self.assertEqual([c["id"] for c in result["changes"]], ["wallet.child"])

    def test_diverged_target_uses_merge_base(self):
        self.add("wallet.parent")
        parent = self.checkpoint()
        self.command("checkout", "-b", "child", self.empty)
        self.data["states"] = []
        self.add("wallet.child")
        self.checkpoint()
        result = self.build(parent)
        self.assertEqual(result["base"], self.empty)
        self.assertEqual(result["target"], parent)
        self.assertEqual([c["id"] for c in result["changes"]], ["wallet.child"])

    def test_fork_after_image_uses_the_fork_repository(self):
        self.add("wallet.empty")
        head = self.checkpoint()
        result = gallery.build(self.root, self.empty, "HEAD", "example/wallet", "contributor/wallet")
        self.assertIn("github.com/contributor/wallet/blob/", result["changes"][0]["afterUrl"])
        body = gallery.markdown(result)
        self.assertIn(f'src="https://raw.githubusercontent.com/contributor/wallet/{head}/{self.baselines}/wallet.empty.png"', body)
        self.assertNotIn("?raw=true", body)

    def test_browser_catalogue_is_included_without_mobile_duplication(self):
        catalogue = self.root / gallery.BROWSER_CATALOGUE_PATH
        catalogue.parent.mkdir(parents=True)
        catalogue.write_text(json.dumps({"requirements": ["Readable account feedback"], "environment": "Chromium fixture host",
            "states": [{"id": "account.empty.wide", "width": 1280, "height": 800}]}))
        snapshots = catalogue.parent / "snapshots"
        snapshots.mkdir()
        Image.new("RGB", (1280, 800), "red").save(snapshots / "account-empty-wide.png")
        self.checkpoint()
        result = self.build()
        self.assertEqual(result["counts"], {"added": 1})
        cell = result["changes"][0]
        self.assertEqual(cell["renderer"], "browser")
        self.assertEqual(cell["after"]["configuration"]["viewport"], [1280, 800])
        self.assertIn(gallery.BROWSER_CATALOGUE_PATH, result["changedCatalogues"])
        self.assertIn("Browser", gallery.markdown(result))

    def test_gallery_contains_every_changed_platform_cell(self):
        for i in range(9):
            self.add(f"wallet.state_{i}")
        self.checkpoint()
        result = self.build()
        body = gallery.markdown(result, limit=6)
        self.assertIn("9 platform references", body)
        self.assertEqual(body.count('<img src='), 6)
        for i in range(9):
            self.assertIn(f"| wallet.state_{i} |", body)
            self.assertIn(f"#wallet-state_{i}", body)
        self.assertNotIn("/main/", body)

    def test_inventory_links_both_committed_catalogues_including_removed_states(self):
        path = self.add("wallet.changed")
        removed = self.add("wallet.removed")
        before = self.checkpoint()
        Image.new("RGB", (2, 3), "blue").save(path)
        removed.unlink()
        self.data["states"] = self.data["states"][:1]
        self.checkpoint()
        result = self.build(before)
        body = gallery.markdown(result, limit=0)
        self.assertNotIn("<img", body)
        for cell in result["changes"]:
            for side in ("before", "after"):
                if cell[side]:
                    self.assertIn(result["referenceIndexes"][side]["url"] + "#" + gallery.anchor(cell[side]["id"]), body)
        self.assertNotIn("inventoryPages", result)

    def test_large_gallery_keeps_all_references_in_the_description(self):
        for i in range(90):
            self.add(f"wallet.state_{i}")
        self.checkpoint()
        result = self.build()
        body = gallery.markdown(result)
        self.assertLess(len(body.encode()), 60_000)
        self.assertNotIn("inventoryPages", result)
        for i in range(90):
            self.assertEqual(body.count(f"| wallet.state_{i} |"), 1)
            self.assertIn(f"#wallet-state_{i}", body)

    def test_budget_failure_never_spills_into_comments(self):
        self.add("wallet.empty")
        self.checkpoint()
        with mock.patch.object(gallery, "BODY_BUDGET", 100):
            with self.assertRaisesRegex(ValueError, "No comments"):
                gallery.markdown(self.build())

    def test_large_text_is_labelled_separately_from_primary_flows(self):
        self.add("a.large_text")
        self.data["states"][-1]["configuration"] = {"android": {"fontScale": 1.5, "viewport": [320, 568]}}
        self.add("wallet.default")
        self.checkpoint()
        body = gallery.markdown(self.build(), limit=1)
        normal, variants = body.split("<summary>Accessibility and layout variants</summary>")
        self.assertIn('alt="wallet · default', normal)
        self.assertNotIn('alt="a · large text', normal)
        self.assertIn("1.5× text", variants)
        self.assertIn("Compose Android | Compose iOS | Native iOS", body)

    def test_explicit_product_preview_overrides_filename_order(self):
        self.add("access.failure")
        self.add("wallet.ready")
        self.data["galleryPreview"] = ["wallet.ready"]
        self.checkpoint()
        body = gallery.markdown(self.build(), limit=1)
        self.assertIn('alt="wallet · ready', body)
        self.assertNotIn('alt="access · failure', body)

    def test_stale_committed_index_fails_closed(self):
        self.add("wallet.empty")
        self.checkpoint()
        (self.root / gallery.INDEX_PATH).write_text("outdated")
        self.command("add", ".")
        self.command("commit", "-m", "stale index")
        with self.assertRaisesRegex(ValueError, "catalogue is stale"):
            self.build()

    def test_renamed_reference_keeps_the_old_index_anchor(self):
        path = self.add("wallet.old")
        before = self.checkpoint()
        path.rename(path.with_name("wallet.new.png"))
        self.data["states"][0]["id"] = "wallet.new"
        self.checkpoint()
        body = gallery.markdown(self.build(before), limit=0)
        self.assertIn(f"/{before}/{gallery.INDEX_PATH}#wallet-old", body)
        self.assertIn(f"/{self.head()}/{gallery.INDEX_PATH}#wallet-new", body)

    def test_preview_prioritizes_different_screen_families(self):
        for identifier in ("wallet.empty", "wallet.populated", "credential.details"):
            self.add(identifier)
        self.checkpoint()
        body = gallery.markdown(self.build(), limit=2)
        self.assertIn("**credential · details**", body)
        self.assertIn("**wallet · empty**", body)
        self.assertNotIn("**wallet · populated**", body)

    def test_refresh_is_idempotent_and_preserves_authored_prose(self):
        self.add("wallet.empty")
        self.checkpoint()
        generated = gallery.markdown(self.build())
        original = "## Summary\n\nAuthored content.\n"
        refreshed = gallery.replace_generated(original, generated)
        self.assertEqual(refreshed, gallery.replace_generated(refreshed, generated))
        self.assertTrue(refreshed.startswith(original.rstrip()))
        self.assertEqual(gallery.replace_generated(refreshed, "").strip(), original.strip())

    def test_ambiguous_markers_fail_closed(self):
        for body in (gallery.START, gallery.END, gallery.START + gallery.END + gallery.START + gallery.END):
            with self.subTest(body=body), self.assertRaises(ValueError):
                gallery.replace_generated(body, "")

    def test_missing_baseline_does_not_produce_a_gallery(self):
        path = self.add("wallet.empty")
        path.unlink()
        self.checkpoint()
        with self.assertRaises(subprocess.CalledProcessError):
            self.build()

    def test_invalid_catalogue_path_is_rejected(self):
        self.data["renderers"]["android"]["baselineDirectory"] = "../../outside"
        self.data["states"].append({"id": "wallet.empty", "composeTest": "testScreen", "requirements": []})
        self.save()
        self.command("add", ".")
        self.command("commit", "-m", "invalid path")
        with self.assertRaisesRegex(ValueError, "inside a wallet demo"):
            self.build()

    def test_option_like_git_revision_is_not_executed(self):
        with self.assertRaises(subprocess.CalledProcessError):
            gallery.commit(self.root, "--help")


if __name__ == "__main__":
    unittest.main()
