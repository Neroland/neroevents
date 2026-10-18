#!/usr/bin/env python3
"""Merge the per-package English lang fragments into the shipped lang file.

Every ``tools/lang/*.en_us.json`` fragment is a flat JSON object of translation key -> English text. The
merge writes ``common/src/main/resources/assets/neroevents/lang/en_us.json`` with every key from every
fragment, sorted, two-space indented, UTF-8, LF line endings and a trailing newline.

A key that appears more than once (in one fragment or across fragments) is fine when every occurrence
has the same text, and fails the merge when the texts differ. A non-string value fails the merge.

Usage (from anywhere):
    python tools/merge_lang.py           # write en_us.json
    python tools/merge_lang.py --check   # exit 1 if en_us.json is not the merge (writes nothing)

``LangMergeTest`` pins the same rules: the committed en_us.json must equal this script's output.
"""

import argparse
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
FRAGMENTS = REPO / "tools" / "lang"
TARGET = REPO / "common" / "src" / "main" / "resources" / "assets" / "neroevents" / "lang" / "en_us.json"
SUFFIX = ".en_us.json"


class MergeError(Exception):
    pass


def _pairs(path):
    def hook(pairs):
        return pairs

    with open(path, "r", encoding="utf-8") as handle:
        try:
            pairs = json.load(handle, object_pairs_hook=hook)
        except json.JSONDecodeError as error:
            raise MergeError(f"{path.name}: not valid JSON ({error})") from error
    if not isinstance(pairs, list):
        raise MergeError(f"{path.name}: must be a JSON object of key -> text")
    return pairs


def merge(fragment_dir=FRAGMENTS):
    """Returns the merged {key: text} dict, sorted by key. Raises MergeError on any conflict."""
    files = sorted(p for p in fragment_dir.glob("*" + SUFFIX) if p.is_file())
    if not files:
        raise MergeError(f"no *{SUFFIX} fragments in {fragment_dir}")
    merged = {}
    origin = {}
    for path in files:
        for key, value in _pairs(path):
            if not isinstance(value, str):
                raise MergeError(f"{path.name}: {key} must be a string")
            if key in merged and merged[key] != value:
                raise MergeError(
                    f"duplicate key {key} with different text in {origin[key]} and {path.name}")
            merged.setdefault(key, value)
            origin.setdefault(key, path.name)
    return dict(sorted(merged.items()))


def render(merged):
    return json.dumps(merged, indent=2, ensure_ascii=False) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description="Merge tools/lang/*.en_us.json into en_us.json.")
    parser.add_argument("--check", action="store_true", help="fail if en_us.json differs; write nothing")
    args = parser.parse_args(argv)
    try:
        merged = merge()
        text = render(merged)
    except MergeError as error:
        print(f"merge_lang: {error}", file=sys.stderr)
        return 2
    current = TARGET.read_text(encoding="utf-8") if TARGET.exists() else None
    if args.check:
        if current != text:
            print("merge_lang: en_us.json is not the merge of tools/lang; run python tools/merge_lang.py",
                  file=sys.stderr)
            return 1
        print("merge_lang: en_us.json is up to date")
        return 0
    if current == text:
        print("merge_lang: en_us.json already up to date")
        return 0
    with open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print(f"merge_lang: wrote {TARGET.relative_to(REPO).as_posix()} ({len(merged)} keys)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
