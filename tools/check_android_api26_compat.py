#!/usr/bin/env python3
"""Functional Android minSdk 26 gate: reject unsupported Java 16 Stream.toList calls.

Android 14/API34 supplies Stream.toList natively; Android 10/API29 does not.
Our build intentionally targets both without installing desugar_jdk_libs 2.1.5.
"""
from pathlib import Path
import re
import sys

roots = ("android", "core", "game")
bad = []
for root in roots:
    for source in Path(root, "src", "main").rglob("*.java"):
        # This is deliberately broad: even a currently unreachable method may
        # become reachable through campaign management or an updater path.
        for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
            if (re.search(r"(?<!Collectors)\.toList\s*\(\s*\)", line) or
                re.search(r"\b(?:java\.util\.)?HexFormat\b", line)) and not line.lstrip().startswith(("//", "*")):
                bad.append(f"{source}:{line_number}: {line.strip()}")
if bad:
    print("FAIL Android minSdk26 forbidden Java runtime APIs (Stream.toList / HexFormat):", file=sys.stderr)
    print("\n".join(bad), file=sys.stderr)
    sys.exit(1)
print("PASS runtime sources avoid Android-incompatible Stream.toList and java.util.HexFormat")
