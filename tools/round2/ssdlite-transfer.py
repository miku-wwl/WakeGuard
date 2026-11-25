import argparse
import csv
import json
import random
import time
from pathlib import Path

import torch
from PIL import Image, ImageEnhance, ImageOps
from torch.utils.data import DataLoader, Dataset
from torchvision.models.detection import ssdlite320_mobilenet_v3_large
from torchvision.transforms.functional import pil_to_tensor

CLASSES = ("sink", "toothbrush", "toothpaste")

class BoxPhotos(Dataset):
    def __init__(self, rows, sample_count):
        self.rows = rows
        self.sample_count = sample_count

    def __len__(self):
        return self.sample_count

    def __getitem__(self, item):
        row = self.rows[item % len(self.rows)]
        with Image.open(row["local_path"]) as source:
            im = source.convert("RGB")
        boxes = []
        labels = []
        for label, items in json.loads(row["bbox_xyxy_normalized"]).items():
            boxes.extend(items)
            labels.extend([CLASSES.index(label) + 1] * len(items))
        scale = random.uniform(.68, 1)
        resized = im.resize((max(2, int(im.width * scale)), max(2, int(im.height * scale))))
        dx = random.randint(0, im.width - resized.width)
        dy = random.randint(0, im.height - resized.height)
        canvas = Image.new("RGB", im.size, (112, 112, 112))
        canvas.paste(resized, (dx,dy))
        sx, sy = resized.width, resized.height
        boxes = [(x1*sx+dx, y1*sy+dy, x2*sx+dx, y2*sy+dy) for x1,y1,x2,y2 in boxes]
        if random.random() < .5:
            canvas = ImageOps.mirror(canvas)
            boxes = [(im.width-x2, y1, im.width-x1, y2) for x1,y1,x2,y2 in boxes]
        canvas = ImageEnhance.Brightness(canvas).enhance(random.uniform(.72, 1.25))
        target = {
            "boxes": torch.tensor(boxes,dtype=torch.float32).reshape(-1,4),
            "labels": torch.tensor(labels,dtype=torch.int64),
        }
        return pil_to_tensor(canvas).float().div(255), target

def build(pretrained):
    original = ssdlite320_mobilenet_v3_large(weights=None, weights_backbone=None, num_classes=91)
    original.load_state_dict(torch.load(pretrained, map_location="cpu", weights_only=True))
    mobile = ssdlite320_mobilenet_v3_large(weights=None, weights_backbone=None, num_classes=4,
        score_thresh=0.001, detections_per_img=100)
    mobile.backbone.load_state_dict(original.backbone.state_dict())
    mobile.head.regression_head.load_state_dict(original.head.regression_head.state_dict())
    with torch.no_grad():
        for source, target in zip(original.head.classification_head.module_list, mobile.head.classification_head.module_list):
            target[0].load_state_dict(source[0].state_dict())
            sw = source[1].weight.reshape(-1, 91, *source[1].weight.shape[1:])
            sb = source[1].bias.reshape(-1, 91)
            tw = target[1].weight.view(-1, 4, *target[1].weight.shape[1:])
            tb = target[1].bias.view(-1, 4)
            tw[:, :3].copy_(sw[:, [0,81,90]])
            tb[:, :3].copy_(sb[:, [0,81,90]])
            tw[:, 3].copy_(sw[:, [44,47,88,90]].mean(1))
            tb[:, 3].fill_(-2.5)
    mobile.backbone.requires_grad_(False)
    mobile.head.regression_head.requires_grad_(False)
    return mobile

def main():
    arg = argparse.ArgumentParser()
    arg.add_argument("--manifest", type=Path, required=True)
    arg.add_argument("--pretrained", type=Path, required=True)
    arg.add_argument("--checkpoint", type=Path, required=True)
    arg.add_argument("--output", type=Path, required=True)
    arg.add_argument("--epochs", type=int, choices=range(0,25), default=10)
    arg.add_argument("--split", choices=("calibration", "historical", "holdout"), default="calibration")
    args=arg.parse_args()
    random.seed(7302)
    torch.manual_seed(7302)
    torch.set_num_threads(3)
    torch.set_num_interop_threads(1)
    with args.manifest.open(encoding="utf-8-sig",newline="") as file:
        rows=list(csv.DictReader(file))
    train = [row for row in rows if row["split"]=="training" and row["scored"]=="1"]
    model=build(args.pretrained)
    if args.epochs:
        loader=DataLoader(BoxPhotos(train, len(train)*3),batch_size=4,shuffle=True,collate_fn=lambda pairs: tuple(zip(*pairs)))
        optimizer=torch.optim.AdamW(model.head.classification_head.parameters(),lr=.0012)
        for epoch in range(args.epochs):
            model.train()
            model.backbone.eval()
            model.head.regression_head.eval()
            loss_sum=0.
            for images,targets in loader:
                optimizer.zero_grad(set_to_none=True)
                objective=model(list(images), list(targets))["classification"]
                if not torch.isfinite(objective):
                    raise RuntimeError("Non-finite training loss")
                objective.backward()
                optimizer.step()
                loss_sum+=float(objective.detach())
            print(f"epoch {epoch+1} / {args.epochs}  classification loss {loss_sum/len(loader):.5f}",flush=True)
            torch.save(model.state_dict(), args.checkpoint)
    else:
        model.load_state_dict(torch.load(args.checkpoint, map_location="cpu", weights_only=True))
    model.eval()
    scored=[]
    with torch.inference_mode():
        for row in rows:
            if row["split"]!=args.split or row["scored"]!="1":
                continue
            with Image.open(row["local_path"]) as source:
                image=source.convert("RGB")
            start=time.perf_counter()
            result=model([pil_to_tensor(image).float().div(255)])[0]
            maxima={key:0. for key in CLASSES}
            boxes={}
            for label,score,box in zip(result["labels"],result["scores"],result["boxes"]):
                key=CLASSES[int(label)-1]
                if float(score)>maxima[key]:
                    maxima[key]=float(score)
                    boxes[key]=[float(c)/image.size[i%2] for i,c in enumerate(box)]
            output=dict(id=row["image_id"], scores=maxima, boxes=boxes, latency_ms=(time.perf_counter()-start)*1000)
            print(json.dumps(output),flush=True)
            scored.append(output)
    args.output.write_text(json.dumps(scored,indent=2),encoding="utf-8")

if __name__=="__main__":
    main()
