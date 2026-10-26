#!/usr/bin/env python3
"""Builds a Cloudflare Pages deploy request for the website.

    python3 scripts/yomira/website/cf-pages-request.py <output.json> [project]

Writes a request for the cloud-cf bridge (action "pages_deploy") that uploads every file in
website/ to the Pages project (default "yomira-lyraez", served at yomira.lyraez.lol). Commit
the output under requests/ in cloud-cf to deploy it. Text files are inlined as UTF-8,
everything else as base64. The bridge accepts at most 500 files and 20 MB per request.

References to assets in index.html and in the stylesheets get a "?v=<content hash>" suffix. The
lyraez.lol zone tells browsers to keep assets for hours, so a changed file needs a new URL to
reach people who visited before; unchanged files keep theirs and stay cached.
"""
import base64
import hashlib
import json
import re
import sys
from pathlib import Path

TEXT = {".html", ".css", ".js", ".svg", ".txt", ".json", ".xml", ".webmanifest"}
MAX_FILES = 500
MAX_BYTES = 20_000_000
SITE_URL = "https://yomira.lyraez.lol/"
CSS_URL = re.compile(r'url\("?([^")?#]+)"?\)')
HTML_REF = re.compile(r'((?:src|href|poster|content)=")((?:' + re.escape(SITE_URL) + r')?assets/[^"?#]+)(")')


def short_hash(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()[:10]


def versioned_contents(site: Path) -> dict[str, bytes]:
    """Every file under site/, with asset references rewritten to carry a content hash."""
    contents = {p.relative_to(site).as_posix(): p.read_bytes() for p in site.rglob("*") if p.is_file()}
    hashes = {path: short_hash(data) for path, data in contents.items()}

    # Stylesheets first: their url() targets (fonts) are plain files, then their own hash changes.
    for path in [p for p in contents if p.endswith(".css")]:
        base = path.rsplit("/", 1)[0] + "/" if "/" in path else ""

        def css_ref(m: re.Match) -> str:
            target = base + m.group(1)
            return f'url("{m.group(1)}?v={hashes[target]}")' if target in hashes else m.group(0)

        contents[path] = CSS_URL.sub(css_ref, contents[path].decode("utf-8")).encode("utf-8")
        hashes[path] = short_hash(contents[path])

    def html_ref(m: re.Match) -> str:
        target = m.group(2).removeprefix(SITE_URL)
        if target not in hashes:
            sys.exit(f"index.html points to a missing file: {target}")
        return f"{m.group(1)}{m.group(2)}?v={hashes[target]}{m.group(3)}"

    contents["index.html"] = HTML_REF.sub(html_ref, contents["index.html"].decode("utf-8")).encode("utf-8")
    return contents


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[1])
    project = sys.argv[2] if len(sys.argv) > 2 else "yomira-lyraez"
    site = Path(__file__).resolve().parents[3] / "website"

    files, total = [], 0
    for name, data in sorted(versioned_contents(site).items()):
        total += len(data)
        entry = {"path": name}
        if Path(name).suffix.lower() in TEXT:
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
