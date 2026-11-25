#!/usr/bin/env python3
"""Run the production alarm -> CameraX -> VERIFIED path on WakeGuard_API36."""

import argparse
import json
import re
import shlex
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree

PKG = "com.mikuwwl.wakeguard"
ACTIVITY = f"{PKG}/.presentation.MainActivity"


class Device:
    def __init__(self, serial: str, evidence: Path):
        self.serial = serial
        self.evidence = evidence
        evidence.mkdir(parents=True, exist_ok=True)
        if self.shell("getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("Refusing to run the E2E acceptance test on a physical device.")
        name = self.text("emu", "avd", "name").splitlines()[0].strip()
        if name != "WakeGuard_API36":
            raise RuntimeError(f"Expected WakeGuard_API36, got {name!r}.")

    def raw(self, *args: str, check: bool = True) -> bytes:
        result = subprocess.run(
            ["adb", "-s", self.serial, *args], capture_output=True, timeout=90, check=False
        )
        if check and result.returncode:
            raise RuntimeError(f"adb {args!r} failed: {result.stderr.decode(errors='replace')}")
        return result.stdout

    def text(self, *args: str, check: bool = True) -> str:
        return self.raw(*args, check=check).decode("utf-8", "replace")

    def shell(self, *args: str, check: bool = True) -> str:
        return self.text("shell", *args, check=check)

    def capture(self, name: str, *args: str) -> str:
        value = self.shell(*args)
        (self.evidence / f"{name}.txt").write_text(value, encoding="utf-8")
        return value

    def screenshot(self, name: str) -> Path:
        data = self.raw("exec-out", "screencap", "-p")
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError(f"ADB did not return a PNG screenshot for {name}.")
        path = self.evidence / f"{name}.png"
        path.write_bytes(data)
        return path

    def dump_ui(self, name: str) -> ElementTree.Element:
        self.shell("uiautomator", "dump", "/sdcard/wakeguard-round3.xml")
        raw = self.raw("exec-out", "cat", "/sdcard/wakeguard-round3.xml")
        (self.evidence / f"{name}.xml").write_bytes(raw)
        return ElementTree.fromstring(raw)

    def tap(self, xml: ElementTree.Element, label: str) -> None:
        for node in xml.iter("node"):
            if label not in (node.get("text"), node.get("content-desc")):
                continue
            bounds = node.get("bounds") or ""
            values = list(map(int, re.findall(r"\d+", bounds)))
            if len(values) != 4:
                continue
            x1, y1, x2, y2 = values
            self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return
        raise RuntimeError(f"Could not find UI text or description {label!r}; XML was saved.")

    def press(self, keycode: int) -> None:
        self.shell("input", "keyevent", str(keycode))

    def wake(self) -> None:
        self.press(224)  # KEYCODE_WAKEUP
        self.shell("wm", "dismiss-keyguard", check=False)

    def start_app(self) -> None:
        self.shell("am", "start", "-W", "-n", ACTIVITY)

    def foreground_service(self) -> bool:
        value = self.shell("dumpsys", "activity", "services", PKG)
        return "isForeground=true" in value or "foregroundId=" in value

    def wait_for_service(self, timeout: int = 110) -> str:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.foreground_service():
                rows = self.query("SELECT id, alarmId, triggeredAt, finishedAt, outcome, verificationStartedAt FROM wake_events WHERE outcome IN ('RINGING','VERIFYING') ORDER BY id DESC LIMIT 1;")
                if rows:
                    return str(rows[0]["id"])
            time.sleep(1)
        raise TimeoutError("No active Room event and foreground service appeared after the scheduled trigger.")

    def query(self, sql: str) -> list[dict]:
        command = f"run-as {PKG} sqlite3 -json databases/wakeguard.db {shlex.quote(sql)}"
        text = self.shell(command).strip()
        if not text:
            return []
        try:
            value = json.loads(text)
        except json.JSONDecodeError as exc:
            raise RuntimeError(f"Android sqlite3 did not return JSON: {text[:500]!r}") from exc
        return value if isinstance(value, list) else []

    def snapshot_room(self, prefix: str) -> dict:
        events = self.query("SELECT id, alarmId, triggeredAt, finishedAt, outcome, verificationStartedAt, failedAttempts FROM wake_events ORDER BY id;")
        alarms = self.query("SELECT id, label, enabled, hour, minute, repeatMask, scheduledAt, deliveryToken FROM alarms ORDER BY id;")
        stats = self.query("SELECT COUNT(*) AS verified_count FROM wake_events WHERE outcome='VERIFIED';")
        snapshot = {"events": events, "alarms": alarms, "stats": stats[0] if stats else {"verified_count": 0}}
        (self.evidence / f"{prefix}-room.json").write_text(json.dumps(snapshot, indent=2), encoding="utf-8")
        for file_name in ("wakeguard.db", "wakeguard.db-wal", "wakeguard.db-shm"):
            data = self.raw("exec-out", "run-as", PKG, "cat", f"databases/{file_name}", check=False)
            if data:
                (self.evidence / f"{prefix}-{file_name}").write_bytes(data)
        return snapshot

    def assert_active_event(self, event_id: str, expected_outcome: str | None = None) -> dict:
        rows = self.query(f"SELECT id, alarmId, triggeredAt, finishedAt, outcome, verificationStartedAt FROM wake_events WHERE id={int(event_id)};")
        if len(rows) != 1:
            raise AssertionError(f"Expected one event {event_id}, got {rows}.")
        event = rows[0]
        if expected_outcome and event["outcome"] != expected_outcome:
            raise AssertionError(f"Event {event_id} outcome={event['outcome']}, expected {expected_outcome}.")
        return event

    def notification_dump(self, name: str) -> str:
        value = self.capture(name, "dumpsys", "notification", "--noredact")
        return value

    def task_id(self) -> int | None:
        activity_dump = self.shell("dumpsys", "activity", "activities")
        for line in activity_dump.splitlines():
            if PKG in line and "MainActivity" in line:
                match = re.search(r"\bt(\d+)\b", line)
                if match:
                    return int(match.group(1))
        return None


def dump_text(xml: ElementTree.Element) -> list[str]:
    return [node.get("text", "") for node in xml.iter("node") if node.get("text")]


def require_text(xml: ElementTree.Element, text: str) -> None:
    if text not in dump_text(xml):
        raise AssertionError(f"Expected UI text {text!r}; got {dump_text(xml)}")


def emergency_cleanup(device: Device, evidence_prefix: str) -> dict:
    camera_or_ringing = device.dump_ui(f"{evidence_prefix}-emergency-entry")
    labels = dump_text(camera_or_ringing)
    if "紧急停止" not in labels and "紧急停止（记录为未完成）" not in labels:
        raise RuntimeError(f"No emergency stop control was visible for cleanup: {labels}")
    device.screenshot(f"{evidence_prefix}-emergency-entry")
    button = "紧急停止（记录为未完成）" if "紧急停止（记录为未完成）" in labels else "紧急停止"
    device.tap(camera_or_ringing, button)
    time.sleep(0.5)
    dialog = device.dump_ui(f"{evidence_prefix}-emergency-confirm")
    require_text(dialog, "紧急停止闹钟？")
    device.screenshot(f"{evidence_prefix}-emergency-confirm")
    device.tap(dialog, "紧急停止")
    time.sleep(2)
    result = device.dump_ui(f"{evidence_prefix}-emergency-result")
    require_text(result, "已紧急停止；本次未完成验证")
    device.screenshot(f"{evidence_prefix}-emergency-result")
    snapshot = device.snapshot_room(f"{evidence_prefix}-emergency-room")
    cleanup = {
        "event_id": snapshot["events"][-1]["id"],
        "outcome": snapshot["events"][-1]["outcome"],
        "verified_count": snapshot["stats"].get("verified_count", 0),
        "foreground_service_remaining": device.foreground_service(),
    }
    (device.evidence / f"{evidence_prefix}-emergency-cleanup.json").write_text(json.dumps(cleanup, indent=2), encoding="utf-8")
    return cleanup


def prepare_permissions(device: Device) -> None:
    device.shell("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
    device.shell("pm", "grant", PKG, "android.permission.CAMERA", check=False)
    device.shell("appops", "set", PKG, "SCHEDULE_EXACT_ALARM", "allow")
    device.shell("appops", "set", PKG, "USE_FULL_SCREEN_INTENT", "allow", check=False)
    (device.evidence / "permissions.txt").write_text(
        device.shell("appops", "get", PKG) + device.shell("dumpsys", "package", PKG),
        encoding="utf-8",
    )


def create_one_time_alarm(device: Device, run_index: int) -> None:
    second = int(device.shell("date", "+%S").strip())
    if second >= 25:
        time.sleep(61 - second)
    home = device.dump_ui(f"run-{run_index:02d}-01-alarm-list")
    device.screenshot(f"run-{run_index:02d}-01-alarm-list")
    device.tap(home, "新增闹钟")
    editor = device.dump_ui(f"run-{run_index:02d}-02-alarm-editor")
    for day in "一二三四五六日":
        device.tap(editor, day)
        editor = device.dump_ui(f"run-{run_index:02d}-02-editor-{day}")
    device.tap(editor, "保存闹钟")
    time.sleep(1)
    device.capture(f"run-{run_index:02d}-03-schedule-alarmmanager", "dumpsys", "alarm")
    schedule = device.dump_ui(f"run-{run_index:02d}-03-scheduled")
    visible = dump_text(schedule)
    if not any("已排程" in item for item in visible):
        raise AssertionError(f"The app did not show a registered exact alarm: {visible}")
    device.screenshot(f"run-{run_index:02d}-03-scheduled")


def exercise_task_removal(device: Device, event_id: str, run_index: int) -> dict:
    device.press(187)  # KEYCODE_APP_SWITCH
    time.sleep(1)
    device.screenshot(f"run-{run_index:02d}-recents-before-removal")
    recents = device.dump_ui(f"run-{run_index:02d}-recents-before-removal")
    card = next((node for node in recents.iter("node") if node.get("content-desc") == "WakeGuard"), None)
    if card is None:
        return {"status": "PARTIAL", "detail": "The launcher did not expose a WakeGuard card in Recents."}
    bounds = list(map(int, re.findall(r"\d+", card.get("bounds") or "")))
    if len(bounds) != 4:
        return {"status": "PARTIAL", "detail": "The WakeGuard Recents card had no usable bounds."}
    left, top, right, bottom = bounds
    center_x = (left + right) // 2
    start_y = max(top + 100, bottom - 200)
    device.shell("input", "swipe", str(center_x), str(start_y), str(center_x), str(max(100, top - 100)), "250")
    time.sleep(2)
    after_recents = device.dump_ui(f"run-{run_index:02d}-recents-after-removal")
    device.screenshot(f"run-{run_index:02d}-recents-after-removal")
    if any(node.get("content-desc") == "WakeGuard" for node in after_recents.iter("node")):
        return {"status": "PARTIAL", "detail": "The launcher kept the task after the upward dismissal gesture."}
    device.press(3)  # Exit overview only after confirming the card was dismissed.
    time.sleep(1)
    if not device.foreground_service():
        raise AssertionError("Removing the Activity task stopped the foreground alarm service.")
    event = device.assert_active_event(event_id)
    if event["outcome"] not in ("RINGING", "VERIFYING"):
        raise AssertionError(f"Task removal changed active event state: {event}")
    device.start_app()
    time.sleep(1)
    screen = device.dump_ui(f"run-{run_index:02d}-after-task-removal")
    require_text(screen, "开始现场验证")
    device.screenshot(f"run-{run_index:02d}-after-task-removal")
    if device.assert_active_event(event_id)["outcome"] not in ("RINGING", "VERIFYING"):
        raise AssertionError("Reopening after task removal did not restore the active event.")
    return {"status": "PASS", "removed_from_recents": True, "event_id": event_id}


def recover_process(device: Device, event_id: str, run_index: int) -> dict:
    before = device.shell("pidof", PKG).strip()
    if not before:
        raise AssertionError("Cannot identify the app process for ordinary process-death testing.")
    device.shell("run-as", PKG, "kill", "-9", before.split()[0])
    deadline = time.monotonic() + 45
    after = ""
    while time.monotonic() < deadline:
        after = device.shell("pidof", PKG, check=False).strip()
        if after and after != before and device.foreground_service():
            break
        time.sleep(1)
    if not after or after == before or not device.foreground_service():
        raise AssertionError(f"START_STICKY recovery did not restart the same process/service: {before} -> {after}")
    event = device.assert_active_event(event_id)
    if event["outcome"] not in ("RINGING", "VERIFYING"):
        raise AssertionError(f"Recovery changed terminal state: {event}")
    device.capture(f"run-{run_index:02d}-sticky-recovery-service", "dumpsys", "activity", "services", PKG)
    device.capture(f"run-{run_index:02d}-sticky-recovery-logcat", "logcat", "-d", "-s", "WakeGuardAlarm:I")
    return {"status": "PASS", "pid_before": before, "pid_after": after, "same_event": event}


def run_positive(device: Device, run_index: int, recover: bool, remove_task: bool) -> dict:
    device.wake()
    device.start_app()
    time.sleep(1)
    entry = device.dump_ui(f"run-{run_index:02d}-entry")
    if "返回闹钟列表" in dump_text(entry):
        device.tap(entry, "返回闹钟列表")
        time.sleep(1)
    create_one_time_alarm(device, run_index)
    before = device.snapshot_room(f"run-{run_index:02d}-before-trigger")
    verified_before = int(before["stats"].get("verified_count", 0))

    device.press(223)  # KEYCODE_SLEEP
    time.sleep(1)
    sleep_dump = device.capture(f"run-{run_index:02d}-04-before-trigger-power", "dumpsys", "power")
    asleep = bool(re.search(r"mWakefulness=Asleep", sleep_dump))
    if not asleep:
        raise AssertionError("KEYCODE_SLEEP did not put the emulator into its verified asleep state.")

    event_id = device.wait_for_service(110)
    event = device.assert_active_event(event_id, "RINGING")
    device.capture(f"run-{run_index:02d}-05-active-service", "dumpsys", "activity", "services", PKG)
    active_notifications = device.notification_dump(f"run-{run_index:02d}-05-active-notification")
    has_alarm_notification = re.search(rf"NotificationRecord\([^\n]*pkg={re.escape(PKG)}\b", active_notifications)
    if not has_alarm_notification:
        raise AssertionError("WakeGuard foreground notification was not present during ringing.")
    device.capture(f"run-{run_index:02d}-05-active-audio", "dumpsys", "audio")
    device.capture(f"run-{run_index:02d}-05-active-wakelocks", "dumpsys", "power")
    device.capture(f"run-{run_index:02d}-05-trigger-logcat", "logcat", "-d", "-s", "WakeGuardAlarm:I")

    device.wake()
    device.start_app()
    time.sleep(1)
    ringing = device.dump_ui(f"run-{run_index:02d}-06-ringing")
    require_text(ringing, "开始现场验证")
    device.screenshot(f"run-{run_index:02d}-06-ringing")
    device.press(4)  # Back is consumed while the active task is ringing.
    time.sleep(0.5)
    after_back = device.dump_ui(f"run-{run_index:02d}-06a-after-back")
    require_text(after_back, "开始现场验证")
    if device.assert_active_event(event_id)["outcome"] != "RINGING":
        raise AssertionError("Back changed the ringing event state.")

    device.press(3)  # Home must leave the service and event active.
    time.sleep(1)
    if not device.foreground_service():
        raise AssertionError("Home stopped the foreground alarm service.")
    if device.assert_active_event(event_id)["outcome"] != "RINGING":
        raise AssertionError("Home changed the ringing event state.")
    device.capture(f"run-{run_index:02d}-07-home-service", "dumpsys", "activity", "services", PKG)
    device.notification_dump(f"run-{run_index:02d}-07-home-notification")
    device.start_app()
    time.sleep(1)
    resumed = device.dump_ui(f"run-{run_index:02d}-08-resumed")
    require_text(resumed, "开始现场验证")
    if device.assert_active_event(event_id)["outcome"] != "RINGING":
        raise AssertionError("Returning from Home did not restore the same ringing event.")

    task_result = exercise_task_removal(device, event_id, run_index) if remove_task else {"status": "NOT_TESTED"}
    recovery_result = recover_process(device, event_id, run_index) if recover else {"status": "NOT_TESTED"}
    device.wake()
    device.start_app()
    time.sleep(1)

    ringing = device.dump_ui(f"run-{run_index:02d}-09-before-camera")
    require_text(ringing, "开始现场验证")
    verify_start = time.monotonic()
    device.tap(ringing, "开始现场验证")
    device.capture(f"run-{run_index:02d}-10-camera-logcat", "logcat", "-d", "-s", "WakeGuardVision:I")
    camera = device.dump_ui(f"run-{run_index:02d}-10-camera")
    device.screenshot(f"run-{run_index:02d}-10-camera")
    camera_labels = dump_text(camera)
    if "拍摄验证" not in camera_labels and "验证完成" not in camera_labels:
        raise AssertionError(f"Neither the live CameraX screen nor its immediate completion appeared: {camera_labels}")

    deadline = time.monotonic() + 35
    while time.monotonic() < deadline and device.foreground_service():
        time.sleep(0.5)
    verification_seconds = time.monotonic() - verify_start
    if device.foreground_service():
        device.capture(f"run-{run_index:02d}-10-no-pass-logcat", "logcat", "-d", "-s", "WakeGuardVision:I", "WakeGuardAlarm:I")
        cleanup = emergency_cleanup(device, f"run-{run_index:02d}-10-no-pass")
        raise AssertionError(f"The selected live camera scene did not complete genuine production verification within 35 seconds; emergency cleanup={cleanup}.")

    result = device.dump_ui(f"run-{run_index:02d}-11-verified-result")
    device.screenshot(f"run-{run_index:02d}-11-verified-result")
    require_text(result, "验证完成")
    after = device.snapshot_room(f"run-{run_index:02d}-after-verified")
    event_rows = [row for row in after["events"] if int(row["id"]) == int(event_id)]
    if len(event_rows) != 1 or event_rows[0]["outcome"] != "VERIFIED":
        raise AssertionError(f"Expected the same single event {event_id} to be VERIFIED: {event_rows}")
    verified_after = int(after["stats"].get("verified_count", 0))
    if verified_after != verified_before + 1:
        raise AssertionError(f"Successful statistics must increase exactly once: {verified_before} -> {verified_after}")

    active_after = device.notification_dump(f"run-{run_index:02d}-12-notification-after")
    alarm_notification_after = bool(re.search(rf"NotificationRecord\([^\n]*pkg={re.escape(PKG)}\b", active_after))
    device.capture(f"run-{run_index:02d}-12-services-after", "dumpsys", "activity", "services", PKG)
    audio_after = device.capture(f"run-{run_index:02d}-12-audio-after", "dumpsys", "audio")
    power_after = device.capture(f"run-{run_index:02d}-12-power-after", "dumpsys", "power")
    logs = device.capture(f"run-{run_index:02d}-12-release-logcat", "logcat", "-d", "-s", "WakeGuardVision:I", "WakeGuardAlarm:I")
    release_log = f"Released playback resources event={event_id}" in logs
    camera_frames = re.findall(r"WakeGuardVision: event camera frame=\d+ latencyMs=\d+", logs)
    app_wakelock_history = [
        line for line in power_after.splitlines()
        if f"{PKG}:wakeguard-alarm" in line and (" - ACQ " in line or " - REL " in line)
    ]
    wake_lock_released = (
        bool(re.search(r"mWakeLockSummary=0x0\b", power_after))
        and bool(re.search(r"Wake Locks: size=0\b", power_after))
        and bool(app_wakelock_history)
        and " - REL " in app_wakelock_history[-1]
    )
    result_data = {
        "status": "PASS",
        "event_id": int(event_id),
        "alarm_id": event["alarmId"],
        "triggered_at_ms": event["triggeredAt"],
        "outcome": event_rows[0]["outcome"],
        "verification_started_at_ms": event_rows[0]["verificationStartedAt"],
        "finished_at_ms": event_rows[0]["finishedAt"],
        "verification_wall_seconds": round(verification_seconds, 2),
        "screen_was_actually_asleep_before_trigger": asleep,
        "active_notification_present": bool(has_alarm_notification),
        "alarm_notification_removed": not alarm_notification_after,
        "notification_dump_after_contains_package": alarm_notification_after,
        "foreground_service_removed": not device.foreground_service(),
        "playback_release_logged": release_log,
        "camera_model_frame_logs": len(camera_frames),
        "wake_lock_name_absent_after": wake_lock_released,
        "verified_count_before": verified_before,
        "verified_count_after": verified_after,
        "task_removal": task_result,
        "process_recovery": recovery_result,
        "audio_after_excerpt": "\n".join(audio_after.splitlines()[-30:]),
    }
    (device.evidence / f"run-{run_index:02d}-acceptance.json").write_text(json.dumps(result_data, indent=2), encoding="utf-8")
    if not release_log or len(camera_frames) < 2 or not wake_lock_released or alarm_notification_after or device.foreground_service():
        raise AssertionError(f"Playback resource cleanup evidence is incomplete: {result_data}")
    device.tap(result, "返回闹钟列表")
    return result_data


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--evidence", required=True, type=Path)
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--restart-process-first", action="store_true")
    parser.add_argument("--remove-task-first", action="store_true")
    args = parser.parse_args()
    if not 1 <= args.repeat <= 5:
        parser.error("--repeat must be in 1..5")
    device = Device(args.serial, args.evidence)
    package_list = device.shell("pm", "list", "packages", PKG)
    if f"package:{PKG}" not in package_list:
        raise RuntimeError("Install the x86_64 debug APK before running the E2E script.")
    prepare_permissions(device)
    results = []
    try:
        for index in range(1, args.repeat + 1):
            results.append(run_positive(
                device,
                index,
                recover=args.restart_process_first and index == 1,
                remove_task=args.remove_task_first and index == 1,
            ))
        summary = {
            "avd": device.text("emu", "avd", "name").strip(),
            "serial": args.serial,
            "repeat": len(results),
            "pass_count": sum(1 for item in results if item["status"] == "PASS"),
            "runs": results,
        }
        (args.evidence / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print(json.dumps(summary, indent=2))
        return 0
    finally:
        logs = device.text("logcat", "-d", "-t", "5000", check=False)
        (args.evidence / "logcat.txt").write_text(logs, encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
