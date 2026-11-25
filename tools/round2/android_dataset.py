import argparse
import csv
import hashlib
import io
import json
import subprocess
import tempfile
import zipfile
from pathlib import Path

APP = "com.mikuwwl.wakeguard"
TEST = APP + ".LocalVisionDatasetTest"
RUNTIME = APP + ".test/androidx.test.runner.AndroidJUnitRunner"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    def adb(*command, timeout=90):
        result = subprocess.run(["adb", "-s", args.serial, *command],
            check=True, capture_output=True, timeout=timeout)
        return result.stdout

    if adb("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("Only an emulator is allowed")
    if adb("emu","avd","name").splitlines()[0].strip() != b"WakeGuard_API36":
        raise RuntimeError("Use the WakeGuard_API36 AVD")
    with args.manifest.open(newline="", encoding="utf-8-sig") as manifest:
        records = [row for row in csv.DictReader(manifest)
            if row["split"] in ("calibration","holdout","historical") and row["scored"] == "1"]
    bundle = io.BytesIO()
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_STORED) as archive:
        pairs = []
        for index, row in enumerate(records):
            photo = Path(row["local_path"]).read_bytes()
            if hashlib.sha256(photo).hexdigest().lower() != row["sha256"].lower():
                raise ValueError("Fixture hash changed: " + row["image_id"])
            filename = f"fixture-{index:03}.jpg"
            archive.writestr(filename, photo)
            pairs.append(dict(id=row["image_id"],file=filename))
        archive.writestr("cases.json", json.dumps(pairs))
    for apk in (args.apk, args.test_apk):
        print(adb("install", "-r", str(apk)).decode("utf8").strip(),flush=True)
    incoming = bundle.getvalue()
    digest = hashlib.sha256(incoming).hexdigest()
    staged = f"/data/local/tmp/wakeguard-round2-{digest[:12]}.zip"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(dir=args.output.parent) as directory:
            local = Path(directory) / "dataset.zip"
            local.write_bytes(incoming)
            adb("push", str(local), staged)
        adb("shell", f"run-as {APP} sh -c 'mkdir -p files/round2 && cp {staged} files/round2/input.zip'")
        remote = adb("shell", "run-as", APP, "sha256sum", "files/round2/input.zip").split()[0].decode()
        if remote != digest:
            raise RuntimeError("Device dataset digest differs from the reviewed bundle")
    finally:
        adb("shell", "rm", "-f", staged)
    result = adb("shell","am","instrument","-w","-r","-e","class",TEST,"-e","localDataset","1",RUNTIME,timeout=300)
    args.output.with_suffix(".log").write_bytes(result)
    if b"OK (1 test)" not in result:
        raise RuntimeError("Dataset instrumentation failed: " + result.decode("utf8","replace")[-1200:])
    data = adb("exec-out", "run-as", APP, "cat", "files/round2/scores.json")
    scores = json.loads(data)
    if len(scores) != len(records):
        raise RuntimeError("Android returned an incomplete dataset result")
    args.output.write_bytes(data)
    print("Measured",len(scores),"fixtures on Android.",flush=True)

if __name__ == "__main__":
    main()
