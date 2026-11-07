#!/usr/bin/env python3
"""Check that every asset file has a valid CREDITS entry (NFR-045).

Finds every file under */src/*/resources/assets/, and under the assets/ of
each built-in resource pack in */src/*/resources/resourcepacks/, and checks it
against the entries in CREDITS. Fails when an asset has no entry, when an entry names a
file that doesn't exist, when an entry is malformed, when a vg source is
under goon/ (NFR-041), when a vg icon has no state or a vg sound has one, or
when a third-party license isn't one that can be adapted under CC BY-SA 4.0
(D-62) or, for a font, one it may keep (D-76). The format is documented at the top of CREDITS.

Usage: python scripts/check_credits.py [repo root]
Needs only Python 3 and its standard library.
"""

import re
import sys
from pathlib import Path

ASSET_GLOBS = ("*/src/*/resources/assets/**/*", "*/src/*/resources/resourcepacks/*/assets/**/*")
ASSET_PATH = re.compile(r"^[^/]+/src/[^/]+/resources/(resourcepacks/[^/]+/)?assets/.+")
KEYS = {"path", "origin", "source", "state", "commit", "license", "attribution", "author", "adapted", "notes"}
ORIGINS = ("original", "vgstation13", "other")
# Fields that only make sense for one origin, and those each origin requires.
ONLY = {"state": "vgstation13", "commit": "vgstation13", "license": "other"}
REQUIRED = {
    "original": (),
    "vgstation13": ("source", "commit"),
    "other": ("source", "license"),
}
# SPDX ids of licenses whose works may be adapted and shared under CC BY-SA
# 4.0 (D-62). NonCommercial and NoDerivatives licenses are never allowed
# (NFR-046). Add to this list only after checking compatibility.
LICENSES = {"CC0-1.0", "CC-BY-3.0", "CC-BY-4.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0"}
# Licenses a font keeps instead of being adapted under CC BY-SA 4.0 (D-76). Only
# files under font/ may use them, and each needs its license text in the jar.
FONT_LICENSES = {"Bitstream-Vera"}
NO_ATTRIBUTION = {"CC0-1.0"}
COMMIT = re.compile(r"^[0-9a-f]{7,40}$")


def parse(text):
    """Return (entries, errors). Each entry is (line number, {key: value})."""
    entries, errors = [], []
    current, start = None, 0

    def close():
        if current is not None:
            entries.append((start, current))

    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if line.startswith("#"):
            continue
        if not line:
            close()
            current = None
            continue
        key, sep, value = line.partition(":")
        key, value = key.strip(), value.strip()
        if not sep or not value:
            errors.append(f"CREDITS:{number}: expected 'key: value', got {line!r}")
            continue
        if current is None:
            current, start = {}, number
            if key != "path":
                errors.append(f"CREDITS:{number}: an entry must start with 'path'")
        if key not in KEYS:
            errors.append(f"CREDITS:{number}: unknown key {key!r}")
        elif key in current:
            errors.append(f"CREDITS:{number}: duplicate key {key!r}")
        else:
            current[key] = value
    close()
    return entries, errors


def check_entry(line, entry):
    where = f"CREDITS:{line}"
    errors = []
    origin = entry.get("origin")
    if origin not in ORIGINS:
        found = "missing" if origin is None else repr(origin)
        return [f"{where}: 'origin' is {found}; expected one of {', '.join(ORIGINS)}"]
    for key, owner in ONLY.items():
        if key in entry and origin != owner:
            errors.append(f"{where}: '{key}' only applies to origin {owner}")
    for key in REQUIRED[origin]:
        if key not in entry:
            errors.append(f"{where}: origin {origin} needs '{key}'")

    if origin == "vgstation13":
        # vg keeps its icons in .dmi files, with a state in each, and its sounds under sound/.
        source = entry.get("source", "")
        paths = [p.strip().replace("\\", "/").lower() for p in source.split(",")]
        if any("goon" in p.split("/") for p in paths):
            errors.append(f"{where}: source {source!r} is under goon/ (CC BY-NC-SA, NFR-041)")
        if source:
            sound = all(p.startswith("sound/") for p in paths)
            if sound and "state" in entry:
                errors.append(f"{where}: 'state' is for icons; source {source!r} is a sound")
            elif not sound and "state" not in entry:
                errors.append(f"{where}: origin vgstation13 needs 'state' for source {source!r}")
            if sound != ("/sounds/" in entry.get("path", "")):
                errors.append(f"{where}: source {source!r} doesn't match the asset: sounds come from vg's sound/")
        commit = entry.get("commit")
        if commit is not None and not COMMIT.match(commit):
            errors.append(f"{where}: commit {commit!r} is not a vgstation13 commit hash (7 to 40 hex digits)")
    elif origin == "other":
        license = entry.get("license")
        font_license = license in FONT_LICENSES and "/font/" in entry.get("path", "")
        if license is not None and license not in LICENSES and not font_license:
            errors.append(
                f"{where}: license {license!r} is not one of {', '.join(sorted(LICENSES))} "
                "(NonCommercial and NoDerivatives licenses are never allowed)"
            )
        if (license in LICENSES or font_license) and license not in NO_ATTRIBUTION and "attribution" not in entry:
            errors.append(f"{where}: license {license} needs 'attribution' (the author to credit)")
    return errors


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
    credits_file = root / "CREDITS"
    if not credits_file.is_file():
        print(f"error: {credits_file} not found")
        return 1

    assets = {p.relative_to(root).as_posix() for glob in ASSET_GLOBS for p in root.glob(glob) if p.is_file()}
    entries, errors = parse(credits_file.read_text(encoding="utf-8"))

    credited = {}
    for line, entry in entries:
        errors += check_entry(line, entry)
        path = entry.get("path")
        if path is None:
            continue
        if path in credited:
            errors.append(f"CREDITS:{line}: {path} already has an entry at line {credited[path]}")
            continue
        credited[path] = line
        if not ASSET_PATH.match(path):
            errors.append(f"CREDITS:{line}: {path} is not under */src/*/resources/assets/ or a resource pack's assets/")
        elif path not in assets:
            errors.append(f"CREDITS:{line}: {path} does not exist")

    missing = sorted(assets - credited.keys())

    if missing:
        print(f"{len(missing)} asset file(s) have no CREDITS entry:")
        for path in missing:
            print(f"  {path}")
    if errors:
        print(f"{len(errors)} problem(s) in CREDITS:")
        for error in sorted(errors, key=lambda e: int(e.split(":")[1])):
            print(f"  {error}")
    if missing or errors:
        print("See the format at the top of CREDITS and CONTRIBUTING.md, 'Assets'.")
        return 1

    print(f"CREDITS OK: {len(assets)} asset file(s), all credited.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
