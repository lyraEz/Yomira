#!/usr/bin/env python3
"""Compiles every regex literal of the Kotlin sources with ICU, the engine Android uses.

The JVM that runs the unit tests accepts patterns Android rejects (for example "[^]]"), and a bad
pattern in a top-level or object property crashes the app as soon as the class loads. This finds
Regex(...) calls with plain or raw (triple-quoted) string literals, also joined with +, and
"...".toRegex(), and compiles each one with the system's libicu through ctypes.

Usage: check-regex-icu.py [source dirs...]   (default: app presentation-core core domain data)
Exit status 1 when a pattern does not compile.
"""
import ctypes
import glob
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]


def load_icu():
    libs = sorted(glob.glob("/usr/lib/*/libicui18n.so.*") + glob.glob("/usr/lib/libicui18n.so.*"))
    libs = [lib for lib in libs if re.search(r"\.so\.\d+$", lib)]
    if not libs:
        sys.exit("libicu not found (install libicu)")
    path = libs[-1]
    version = path.rsplit(".", 1)[-1]
    i18n = ctypes.CDLL(path)
    uc = ctypes.CDLL(path.replace("libicui18n", "libicuuc"))
    return i18n, uc, version


class ParseError(ctypes.Structure):
    _fields_ = [
        ("line", ctypes.c_int32),
        ("offset", ctypes.c_int32),
        ("pre", ctypes.c_uint16 * 16),
        ("post", ctypes.c_uint16 * 16),
    ]


ESCAPES = {"n": "\n", "t": "\t", "r": "\r", "\\": "\\", '"': '"', "$": "$", "'": "'", "b": "\b"}


def unescape(text: str) -> str:
    def repl(match):
        code = match.group(1)
        if code[0] == "u":
            return chr(int(code[1:], 16))
        return ESCAPES.get(code, "\\" + code)

    return re.sub(r"\\(u[0-9a-fA-F]{4}|.)", repl, text)


def literals(src: str):
    """String literals of a Kotlin file as (start, end, value), skipping comments and chars."""
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            i = src.find("\n", i)
            i = n if i < 0 else i
        elif src.startswith("/*", i):
            i = src.find("*/", i + 2)
            i = n if i < 0 else i + 2
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            if j < 0:
                break
            while src.startswith('"', j + 3):
                j += 1  # extra quotes before the closing ones belong to the text
            out.append((i, j + 3, src[i + 3:j]))
            i = j + 3
        elif src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"' and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            out.append((i, j + 1, unescape(src[i + 1:j])))
            i = j + 1
        elif src[i] == "'":
            j = i + 1
            while j < n and src[j] != "'" and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            i = j + 1
        else:
            i += 1
    return out


TEMPLATE = re.compile(r"\$[{A-Za-z_]")


def patterns(path: pathlib.Path):
    src = path.read_text(encoding="utf-8", errors="ignore")
    lits = literals(src)
    by_start = {start: index for index, (start, _, _) in enumerate(lits)}
    for match in re.finditer(r"(?<![A-Za-z_.])Regex\(\s*", src):
        index = by_start.get(match.end())
        parts = []
        while index is not None:
            start, end, value = lits[index]
            parts.append(value)
            plus = re.match(r"\s*\+\s*", src[end:])
            index = by_start.get(end + plus.end()) if plus else None
        pattern = "".join(parts)
        # String templates are only known at run time.
        if parts and not TEMPLATE.search(pattern):
            yield src.count("\n", 0, match.start()) + 1, pattern
    for start, end, value in lits:
        if re.match(r"\s*\.toRegex\(", src[end:]) and not TEMPLATE.search(value):
            yield src.count("\n", 0, start) + 1, value


def main() -> int:
    i18n, uc, version = load_icu()
    regex_open = getattr(i18n, f"uregex_open_{version}")
    regex_open.restype = ctypes.c_void_p
    regex_close = getattr(i18n, f"uregex_close_{version}")
    regex_close.argtypes = [ctypes.c_void_p]
    error_name = getattr(uc, f"u_errorName_{version}")
    error_name.restype = ctypes.c_char_p

    dirs = sys.argv[1:] or ["app", "presentation-core", "core", "domain", "data"]
    checked, bad = 0, 0
    for directory in dirs:
        for path in sorted((ROOT / directory).rglob("*.kt")):
            if "/build/" in str(path):
                continue
            for line, pattern in patterns(path):
                units = pattern.encode("utf-16-le")
                buffer = ctypes.create_string_buffer(units, len(units) + 2)
                error = ParseError()
                status = ctypes.c_int(0)
                handle = regex_open(buffer, len(units) // 2, 0, ctypes.byref(error), ctypes.byref(status))
                checked += 1
                if status.value > 0:
                    bad += 1
                    where = path.relative_to(ROOT)
                    print(f"{where}:{line}: {error_name(status.value).decode()} at {error.offset}: {pattern}")
                if handle:
                    regex_close(handle)
    print(f"{checked} patterns checked with ICU {version}, {bad} rejected")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
