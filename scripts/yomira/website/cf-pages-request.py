#!/usr/bin/env python3
"""Builds a Cloudflare Pages deploy request for the website.

    python3 scripts/yomira/website/cf-pages-request.py <output.json> [project]

Writes a request for the cloud-cf bridge (action "pages_deploy") that uploads every file in
website/ to the Pages project (default "yomira-lyraez", served at yomira.lyraez.lol). Commit
the output under requests/ in cloud-cf to deploy it. Text files are inlined as UTF-8,
everything else as base64. The bridge accepts at most 500 files and 20 MB per request.
"""
import base64
import json
import sys
from pathlib import Path

TEXT = {".html", ".css", ".js", ".svg", ".txt", ".json", ".xml", ".webmanifest"}
MAX_FILES = 500
MAX_BYTES = 20_000_000


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[1])
    project = sys.argv[2] if len(sys.argv) > 2 else "yomira-lyraez"
    site = Path(__file__).resolve().parents[3] / "website"

    files, total = [], 0
    for path in sorted(p for p in site.rglob("*") if p.is_file()):
        data = path.read_bytes()
        total += len(data)
        entry = {"path": path.relative_to(site).as_posix()}
        if path.suffix.lower() in TEXT:
            entry["data"] = data.decode("utf-8")
        else:
            entry["encoding"] = "base64"
            entry["data"] = base64.b64encode(data).decode("ascii")
        files.append(entry)

    if len(files) > MAX_FILES or total > MAX_BYTES:
        sys.exit(f"Too large for one request: {len(files)} files, {total} bytes")

    request = {
        "version": 1,
        "name": f"deploy-{project}-site",
        "action": "pages_deploy",
        "project_name": project,
        "branch": "main",
        "files": files,
    }
    out.write_text(json.dumps(request, indent=1) + "\n", encoding="utf-8")
    print(f"{out}: {len(files)} files, {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
