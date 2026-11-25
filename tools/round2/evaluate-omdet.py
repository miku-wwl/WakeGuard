import argparse
import csv
import json
import os
import time
from pathlib import Path

os.environ["HF_HUB_OFFLINE"] = "1"
os.environ["TRANSFORMERS_OFFLINE"] = "1"

import torch
from PIL import Image
from transformers import AutoProcessor, OmDetTurboForObjectDetection

CLASSES = ["sink", "toothbrush", "toothpaste"]
QUERIES = ["bathroom sink", "toothbrush", "toothpaste tube"]
PROMPT = "Detect a bathroom sink, a toothbrush and a toothpaste tube."

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--split")
    parser.add_argument("images", nargs="*", type=Path)
    args = parser.parse_args()
    torch.set_num_threads(4)
    torch.set_num_interop_threads(1)
    processor = AutoProcessor.from_pretrained(args.weights, local_files_only=True)
    model = OmDetTurboForObjectDetection.from_pretrained(
        args.weights, local_files_only=True, use_safetensors=True,
    ).eval()
    cases = [(path.stem, path) for path in args.images]
    if args.manifest:
        with args.manifest.open(encoding="utf-8-sig", newline="") as stream:
            cases.extend(
                (row["image_id"], Path(row["local_path"]))
                for row in csv.DictReader(stream)
                if row["split"] == args.split and row["scored"] == "1"
            )
    results = []
    with torch.inference_mode():
        for ident, path in cases:
            with Image.open(path) as image:
                rgb = image.convert("RGB")
            started = time.perf_counter()
            inputs = processor(rgb, text=QUERIES, task=PROMPT, return_tensors="pt")
            # Disable prompt caching across calls only when changing the fixed class list.
            outputs = model(**inputs)
            results_by_image = processor.post_process_grounded_object_detection(
                outputs, text_labels=QUERIES, target_sizes=[rgb.size[::-1]],
                threshold=0.001, nms_threshold=0.3,
            )[0]
            scores = {name: 0.0 for name in CLASSES}
            boxes = {}
            for score, name, box in zip(results_by_image["scores"], results_by_image["classes"], results_by_image["boxes"]):
                label = CLASSES[QUERIES.index(name)]
                if float(score) > scores[label]:
                    scores[label] = float(score)
                    boxes[label] = [float(b)/rgb.size[i%2] for i,b in enumerate(box)]
            row = dict(id=ident, scores=scores, boxes=boxes, latency_ms=(time.perf_counter()-started)*1000)
            results.append(row)
            print(json.dumps(row), flush=True)
    args.output.write_text(json.dumps(results, indent=2), encoding="utf-8")

if __name__ == "__main__":
    main()
