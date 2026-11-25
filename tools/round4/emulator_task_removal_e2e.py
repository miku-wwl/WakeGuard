#!/usr/bin/env python3
"""API 36 emulator-only task-removal regression for Round 4."""

import argparse
import json
import re
import sys
import time
from pathlib import Path

ROUND3_TOOLS = Path(__file__).resolve().parents[1] / "round3"
sys.path.insert(0, str(ROUND3_TOOLS))
from emulator_e2e import (  # noqa: E402
    Device,
    create_one_time_alarm,
    dump_text,
    emergency_cleanup,
    prepare_permissions,
    require_text,
)

PKG = "com.mikuwwl.wakeguard"


def swipe_task(device: Device, prefix: str) -> bool:
    device.press(187)  # KEYCODE_APP_SWITCH
    time.sleep(0.8)
    before = device.dump_ui(f"{prefix}-recents-before")
    device.screenshot(f"{prefix}-recents-before")
    card = next((node for node in before.iter("node") if node.get("content-desc") == "WakeGuard"), None)
    if card is None:
        return False
    bounds = list(map(int, re.findall(r"\d+", card.get("bounds") or "")))
    if len(bounds) != 4:
        return False
    left, top, right, bottom = bounds
    x = (left + right) // 2
    device.shell("input", "swipe", str(x), str(max(top + 100, bottom - 200)), str(x), str(max(100, top - 100)), "250")
    time.sleep(1.5)
    after = device.dump_ui(f"{prefix}-recents-after")
    device.screenshot(f"{prefix}-recents-after")
    if any(node.get("content-desc") == "WakeGuard" for node in after.iter("node")):
        return False
    device.press(3)  # KEYCODE_HOME; leave Recents only after the card is gone.
    time.sleep(0.8)
    return True


def diagnostics(device: Device, prefix: str) -> list[dict]:
    raw = device.shell("run-as", PKG, "cat", "files/wakeguard-diagnostics.jsonl", check=False)
    path = device.evidence / f"{prefix}-wakeguard-diagnostics.jsonl"
    path.write_text(raw, encoding="utf-8")
    records = []
    for line in raw.splitlines():
        try:
            records.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return records


