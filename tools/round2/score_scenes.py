import argparse
import csv
import itertools
import json
import math
from pathlib import Path
from statistics import median

CLASSES = ("sink", "toothbrush", "toothpaste")
GRID = (.02, .05, .075, .10, .15, .20, .25, .30, .35, .40, .45, .50, .55, .60, .65, .70, .75, .80, .85, .90, .93, .95)

def fraction(n, d):
    return n / d if d else None

def wilson(k, n):
    if not n:
        return None
    z = 1.95996398454
    p = k / n
    m = 1 + z*z/n
    center = (p + z*z/(2*n))/m
    radius = z*math.sqrt(p*(1-p)/n + z*z/(4*n*n))/m
    return [max(0, center-radius), min(1, center+radius)]

def iou(a, b):
    shared=max(0., min(a[2],b[2])-max(a[0],b[0])) * max(0.,min(a[3],b[3])-max(a[1],b[1]))
    area=lambda box: max(0.,box[2]-box[0])*max(0.,box[3]-box[1])
    return shared / max(1e-10, area(a)+area(b)-shared)

def align(scored, truth, split):
    expected = {k for k,v in truth.items() if v["split"] == split and v["scored"] == "1"}
    data = {row["id"]:row for row in scored if row["id"] in expected}
    if set(data) != expected or len(scored) != len({r["id"] for r in scored}):
        raise ValueError(f"Wrong or duplicate IDs for {split}: missing {expected - set(data)}")
    return [(truth[key],data[key]) for key in sorted(expected)]

def has_distinct_dental_objects(result):
    boxes = result.get("boxes",{})
    if "toothbrush" not in boxes or "toothpaste" not in boxes:
        return not boxes
    return iou(boxes["toothbrush"],boxes["toothpaste"]) <= .60

def calculate(samples, thresholds):
    detail = {c:[0,0,0,0] for c in CLASSES}
    grouped={}
    bad=0
    localized={}
    for row,result in samples:
        expected = [row[c] == "1" for c in CLASSES]
        scores = [result["scores"][c] for c in CLASSES]
        finite = all(math.isfinite(s) and 0 <= s <= 1 for s in scores)
        if not finite:
            bad+=1
        predicted = [finite and s >= t for s,t in zip(scores,thresholds)]
        for c,e,p in zip(CLASSES,expected,predicted):
            detail[c][(0 if p else 3) if e else (1 if p else 2)] += 1
        ok=all(predicted) and has_distinct_dental_objects(result)
        group=grouped.setdefault(row["scene_group"],(all(expected), []))
        if group[0] != all(expected):
            raise ValueError("A scored group mixes positive and negative frames")
        group[1].append(ok)
        if result.get("boxes") and all(expected):
            target=json.loads(row["bbox_xyxy_normalized"])
            if target:
                localized[row["image_id"]]={c:max(
                    (iou(result["boxes"][c], box) for box in target.get(c,[])), default=0.)
                    if c in result["boxes"] else 0. for c in CLASSES}
    class_report={}
    for c,(tp,fp,tn,fn) in detail.items():
        class_report[c]=dict(tp=tp,fp=fp,tn=tn,fn=fn,precision=fraction(tp,tp+fp),
            recall=fraction(tp,tp+fn),f1=fraction(2*tp,2*tp+fp+fn))
    positives=[(key, flags) for key,(positive,flags) in grouped.items() if positive]
    negatives=[(key, flags) for key,(positive,flags) in grouped.items() if not positive]
    passed=[key for key,flags in positives if all(flags)]
    false=[key for key,flags in negatives if any(flags)]
    times=sorted(row["latency_ms"] for _,row in samples if row.get("latency_ms") is not None)
    return dict(
        thresholds=dict(zip(CLASSES,thresholds)),
        photos=len(samples),
        positive_groups=len(positives), positive_accepts=len(passed),
        positive_acceptance_rate=fraction(len(passed),len(positives)),
        positive_acceptance_wilson95=wilson(len(passed),len(positives)), positive_passes=passed,
        negative_groups=len(negatives), false_accepts=len(false),
        false_acceptance_rate=fraction(len(false),len(negatives)),
        false_acceptance_wilson95=wilson(len(false),len(negatives)), false_accept_groups=false,
        negative_label_patterns=sorted({"".join(row[c] for c in CLASSES) for row,_ in samples
                                       if not all(row[c]=="1" for c in CLASSES)}),
        invalid_score_rows=bad, per_class_presence=class_report, positive_box_iou=localized,
        latency_samples=len(times),
        latency_p50_ms=median(times) if times else None,
        latency_p95_ms=times[math.ceil(.95*len(times))-1] if times else None,
    )

def calibrate(samples):
    def rank(values):
        report=calculate(samples,values)
        macro=sum(detail["f1"] or 0 for detail in report["per_class_presence"].values())
        return (-report["false_accepts"],report["positive_accepts"],macro,sum(values))
    return max(itertools.product(GRID, repeat=3), key=rank)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--mode", choices=["calibrate","score"], required=True)
    parser.add_argument("--split", default="holdout")
    parser.add_argument("--locked-thresholds", type=Path)
    parser.add_argument("--fixed", type=float, nargs=3)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("predictions", type=Path)
    args=parser.parse_args()
    with args.manifest.open(encoding="utf-8-sig",newline="") as file:
        truth={row["image_id"]:row for row in csv.DictReader(file)}
    values=json.loads(args.predictions.read_text(encoding="utf-8-sig"))
    if args.mode=="calibrate":
        samples=align(values,truth,"calibration")
        thresholds=calibrate(samples)
    else:
        samples=align(values,truth,args.split)
        if args.fixed:
            thresholds=args.fixed
        else:
            data=json.loads(args.locked_thresholds.read_text(encoding="utf-8-sig"))["thresholds"]
            thresholds=[data[c] for c in CLASSES]
    report=calculate(samples,thresholds)
    args.output.write_text(json.dumps(report, indent=2),encoding="utf8")
    print(json.dumps(report))

if __name__=="__main__":
    main()
