import argparse
import json
import re
import sqlite3
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree

PKG = "com.mikuwwl.wakeguard"
ACTIVITY = f"{PKG}/.presentation.MainActivity"

class Emulator:
    def __init__(self, serial, output):
        self.prefix = ["adb", "-s", serial]
        self.out = output
        output.mkdir(parents=True, exist_ok=True)
        if self.text("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("WakeGuard test requires an Android emulator.")
        if self.text("emu", "avd", "name").splitlines()[0].strip() != "WakeGuard_API36":
            raise RuntimeError("This walkthrough operates only on WakeGuard_API36.")

    def raw(self, *cmd, check=True):
        result = subprocess.run([*self.prefix,*cmd], check=check, capture_output=True, timeout=40)
        return result.stdout

    def text(self, *cmd, check=True):
        return self.raw(*cmd, check=check).decode("utf-8","replace")

    def shell(self,*cmd,check=True):
        return self.text("shell",*cmd,check=check)

    def dump(self, name):
        self.shell("uiautomator", "dump", "/sdcard/wakeguard-round2.xml")
        raw = self.raw("exec-out", "cat", "/sdcard/wakeguard-round2.xml")
        (self.out / f"{name}.xml").write_bytes(raw)
        return ElementTree.fromstring(raw)

    def shot(self,name):
        raw = self.raw("exec-out","screencap","-p")
        if not raw.startswith(b"\x89PNG"):
            raise RuntimeError("No emulator screenshot received.")
        (self.out / f"{name}.png").write_bytes(raw)

    def capture(self,name,*cmd):
        value=self.shell(*cmd)
        (self.out/f"{name}.txt").write_text(value,encoding="utf8")
        return value

    def tap(self,xml,label):
        for node in xml.iter("node"):
            if label not in (node.get("text"),node.get("content-desc")):
                continue
            x1,y1,x2,y2=map(int,re.findall(r"\d+",node.get("bounds")))
            self.shell("input","tap",str((x1+x2)//2),str((y1+y2)//2))
            return
        raise ValueError(f"Missing UI label {label!r}; current screen recorded.")

    def key(self,code):
        self.shell("input","keyevent",str(code))

    def wake(self):
        self.key(224)
        self.shell("wm","dismiss-keyguard",check=False)

    def state(self,label):
        return self.capture(label,"dumpsys","power")

    def active_service(self):
        return "isForeground=true" in self.shell("dumpsys","activity","services",PKG)

    def await_service(self, timeout):
        limit=time.monotonic()+timeout
        while time.monotonic()<limit:
            if self.active_service():
                return
            time.sleep(2)
        raise TimeoutError("Scheduled event never started the foreground service.")

    def snapshot_room(self, label):
        folder = self.out / label
        folder.mkdir(exist_ok=True)
        for tail in ["", "-wal"]:
            name = "wakeguard.db" + tail
            bytes_ = self.raw("exec-out","run-as",PKG,"cat",f"databases/{name}",check=False)
            if bytes_:
                (folder/name).write_bytes(bytes_)
        with sqlite3.connect(folder/"wakeguard.db") as database:
            database.row_factory = sqlite3.Row
            rows = [dict(row) for row in database.execute("SELECT * FROM wake_events ORDER BY id")]
            (folder/"wake-events.json").write_text(json.dumps(rows,indent=2),encoding="utf8")
        return rows

def scenario(device, name, restart_process=False, force_stop=False):
    device.wake()
    device.shell("am","start","-W","-n",ACTIVITY)
    time.sleep(1)
    home=device.dump("01-home")
    device.shot("01-home")
    # The editor defaults to the next minute. Start early enough to save before it.
    second = int(device.shell("date", "+%S").strip())
    if second >= 25:
        time.sleep(61 - second)
    device.tap(home,"新增闹钟")
    editor=device.dump("02-editor")
    for day in "一二三四五六日":
        device.tap(editor,day)
    device.tap(editor,"保存闹钟")
    time.sleep(1)
    device.capture("03-schedule","dumpsys","alarm")
    scheduled=device.dump("03-scheduled-home")
    if "下一个已排程闹钟" not in [node.get("text") for node in scheduled.iter("node")]:
        raise RuntimeError("The UI does not report a registered exact alarm.")
    device.shot("03-scheduled-home")
    device.key(223)
    sleep_state=device.state("04-screen-before-trigger")
    print("Before scheduled trigger:", [x.strip() for x in sleep_state.splitlines() if "mWakefulness=" in x], flush=True)
    device.await_service(95)
    time.sleep(1)
    device.capture("05-active-service","dumpsys","activity","services",PKG)
    device.capture("05-notification","dumpsys","notification")
    device.capture("05-audio","dumpsys","audio")
    device.capture("05-vibration","dumpsys","vibrator_manager")
    device.state("05-trigger-screen-state")
    if force_stop:
        device.shell("am", "force-stop", PKG)
        assert not device.active_service()
        device.wake()
        device.shell("am", "start", "-W", "-n", ACTIVITY)
        time.sleep(2)
        events = device.snapshot_room("force-stop-room")
        assert events[-1]["outcome"] == "INTERRUPTED", events[-1]
        assert not device.active_service()
        print(json.dumps({"scenario": name, "last_event": events[-1]}, ensure_ascii=False), flush=True)
        return
    if restart_process:
        old_pid = device.shell("pidof", PKG).strip()
        device.shell("run-as", PKG, "kill", "-9", old_pid)
        time.sleep(2)
        device.await_service(40)
        new_pid = device.shell("pidof", PKG).strip()
        assert old_pid != new_pid and new_pid
        device.capture("05-sticky-restart-log", "logcat", "-d", "-s", "WakeGuardAlarm:I")
        print(f"Sticky process recovery: {old_pid} -> {new_pid}", flush=True)
    device.wake()
    device.shell("am","start","-W","-n",ACTIVITY)
    ringing = device.dump("06-ringing")
    device.shot("06-ringing")
    device.tap(ringing,"开始现场验证")
    deadline = time.monotonic() + 26
    while True:
        camera = device.dump("07-camera")
        text = [node.get("text", "") for node in camera.iter("node")]
        if any(re.search(r"\d+% / \d+%", label) for label in text) or any(
            label.startswith(("需要相机权限", "无法启动相机", "本地识别暂不可用", "验证完成")) for label in text
        ) or time.monotonic() >= deadline:
            print("Camera UI:", [label for label in text if label], flush=True)
            break
        time.sleep(2)
    device.shot("07-camera")
    device.capture("07-memory","dumpsys","meminfo",PKG)
    if not device.active_service():
        print("Alarm ended after the live stream attempt. Inspecting the persisted result.",flush=True)
    else:
        device.key(4)
        device.dump("07-after-back")
        device.key(3)
        time.sleep(2)
        device.capture("08-home-services","dumpsys","activity","services",PKG)
        assert device.active_service()
        device.shell("am","start","-W","-n",ACTIVITY)
        device.dump("09-resumed")
        device.key(26)
        time.sleep(1)
        device.state("10-keycode-power")
        device.key(223)
        time.sleep(1)
        device.state("11-keycode-sleep")
        device.capture("11-audio-asleep","dumpsys","audio")
        assert device.active_service()
        device.wake()
        device.shell("am","start","-W","-n",ACTIVITY)
        time.sleep(3)
        after_wake=device.dump("12-after-wake")
        device.shot("12-after-wake")
        if device.active_service():
            emergency = "紧急停止" if any(n.get("text") == "紧急停止" for n in after_wake.iter("node")) else "紧急停止（记录为未完成）"
            device.tap(after_wake,emergency)
            dialog=device.dump("13-emergency-dialog")
            device.shot("13-emergency-dialog")
            for node in dialog.iter("node"):
                if node.get("text")=="紧急停止闹钟？":
                    break
            else:
                raise RuntimeError("Missing emergency confirmation")
            # The last visible exact label is the confirmation button above the camera UI.
            stops=[n for n in dialog.iter("node") if n.get("text")=="紧急停止"]
            if not stops:
                raise RuntimeError("No emergency stop confirmation button")
            temp=ElementTree.Element("confirmation")
            temp.append(stops[-1])
            device.tap(temp,"紧急停止")
            time.sleep(3)
            device.dump("14-result")
            device.shot("14-result")
    device.capture("15-after-services","dumpsys","activity","services",PKG)
    device.capture("15-after-notifications","dumpsys","notification")
    device.capture("15-after-audio","dumpsys","audio")
    device.capture("15-after-vibration","dumpsys","vibrator_manager")
    device.capture("15-wake-locks","dumpsys","power")
    device.capture("15-window-flags","dumpsys","window","windows")
    assert not device.active_service()
    result=device.snapshot_room("16-persisted")
    print(json.dumps({"scenario":name,"wake_events":result},ensure_ascii=False),flush=True)
    done=device.dump("17-result")
    if any(n.get("text")=="返回闹钟列表" for n in done.iter("node")):
        device.tap(done,"返回闹钟列表")
    home=device.dump("18-before-stats")
    if any(n.get("text")=="统计" for n in home.iter("node")):
        device.tap(home,"统计")
        time.sleep(1)
        device.dump("19-stats")
        device.shot("19-stats")

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--serial",default="emulator-5554")
    p.add_argument("--evidence",required=True,type=Path)
    p.add_argument("--name",default="full-scene")
    mode=p.add_mutually_exclusive_group()
    mode.add_argument("--restart-process", action="store_true")
    mode.add_argument("--force-stop", action="store_true")
    args=p.parse_args()
    device=Emulator(args.serial,args.evidence)
    try:
        scenario(device,args.name,args.restart_process,args.force_stop)
    finally:
        logs=device.text("logcat","-d","-t","2800")
        (device.out/"logcat.txt").write_text(logs,encoding="utf8")

if __name__=="__main__":
    main()
