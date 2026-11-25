#!/usr/bin/env python3
"""Extract the Android instrumentation policy summary from filtered logcat."""

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("logcat", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--allow-missing", action="store_true")
    args = parser.parse_args()

    marker = "WakeGuardPolicy: SUMMARY "
    summary = None
    for line in args.logcat.read_text(encoding="utf-8", errors="replace").splitlines():
        if marker not in line:
            continue
        payload = line.split(marker, 1)[1].strip()
        try:
            summary = json.loads(payload)
        except json.JSONDecodeError:
            continue
    if summary is None:
        summary = {"error": "No instrumentation policy summary found", "fixtures": []}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        return 0 if args.allow_missing else 1

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({key: summary.get(key) for key in (
        "selected_policy", "positive_accepted", "positive_total",
        "independent_positive_accepted", "independent_positive_total",
        "obvious_unrelated_accepted", "obvious_unrelated_total",
    )}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
