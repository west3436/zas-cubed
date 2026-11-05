#!/usr/bin/env python3
"""Print the engine's line coverage from its JaCoCo report (NFR-030).

Reports the whole engine and each package, lowest first, as Markdown, so CI can
add it to the job summary. Warns on stderr if the engine is under NFR-030's
80 %, which GitHub shows on the run. It doesn't fail: the requirement is a
Should, checked by reading the report.

Usage: python scripts/coverage_summary.py <jacocoTestReport.xml>
Needs only Python 3 and its standard library.
"""

import sys
import xml.etree.ElementTree as ET

TARGET = 80.0


def lines(element):
    """Return (covered, total) for a report element's own LINE counter."""
    for counter in element.findall("counter"):
        if counter.get("type") == "LINE":
            covered = int(counter.get("covered"))
            return covered, covered + int(counter.get("missed"))
    return 0, 0


def percent(covered, total):
    return 100.0 * covered / total if total else 100.0


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    root = ET.parse(sys.argv[1]).getroot()
    covered, total = lines(root)
    overall = percent(covered, total)
    print(f"**Engine line coverage: {overall:.1f} %** ({covered:,} of {total:,} lines; NFR-030 asks for {TARGET:g} %)")
    print()
    print("| Package | Line coverage | Lines |")
    print("|---|---|---|")
    packages = []
    for package in root.findall("package"):
        c, t = lines(package)
        name = package.get("name").replace("/", ".").replace("io.github.west3436.zas.", "")
        packages.append((percent(c, t), name, c, t))
    for share, name, c, t in sorted(packages):
        print(f"| `{name}` | {share:.1f} % | {c:,} of {t:,} |")
    if overall < TARGET:
        print(f"::warning::Engine line coverage is {overall:.1f} %, under NFR-030's {TARGET:g} %", file=sys.stderr)


if __name__ == "__main__":
    main()
