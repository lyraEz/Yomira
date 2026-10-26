#!/usr/bin/env python3
"""Writes the text of a GitHub release (also shown by the in-app updater) from CHANGELOG.md.

The section "## Yomira [<version>]" is turned into short notes:
  intro paragraph   -> lead line in bold
  ### Added         -> New
  ### Changed/Improved -> Improved
  ### Fixed         -> Fixed
  ### Removed       -> Removed (only when the section exists)
  ### Other         -> left out (technical)
followed by a short "how to update" footer.

Usage: release-notes.py <version> <out-file>
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHANGELOG = ROOT / "CHANGELOG.md"
CERT_FILE = ROOT / "docs" / "signing-certificate.sha256"

SECTIONS = [
    ("New", ("added",)),
    ("Improved", ("changed", "improved")),
    ("Fixed", ("fixed",)),
    ("Removed", ("removed",)),
]


def changelog_section(version: str) -> str | None:
    text = CHANGELOG.read_text(encoding="utf-8")
    heading = re.compile(r"^## Yomira \[" + re.escape(version) + r"\].*$", re.MULTILINE)
    match = heading.search(text)
    if not match:
        return None
    rest = text[match.end():]
    end = re.search(r"^## ", rest, re.MULTILINE)
    return rest[: end.start()] if end else rest


def parse(section: str) -> tuple[str, dict[str, list[str]]]:
    intro: list[str] = []
    groups: dict[str, list[str]] = {}
    current: str | None = None
    for raw in section.splitlines():
        line = raw.rstrip()
        sub = re.match(r"^###\s+(\w+)", line)
        if sub:
            current = sub.group(1).lower()
            groups.setdefault(current, [])
            continue
        if current is None:
            if line.strip():
                intro.append(line.strip())
            continue
        if line.startswith("- "):
            groups[current].append(line)
        elif line.lstrip().startswith("- ") and groups[current]:
            # Nested bullet under the previous item.
            groups[current][-1] += "\n  " + line.lstrip()
        elif line.strip() and groups[current]:
            # Continuation of the previous bullet.
            groups[current][-1] += " " + line.strip()
    return " ".join(intro), groups


def notes(version: str) -> str:
    section = changelog_section(version)
    out: list[str] = []
    if section is None:
        out.append("Fixes and improvements.")
    else:
        intro, groups = parse(section)
        if intro:
            out.append(f"**{intro}**")
        for title, keys in SECTIONS:
            items = [item for key in keys for item in groups.get(key, [])]
            if items:
                out.append(f"### {title}")
                out.append("\n".join(items))

    cert = CERT_FILE.read_text(encoding="utf-8").strip().lower() if CERT_FILE.exists() else ""
    footer = [
        "---",
        "**How to update:** in the app, *More > About > Check for updates*, or download one of the APKs "
        "below. It installs over the previous version and keeps your data.",
        "\n".join([
            f"- `Yomira-v{version}-arm64-v8a.apk`: most phones",
            f"- `Yomira-v{version}-armeabi-v7a.apk`: 32-bit phones",
            f"- `Yomira-v{version}-universal.apk`: any phone (larger file)",
        ]),
    ]
    if cert:
        footer.append(f"*Signing certificate SHA-256 `{cert}`. File checksums are in `SHA256SUMS.txt`.*")
    out.extend(footer)
    return "\n\n".join(out) + "\n"


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit("usage: release-notes.py <version> <out-file>")
    version, out_file = sys.argv[1], sys.argv[2]
    path = pathlib.Path(out_file)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(notes(version), encoding="utf-8")
    print(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
