#!/usr/bin/env python3
"""Check that every asset file has a valid CREDITS entry (NFR-045).

Finds every file under */src/*/resources/assets/ and checks it against the
entries in CREDITS. Fails when an asset has no entry, when an entry names a
file that doesn't exist, when an entry is malformed, or when a vg source is
under goon/ (NFR-041). The format is documented at the top of CREDITS.

Usage: python scripts/check_credits.py [repo root]
Needs only Python 3 and its standard library.
"""

import re
import sys
from pathlib import Path

ASSET_GLOB = "*/src/*/resources/assets/**/*"
ASSET_PATH = re.compile(r"^[^/]+/src/[^/]+/resources/assets/.+")
KEYS = {"path", "source", "state", "commit", "attribution", "author", "notes"}
VG_REQUIRED = ("state", "commit", "attribution")
COMMIT = re.compile(r"^[0-9a-f]{40}$")


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
    source = entry.get("source")
    if source is None:
        errors.append(f"{where}: missing 'source' (a vg .dmi path or 'original')")
    elif source == "original":
        for key in VG_REQUIRED:
            if key in entry:
                errors.append(f"{where}: an original asset has no '{key}'")
    else:
        if "goon" in source.replace("\\", "/").lower().split("/"):
            errors.append(f"{where}: source {source!r} is under goon/ (CC BY-NC-SA, NFR-041)")
        for key in VG_REQUIRED:
            if key not in entry:
                errors.append(f"{where}: a vg-derived asset needs '{key}'")
        commit = entry.get("commit")
        if commit is not None and not COMMIT.match(commit):
            errors.append(f"{where}: commit {commit!r} is not a full 40-character hash")
        attribution = entry.get("attribution")
        if attribution is not None and "vgstation13 contributors" not in attribution.lower():
            errors.append(f"{where}: attribution must name the vgstation13 contributors")
    return errors


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
    credits_file = root / "CREDITS"
    if not credits_file.is_file():
        print(f"error: {credits_file} not found")
        return 1

    assets = {p.relative_to(root).as_posix() for p in root.glob(ASSET_GLOB) if p.is_file()}
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
            errors.append(f"CREDITS:{line}: {path} is not under */src/*/resources/assets/")
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
