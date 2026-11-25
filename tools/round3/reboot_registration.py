import json
from pathlib import Path
import sqlite3
import sys
import time

sys.path.insert(0, r"D:\workshop\nov\WakeGuard\tools\round2")
from emulator_e2e import ACTIVITY, Emulator, PKG

out = Path(r"D:\workshop\nov\reports\round3\reboot-registration")
device = Emulator("emulator-5554", out)
device.shell("appops", "set", PKG, "SCHEDULE_EXACT_ALARM", "allow")
device.shell("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
device.shell("pm", "grant", PKG, "android.permission.CAMERA", check=False)

def texts(tree):
    return [node.get("text") for node in tree.iter("node") if node.get("text")]

def current_alarm(label):
    device.snapshot_room(label)
    with sqlite3.connect(out / label / "wakeguard.db") as db:
        db.row_factory = sqlite3.Row
        row = db.execute("SELECT * FROM alarms ORDER BY id DESC LIMIT 1").fetchone()
    data = dict(row) if row else {}
    (out / label / "latest-alarm.json").write_text(json.dumps(data, indent=2), encoding="utf8")
    return data

assert not device.active_service()
device.wake()
device.shell("am", "start", "-W", "-n", ACTIVITY)
time.sleep(1)
home = device.dump("01-initial")
if "返回闹钟列表" in texts(home):
    device.tap(home, "返回闹钟列表")
    time.sleep(1)
    home = device.dump("01b-alarm-list-after-result")
if "闹钟" in texts(home) and not any(n.get("content-desc") == "新增闹钟" for n in home.iter("node")):
    device.tap(home, "闹钟")
    home = device.dump("02-home")
device.tap(home, "新增闹钟")
editor = device.dump("03-editor")
tomorrow = int(device.shell("date", "+%u").strip()) % 7
for index, day in enumerate("一二三四五六日"):
    if index != tomorrow:
        device.tap(editor, day)
device.tap(editor, "保存闹钟")
time.sleep(2)
before = current_alarm("04-before-room")
assert before["scheduledAt"] and before["deliveryToken"] and before["repeatMask"] == 1 << tomorrow, before
assert before["scheduledAt"] - time.time() * 1000 > 3600000, before
a = device.capture("05-before-schedule", "dumpsys", "alarm")
assert str(before["scheduledAt"]) in a and "com.mikuwwl.wakeguard.action.TRIGGER" in a
boot_file = "/proc/sys/kernel/random/boot_id"
before_boot = device.capture("06-before-boot-id", "cat", boot_file).strip()
print("Registered tomorrow's event and issued adb reboot.", flush=True)
device.text("reboot")
deadline = time.monotonic() + 170
while time.monotonic() < deadline:
    boot = device.shell("cat", boot_file, check=False).strip()
    ready = device.shell("getprop", "sys.boot_completed", check=False).strip()
    if boot and boot != before_boot and ready == "1":
        logs = device.shell("logcat", "-d", "-s", "WakeGuardRestore:I", check=False)
        if "Rescheduled alarms after android.intent.action.BOOT_COMPLETED" in logs:
            break
    time.sleep(3)
else:
    raise RuntimeError("No new BOOT_COMPLETED restoration before timeout")
device.capture("07-after-boot-id", "cat", boot_file)
device.capture("08-restore-logcat", "logcat", "-d", "-s", "WakeGuardRestore:I")
after = current_alarm("09-after-room")
assert before["id"] == after["id"] and before["scheduledAt"] == after["scheduledAt"], (before, after)
assert before["deliveryToken"] != after["deliveryToken"] and after["deliveryToken"], (before, after)
a = device.capture("10-after-schedule", "dumpsys", "alarm")
assert str(after["scheduledAt"]) in a and "com.mikuwwl.wakeguard.action.TRIGGER" in a
assert not device.active_service()
print(json.dumps(dict(outcome="PASS", alarm_id=after["id"], restored_due_ms=after["scheduledAt"], boot_changed=True, token_changed=True)), flush=True)

device.wake()
device.shell("am", "start", "-W", "-n", ACTIVITY)
time.sleep(2)
t = f"{after['hour']:02}:{after['minute']:02}"
home = device.dump("11-after-home")
label = "删除闹钟 " + t
if sum(n.get("content-desc") == label for n in home.iter("node")) == 1:
    device.tap(home, label)
    dialog = device.dump("12-remove-test-schedule")
    if "删除" in texts(dialog):
        device.tap(dialog, "删除")
        time.sleep(1)
        cleaned = current_alarm("13-after-cleanup-room")
        assert cleaned.get("id") != after["id"]
        print("Removed only the alarm created for this reboot check.", flush=True)
