#!/usr/bin/env python3
"""Replaces the inherited app name in user-visible string resources.

Upstream (AniZen) strings mention the app by name. Yomira keeps the translations
but shows its own name. Re-run after every upstream sync:

    python3 scripts/yomira/rebrand_strings.py
"""

import glob
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PATTERNS = [
    "i18n*/src/commonMain/moko-resources/*/strings.xml",
    "i18n*/src/commonMain/moko-resources/*/plurals.xml",
    "app/src/*/res/values*/strings.xml",
]
NAME = re.compile(r"\bAni[Zz]en\b")


def main():
    changed = 0
    for pattern in PATTERNS:
        for path in glob.glob(os.path.join(ROOT, pattern)):
            with open(path, encoding="utf-8") as fh:
                text = fh.read()
            out = []
            for line in text.splitlines(keepends=True):
                # Comments keep the upstream name: they document provenance.
                if line.lstrip().startswith("<!--"):
                    out.append(line)
                else:
                    out.append(NAME.sub("Yomira", line))
            new = "".join(out)
            if new != text:
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(new)
                changed += 1
    print(f"updated {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
