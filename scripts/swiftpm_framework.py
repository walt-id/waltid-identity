"""Locate a framework from the SwiftPM definition used by Kotlin's cinterop."""
from pathlib import Path
import shlex


def imported_framework(definition: Path, name: str) -> Path:
    options = next((value for line in definition.read_text().splitlines()
                    for key, separator, value in [line.partition("=")]
                    if separator and key.strip() == "compilerOpts"), "")
    # Match Clang's search order: SwiftPM can also emit a second, static framework
    # beside the dynamically linked framework in PackageFrameworks.
    for option in shlex.split(options):
        if option.startswith("-F") and len(option) > 2:
            framework = Path(option[2:]) / f"{name}.framework"
            if framework.is_dir():
                return framework.resolve()
    raise RuntimeError(f"No imported {name} framework found in {definition}")
