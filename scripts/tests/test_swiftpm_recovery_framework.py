from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from swiftpm_framework import imported_framework


class ImportedFrameworkTest(unittest.TestCase):
    def test_uses_compiled_search_paths_with_spaces_and_ignores_other_caches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            selected = root / "current cache" / "SQLCipher.framework"
            selected.mkdir(parents=True)
            (root / "stale cache" / "SQLCipher.framework").mkdir(parents=True)
            definition = root / "arm64.def"
            definition.write_text(f'compilerOpts = -fmodules "-F{selected.parent}" "-F{root / "missing"}"\n')
            self.assertEqual(selected.resolve(), imported_framework(definition, "SQLCipher"))

    def test_missing_framework_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            definition = Path(directory) / "arm64.def"
            definition.write_text(f'compilerOpts = "-F{directory}"\n')
            with self.assertRaisesRegex(RuntimeError, "No imported SQLCipher framework"):
                imported_framework(definition, "SQLCipher")

    def test_preserves_compiler_search_order(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("first", "second"):
                (root / name / "SQLCipher.framework").mkdir(parents=True)
            definition = root / "arm64.def"
            definition.write_text(f'compilerOpts = "-F{root / "first"}" "-F{root / "second"}"\n')
            self.assertEqual((root / "first" / "SQLCipher.framework").resolve(),
                             imported_framework(definition, "SQLCipher"))


if __name__ == "__main__":
    unittest.main()
