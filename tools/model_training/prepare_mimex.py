#!/usr/bin/env python
"""Extract the pinned, MIT-tagged MIMEX parquet files into local image folders."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
from collections import Counter
from pathlib import Path

import pyarrow.parquet as pq
from PIL import Image, ImageOps


DATASET_REVISION = "fa7017f07b10efdb4dc461c14b8da1ae8aa01e73"
LABELS = [
    "rocher_chocolate", "milka_chocolate", "kinder_chocolate", "toblerone_white",
    "toblerone_black", "lays_classic", "lays_chill", "pringles_original",
    "pringles_paprika", "nestle_water", "sanpellegrino_water", "redbull_energydrink",
    "monster_energydrink", "heinz_ketchup", "heinz_mayo", "barilla_pesto",
    "barilla_pomodoro", "barilla_lasagne", "loreal_shampoo", "dove_soap",
    "sensodyne_toothpaste", "colgate_toothpaste", "sensodyne_mouthwash", "nivea_rollon",
    "rexona_spray", "dove_spray", "nivea_baby", "johnson_baby",
]


def extract_split(parquet_path: Path, output: Path, split: str) -> dict:
    parquet = pq.ParquetFile(parquet_path)
    counts: Counter[str] = Counter()
    written = 0
    for batch in parquet.iter_batches(columns=["image", "label", "image_hash"], batch_size=128):
        for row in batch.to_pylist():
            label = int(row["label"])
            if label < 0 or label >= len(LABELS):
                raise ValueError(f"Unexpected class index {label}")
            image_info = row["image"]
            raw = image_info.get("bytes") if image_info else None
            if not raw:
                raise ValueError(f"Missing embedded image at row {written} in {parquet_path}")
            class_name = f"{label:02d}_{LABELS[label]}"
            folder = output / split / class_name
            folder.mkdir(parents=True, exist_ok=True)
            digest = row.get("image_hash") or hashlib.sha256(raw).hexdigest()[:12]
            filename = f"{written:06d}_{digest}.jpg"
            with Image.open(io.BytesIO(raw)) as source:
                image = ImageOps.exif_transpose(source).convert("RGB")
                image.save(folder / filename, format="JPEG", quality=92, optimize=True)
            counts[class_name] += 1
            written += 1
        print(f"{split}: extracted {written} images", flush=True)
    return {"count": written, "class_counts": dict(sorted(counts.items()))}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--train", required=True, type=Path)
    parser.add_argument("--test", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        raise SystemExit(f"Refusing to overwrite non-empty output directory: {args.output}")
    args.output.mkdir(parents=True, exist_ok=True)
    train = extract_split(args.train, args.output, "train")
    test = extract_split(args.test, args.output, "test")
    manifest = {
        "dataset": "Anilot/MIMEX",
        "revision": DATASET_REVISION,
        "license_tag": "MIT (dataset card metadata)",
        "labels": LABELS,
        "train": train,
        "test": test,
    }
    (args.output / "dataset_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (args.output / ".complete").write_text("ok\n", encoding="utf-8")
    print(json.dumps({"train": train["count"], "test": test["count"], "output": str(args.output)}, indent=2))


if __name__ == "__main__":
    main()
