#!/usr/bin/env python
"""Train a local 28-class MobileNetV3-Small classifier and export ONNX."""

from __future__ import annotations

import argparse
import csv
import json
import random
from collections import defaultdict
from pathlib import Path

import numpy as np
import torch
from PIL import Image, ImageOps
from torch import nn
from torch.utils.data import DataLoader, Dataset, Subset, WeightedRandomSampler
from torchvision import datasets, models, transforms


SEED = 1337
TARGET_CLASSES = (20, 21)
IMAGE_SIZE = 224
MEAN = (0.485, 0.456, 0.406)
STD = (0.229, 0.224, 0.225)


class FiveCropDataset(Dataset):
    def __init__(self, folder: Path):
        self.base = datasets.ImageFolder(str(folder))

    def __len__(self):
        return len(self.base.samples)

    @staticmethod
    def crop_views(image: Image.Image) -> list[Image.Image]:
        width, height = image.size
        crop_width = max(1, int(width * 0.62))
        crop_height = max(1, int(height * 0.62))
        boxes = [
            (0, 0, width, height),
            (0, 0, crop_width, crop_height),
            (width - crop_width, 0, width, crop_height),
            (0, height - crop_height, crop_width, height),
            (width - crop_width, height - crop_height, width, height),
        ]
        return [ImageOps.fit(image.crop(box), (IMAGE_SIZE, IMAGE_SIZE), method=Image.Resampling.BILINEAR) for box in boxes]

    def __getitem__(self, index: int):
        path, label = self.base.samples[index]
        with Image.open(path) as source:
            image = ImageOps.exif_transpose(source).convert("RGB")
            views = self.crop_views(image)
        tensor_views = torch.stack([EVAL_TRANSFORM(view) for view in views])
        return tensor_views, label, path


TRAIN_TRANSFORM = transforms.Compose([
    transforms.RandomResizedCrop(IMAGE_SIZE, scale=(0.45, 1.0), ratio=(0.72, 1.38)),
    transforms.RandomHorizontalFlip(p=0.5),
    transforms.RandomRotation(12),
    transforms.ColorJitter(brightness=0.18, contrast=0.18, saturation=0.14, hue=0.025),
    transforms.ToTensor(),
    transforms.Normalize(MEAN, STD),
])
EVAL_TRANSFORM = transforms.Compose([
    transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
    transforms.ToTensor(),
    transforms.Normalize(MEAN, STD),
])


def build_model(classes: int) -> nn.Module:
    model = models.mobilenet_v3_small(weights=None)
    model.classifier[-1] = nn.Linear(model.classifier[-1].in_features, classes)
    return model


def evaluate(model: nn.Module, folder: Path, device: torch.device, batch_size: int) -> tuple[np.ndarray, np.ndarray, list[str]]:
    data = FiveCropDataset(folder)
    loader = DataLoader(data, batch_size=batch_size, shuffle=False, num_workers=0)
    scores: list[np.ndarray] = []
    labels: list[np.ndarray] = []
    paths: list[str] = []
    model.eval()
    with torch.inference_mode():
        for views, targets, files in loader:
            batch, crops, channels, height, width = views.shape
            logits = model(views.view(batch * crops, channels, height, width).to(device))
            probabilities = torch.softmax(logits, dim=1).view(batch, crops, -1)
            toothpaste = probabilities[:, :, TARGET_CLASSES[0]] + probabilities[:, :, TARGET_CLASSES[1]]
            scores.extend(toothpaste.max(dim=1).values.cpu().numpy())
            labels.extend(targets.numpy())
            paths.extend(files)
    return np.asarray(scores, dtype=np.float32), np.asarray(labels, dtype=np.int64), paths


def confusion(scores: np.ndarray, labels: np.ndarray, threshold: float) -> dict[str, float | int]:
    truth = np.isin(labels, TARGET_CLASSES)
    predicted = scores >= threshold
    tp = int(np.count_nonzero(truth & predicted))
    fp = int(np.count_nonzero(~truth & predicted))
    tn = int(np.count_nonzero(~truth & ~predicted))
    fn = int(np.count_nonzero(truth & ~predicted))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    specificity = tn / (tn + fp) if tn + fp else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"tp": tp, "fp": fp, "tn": tn, "fn": fn, "precision": precision, "recall": recall, "specificity": specificity, "f1": f1}