def schedule(device: Device, run_index: int, prefix: str) -> int:
    current = device.dump_ui(f"{prefix}-pre-schedule")
    if "返回闹钟列表" in dump_text(current):
        device.tap(current, "返回闹钟列表")
        time.sleep(0.5)
    create_one_time_alarm(device, run_index)
    rows = device.query("SELECT id, scheduledAt, deliveryToken FROM alarms WHERE enabled=1 ORDER BY id DESC LIMIT 1;")
    if not rows or not rows[0].get("deliveryToken"):
        raise AssertionError("No newly registered one-time alarm with a delivery token was found.")
    (device.evidence / f"{prefix}-scheduled-alarm.json").write_text(json.dumps(rows[0], indent=2), encoding="utf-8")
    device.press(3)
    time.sleep(0.5)
    return int(rows[0]["id"])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--evidence", required=True, type=Path)
    args = parser.parse_args()
    device = Device(args.serial, args.evidence)  # refuses physical devices and unexpected AVDs
    if f"package:{PKG}" not in device.shell("pm", "list", "packages", PKG):
        raise RuntimeError("Install the Round 4 x86_64 debug APK before running this emulator-only scenario.")
    prepare_permissions(device)
    device.start_app()
    time.sleep(1)
    if device.query("SELECT id FROM wake_events WHERE outcome IN ('RINGING','VERIFYING');"):
        raise RuntimeError("An active alarm already exists on the AVD; emergency-stop it before starting Round 4 E2E.")

    result = {"avd": device.text("emu", "avd", "name").strip(), "physical_device": False, "cases": []}
    active_event_id = None
    try:
        # TC01: remove the app task while the exact alarm is still in the future.
        alarm_id = schedule(device, 41, "tc01-future")
        removed_before = swipe_task(device, "tc01-future")
        device.capture("tc01-after-task-removal-alarm-manager", "dumpsys", "alarm")
        event_id = device.wait_for_service(110)
        active_event_id = event_id
        event = device.assert_active_event(event_id, "RINGING")
        if int(event["alarmId"]) != alarm_id:
            raise AssertionError(f"The delivered event belongs to alarm {event['alarmId']}, expected {alarm_id}.")
        time.sleep(1.5)
        trigger_logs = device.capture("tc01-delivery-logcat", "logcat", "-d", "-s", "WakeGuardAlarm:I", "WakeGuardDiagnostics:I")
        if f"Playback active event={event_id}" not in trigger_logs:
            raise AssertionError("The scheduled alarm event was created but actual MediaPlayer playback was not logged.")
        if not device.foreground_service():
            raise AssertionError("The scheduled event did not retain a foreground service after pre-delivery task removal.")
        device.start_app()
        time.sleep(0.8)
        active_ui = device.dump_ui("tc01-active-alarm-after-delivery")
        require_text(active_ui, "开始现场验证")
        tc01_records = diagnostics(device, "tc01")
        result["cases"].append({
            "id": "TC01",
            "status": "PASS" if removed_before else "PARTIAL",
            "task_removed_before_delivery": removed_before,
            "alarm_id": alarm_id,
            "event_id": int(event_id),
            "outcome": event["outcome"],
            "playback_started": True,
            "task_removed_callback_logged": any(r.get("event") == "TASK_REMOVED" for r in tc01_records),
        })
        (args.evidence / "acceptance-progress.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        cleanup = emergency_cleanup(device, "tc01-emergency-cleanup")
        if cleanup["outcome"] != "EMERGENCY_STOP" or cleanup["event_id"] != int(event_id):
            raise AssertionError(f"TC01 emergency cleanup did not close the same event: {cleanup}")
        active_event_id = None

        # TC02/03: swipe the Recents task three times during the same active alarm.
        alarm_id = schedule(device, 42, "tc02-active")
        event_id = device.wait_for_service(110)
        active_event_id = event_id
        event = device.assert_active_event(event_id, "RINGING")
        if int(event["alarmId"]) != alarm_id:
            raise AssertionError(f"The active event belongs to alarm {event['alarmId']}, expected {alarm_id}.")
        time.sleep(1.5)
        active_logs = device.capture("tc02-playback-logcat", "logcat", "-d", "-s", "WakeGuardAlarm:I")
        if f"Playback active event={event_id}" not in active_logs:
            raise AssertionError("Active alarm had no MediaPlayer start evidence before task removal.")

        removals = []
        for index in range(1, 4):
            removed = swipe_task(device, f"tc02-active-swipe-{index}")
            time.sleep(1)
            same_event = device.assert_active_event(event_id, "RINGING")
            service_running = device.foreground_service()
            device.capture(f"tc02-active-swipe-{index}-service", "dumpsys", "activity", "services", PKG)
            device.capture(f"tc02-active-swipe-{index}-audio", "dumpsys", "audio")
            if not removed or not service_running or int(same_event["id"]) != int(event_id):
                raise AssertionError(f"Task swipe {index} stopped or changed active playback state.")
            device.start_app()
            time.sleep(0.7)
            ui = device.dump_ui(f"tc02-active-swipe-{index}-reopened")
            require_text(ui, "开始现场验证")
            removals.append({"attempt": index, "task_removed": removed, "same_event_id": int(same_event["id"]), "service_running": service_running})

        tc02_records = diagnostics(device, "tc02")
        callback_count = sum(1 for r in tc02_records if r.get("event") == "TASK_REMOVED" and r.get("eventId") == int(event_id))
        result["cases"].append({
            "id": "TC02_TC03",
            "status": "PASS",
            "alarm_id": alarm_id,
            "event_id": int(event_id),
            "removals": removals,
            "task_removed_callback_count": callback_count,
            "same_event_retained": True,
            "outcome_after_removals": device.assert_active_event(event_id)["outcome"],
        })
        (args.evidence / "acceptance-progress.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        cleanup = emergency_cleanup(device, "tc02-emergency-cleanup")
        if cleanup["outcome"] != "EMERGENCY_STOP" or cleanup["event_id"] != int(event_id):
            raise AssertionError(f"Emergency cleanup did not stop the same event: {cleanup}")
        active_event_id = None
        result["emergency_cleanup"] = cleanup
        result["status"] = "PASS" if all(c["status"] == "PASS" for c in result["cases"]) else "PARTIAL"
        (args.evidence / "acceptance.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(json.dumps(result, indent=2))
        return 0 if result["status"] == "PASS" else 2
    finally:
        if active_event_id is not None:
            try:
                device.start_app()
                emergency_cleanup(device, "99-final-safety-cleanup")
            except Exception as cleanup_error:
                print(f"Emergency cleanup could not complete: {cleanup_error}", flush=True)
        logs = device.text("logcat", "-d", "-s", "WakeGuardAlarm:I", "WakeGuardDiagnostics:I", check=False)
        (args.evidence / "wakeguard-logcat.txt").write_text(logs, encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
