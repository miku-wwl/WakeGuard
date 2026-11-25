import argparse
import json
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("logs", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    rows = {}
    for file in args.logs.rglob("*.txt"):
        for line in file.read_text(encoding="utf8", errors="replace").splitlines():
            if "WakeGuardFixture" not in line or '{"id":' not in line:
                continue
            row = json.loads(line[line.index('{"id":'):].strip())
            rows[(row["id"], row["attempt"])] = row
    results = sorted(rows.values(),key=lambda row:(row["id"], row["attempt"]))
    finals = [row for row in results if row["attempt"]==3]
    report = {
        "rows": results,
        "complete_count": sum(row["is_complete"] for row in finals),
        "complete_accepted": sum(row["is_complete"] and row["passed"] for row in finals),
        "incomplete_count": sum(not row["is_complete"] for row in finals),
        "incomplete_accepted": sum(not row["is_complete"] and row["passed"] for row in finals),
    }
    args.output.write_text(json.dumps(report,indent=2),encoding="utf8")

if __name__ == "__main__":
    main()