def select_threshold(scores: np.ndarray, labels: np.ndarray) -> tuple[float, dict]:
    candidates = np.arange(0.20, 0.991, 0.01)
    measured = [(float(value), confusion(scores, labels, float(value))) for value in candidates]
    safe = [item for item in measured if item[1]["precision"] >= 0.90]
    if safe:
        return max(safe, key=lambda item: (item[1]["recall"], item[1]["precision"]))
    return max(measured, key=lambda item: (item[1]["f1"], item[1]["precision"]))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--model-out", required=True, type=Path)
    parser.add_argument("--metrics-out", required=True, type=Path)
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()
    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    torch.set_num_threads(min(8, torch.get_num_threads()))
    try:
        torch.set_num_interop_threads(4)
    except RuntimeError:
        pass

    train_folder = args.dataset / "train"
    test_folder = args.dataset / "test"
    all_train = datasets.ImageFolder(str(train_folder), transform=TRAIN_TRANSFORM)
    class_names = all_train.classes
    if len(class_names) != 28 or class_names[20] != "20_sensodyne_toothpaste" or class_names[21] != "21_colgate_toothpaste":
        raise SystemExit(f"Unexpected class map: {class_names}")

    grouped: dict[int, list[int]] = defaultdict(list)
    for index, (_, target) in enumerate(all_train.samples):
        grouped[target].append(index)
    train_indices: list[int] = []
    validation_indices: list[int] = []
    for target, indices in sorted(grouped.items()):
        random.shuffle(indices)
        validation_count = max(1, round(len(indices) * 0.10))
        validation_indices.extend(indices[:validation_count])
        train_indices.extend(indices[validation_count:])

    targets = np.asarray([all_train.samples[index][1] for index in train_indices], dtype=np.int64)
    per_class = np.bincount(targets, minlength=28)
    sample_weights = torch.as_tensor(1.0 / per_class[targets], dtype=torch.double)
    sampler = WeightedRandomSampler(sample_weights, num_samples=len(train_indices), replacement=True)
    train_loader = DataLoader(Subset(all_train, train_indices), batch_size=args.batch_size, sampler=sampler, num_workers=0)

    device = torch.device("cpu")
    model = build_model(28).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=0.001, weight_decay=0.0001)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)
    loss_function = nn.CrossEntropyLoss()
    validation_folder = args.dataset / "train"
    best_state = None
    best_f1 = -1.0
    best_epoch = 0

    for epoch in range(1, args.epochs + 1):
        model.train()
        running_loss = 0.0
        for step, (images, labels) in enumerate(train_loader, start=1):
            images, labels = images.to(device), labels.to(device)
            optimizer.zero_grad(set_to_none=True)
            logits = model(images)
            loss = loss_function(logits, labels)
            loss.backward()
            optimizer.step()
            running_loss += float(loss.detach())
            if step % 100 == 0:
                print(f"epoch={epoch}/{args.epochs} step={step}/{len(train_loader)} loss={running_loss / step:.4f}", flush=True)
        scheduler.step()

        # Evaluate the deterministic holdout created above without modifying the public test split.
        validation = Subset(datasets.ImageFolder(str(validation_folder), transform=EVAL_TRANSFORM), validation_indices)
        val_loader = DataLoader(validation, batch_size=args.batch_size, shuffle=False, num_workers=0)
        # The validation pass uses one full-frame image per row; final calibration and test use the five-crop path.
        val_scores: list[float] = []
        val_labels: list[int] = []
        model.eval()
        with torch.inference_mode():
            for images, labels in val_loader:
                logits = model(images.to(device))
                probs = torch.softmax(logits, dim=1)
                val_scores.extend((probs[:, 20] + probs[:, 21]).cpu().numpy().tolist())
                val_labels.extend(labels.numpy().tolist())
        threshold, val_metrics = select_threshold(np.asarray(val_scores), np.asarray(val_labels))
        print(f"epoch={epoch} mean_loss={running_loss / max(1, len(train_loader)):.4f} val_threshold={threshold:.2f} val={val_metrics}", flush=True)
        if val_metrics["f1"] > best_f1:
            best_f1 = val_metrics["f1"]
            best_epoch = epoch
            best_state = {key: value.detach().cpu().clone() for key, value in model.state_dict().items()}

    if best_state is None:
        raise RuntimeError("Training did not produce a model checkpoint")
    model.load_state_dict(best_state)
    model.to(device).eval()

    # Recalibrate the chosen checkpoint on the held-out validation subset with the exact five-crop path used on-device.
    val_scores, val_labels, _ = evaluate_subset(model, args.dataset, validation_indices, device, args.batch_size)
    threshold, validation_metrics = select_threshold(val_scores, val_labels)
    test_scores, test_labels, test_paths = evaluate(model, test_folder, device, args.batch_size)
    test_metrics = confusion(test_scores, test_labels, threshold)

    args.model_out.parent.mkdir(parents=True, exist_ok=True)
    dummy = torch.zeros(1, 3, IMAGE_SIZE, IMAGE_SIZE, dtype=torch.float32)
    torch.onnx.export(
        model,
        dummy,
        args.model_out,
        input_names=["input"],
        output_names=["logits"],
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )
    import onnx
    onnx.checker.check_model(onnx.load(str(args.model_out)))

    args.metrics_out.parent.mkdir(parents=True, exist_ok=True)
    with args.metrics_out.with_suffix(".csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["image", "true_is_toothpaste", "toothpaste_score", "predicted_at_threshold"])
        for path, label, score in zip(test_paths, test_labels, test_scores):
            writer.writerow([Path(path).name, int(label in TARGET_CLASSES), f"{float(score):.6f}", int(score >= threshold)])
    metrics = {
        "architecture": "torchvision MobileNetV3-Small, randomly initialized (no pretrained weights)",
        "dataset": "Anilot/MIMEX",
        "dataset_revision": "fa7017f07b10efdb4dc461c14b8da1ae8aa01e73",
        "dataset_license_tag": "MIT",
        "seed": SEED,
        "epochs_requested": args.epochs,
        "best_epoch": best_epoch,
        "train_images": len(train_indices),
        "validation_images": len(validation_indices),
        "test_images": len(test_paths),
        "target_classes": ["sensodyne_toothpaste", "colgate_toothpaste"],
        "inference_views": 5,
        "threshold_selected_from_validation": threshold,
        "validation": validation_metrics,
        "test": test_metrics,
        "test_product_image_counts": {
            "positive": int(np.count_nonzero(np.isin(test_labels, TARGET_CLASSES))),
            "negative": int(np.count_nonzero(~np.isin(test_labels, TARGET_CLASSES))),
        },
        "onnx_model": str(args.model_out),
    }
    args.metrics_out.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    print(json.dumps(metrics, indent=2), flush=True)


def evaluate_subset(model: nn.Module, dataset_root: Path, indices: list[int], device: torch.device, batch_size: int):
    data = FiveCropDataset(dataset_root / "train")
    subset = Subset(data, indices)
    loader = DataLoader(subset, batch_size=batch_size, shuffle=False, num_workers=0)
    all_scores: list[float] = []
    all_labels: list[int] = []
    all_paths: list[str] = []
    model.eval()
    with torch.inference_mode():
        for views, labels, paths in loader:
            batch, crops, channels, height, width = views.shape
            logits = model(views.view(batch * crops, channels, height, width).to(device))
            probabilities = torch.softmax(logits, dim=1).view(batch, crops, -1)
            scores = probabilities[:, :, 20] + probabilities[:, :, 21]
            all_scores.extend(scores.max(dim=1).values.cpu().numpy().tolist())
            all_labels.extend(labels.numpy().tolist())
            all_paths.extend(paths)
    return np.asarray(all_scores), np.asarray(all_labels), all_paths


if __name__ == "__main__":
    main()
