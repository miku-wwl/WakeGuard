#!/usr/bin/env python3
"""API 36 emulator-only task-removal regression for Round 4."""

import argparse
import json
import re
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

ROUND3_TOOLS = Path(__file__).resolve().parents[1] / "round3"
sys.path.insert(0, str(ROUND3_TOOLS))
from emulator_e2e import (  # noqa: E402
    Device,
    dump_text,
    emergency_cleanup,
    prepare_permissions,
    require_text,
)

PKG = "com.mikuwwl.wakeguard"


def _bounds(node) -> tuple[int, int, int, int] | None:
    values = list(map(int, re.findall(r"\d+", node.get("bounds") or "")))
    return tuple(values) if len(values) == 4 else None


def _tap_resource(device: Device, xml, resource_id: str) -> None:
    node = next((item for item in xml.iter("node") if item.get("resource-id") == resource_id), None)
    bounds = _bounds(node) if node is not None else None
    if bounds is None:
        raise RuntimeError(f"Could not find UI resource {resource_id!r}; XML was saved.")
    left, top, right, bottom = bounds
    device.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def _set_picker_field(device: Device, xml, resource_id: str, value: int) -> None:
    _tap_resource(device, xml, resource_id)
    device.press(123)  # KEYCODE_MOVE_END
    device.press(67)   # KEYCODE_DEL
    device.press(67)
    device.shell("input", "text", f"{value:02d}")


def ensure_alarm_list(device: Device, prefix: str) -> None:
    device.start_app()
    time.sleep(0.8)
    for attempt in range(4):
        screen = device.dump_ui(f"{prefix}-navigation-{attempt}")
        labels = dump_text(screen)
        if "新增闹钟" in labels or any(node.get("content-desc") == "新增闹钟" for node in screen.iter("node")):
            return
        if "返回闹钟列表" in labels:
            device.tap(screen, "返回闹钟列表")
        elif "保存闹钟" in labels and "新建闹钟" in labels:
            # Discard the pre-existing unsaved editor with Back, then launch a fresh list task.
            device.press(4)
            time.sleep(0.5)
            device.start_app()
        else:
            # The app may currently be at the launcher after the unsaved editor was discarded.
            device.start_app()
        time.sleep(0.5)
    raise RuntimeError("Alarm list did not appear after navigating back from the current screen.")


def create_future_one_time_alarm(device: Device, prefix: str) -> int:
    ensure_alarm_list(device, prefix)
    previous = device.query("SELECT COALESCE(MAX(id), 0) AS max_id FROM alarms;")
    previous_max_id = int(previous[0]["max_id"]) if previous else 0

    device.tap(device.dump_ui(f"{prefix}-alarm-list"), "新增闹钟")
    editor = device.dump_ui(f"{prefix}-alarm-editor")
    device.tap(editor, "选择时间")
    picker = device.dump_ui(f"{prefix}-time-picker-clock")
    mode = next((node.get("content-desc", "") for node in picker.iter("node") if node.get("resource-id") == "android:id/toggle_mode"), "")
    if "text input mode" in mode:
        device.tap(picker, mode)
        picker = device.dump_ui(f"{prefix}-time-picker-text")

    device_now = datetime.strptime(device.shell("date", "+%H%M").strip(), "%H%M")
    target = device_now + timedelta(minutes=3)
    target_hour, target_minute = target.hour, target.minute
    _set_picker_field(device, picker, "android:id/input_hour", target_hour)
    # Refresh the XML after editing the hour so the minute field bounds reflect the active text mode.
    picker = device.dump_ui(f"{prefix}-time-picker-hour-set")
    _set_picker_field(device, picker, "android:id/input_minute", target_minute)
    picker = device.dump_ui(f"{prefix}-time-picker-set")
    values = {
        node.get("resource-id"): node.get("text", "")
        for node in picker.iter("node")
        if node.get("resource-id") in {"android:id/input_hour", "android:id/input_minute"}
    }
    if values.get("android:id/input_hour") != f"{target_hour:02d}" or values.get("android:id/input_minute") != f"{target_minute:02d}":
        raise AssertionError(f"Time picker did not retain target {target_hour:02d}:{target_minute:02d}: {values}")
    _tap_resource(device, picker, "android:id/button1")

    editor = device.dump_ui(f"{prefix}-alarm-editor-time-set")
    # The current UI defaults new alarms to every day. Toggle each day off to make
    # this future-delivery regression genuinely one-time, then verify repeatMask in Room.
    for day in "一二三四五六日":
        device.tap(editor, day)
        editor = device.dump_ui(f"{prefix}-repeat-day-{day}-off")

    device.tap(editor, "保存闹钟")
    time.sleep(0.8)
    deadline = time.monotonic() + 12
    rows = []
    while time.monotonic() < deadline:
        rows = device.query(
            "SELECT id, scheduledAt, deliveryToken, repeatMask FROM alarms "
            f"WHERE enabled=1 AND id>{previous_max_id} ORDER BY id DESC LIMIT 1;"
        )
        if rows and rows[0].get("deliveryToken"):
            break
        time.sleep(0.5)
    if not rows or not rows[0].get("deliveryToken"):
        raise AssertionError(f"No newly persisted alarm with a registered delivery token was found: {rows}")
    row = rows[0]
    if int(row.get("repeatMask", -1)) != 0:
        raise AssertionError(f"Expected a one-time alarm (repeatMask=0), got {row}.")
    now_epoch_ms = int(device.shell("date", "+%s").strip()) * 1000
    if int(row["scheduledAt"]) < now_epoch_ms + 60_000:
        raise AssertionError(f"The alarm is not safely in the future: {row}")
    (device.evidence / f"{prefix}-scheduled-alarm.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
    device.capture(f"{prefix}-schedule-alarmmanager", "dumpsys", "alarm")
    device.press(3)
    time.sleep(0.5)
    return int(row["id"])


def swipe_task(device: Device, prefix: str) -> bool:
    device.press(187)  # KEYCODE_APP_SWITCH
    time.sleep(0.8)
    before = device.dump_ui(f"{prefix}-recents-before")
    device.screenshot(f"{prefix}-recents-before")
    before_labels = dump_text(before)
    if "Screenshot" not in before_labels or "Select" not in before_labels:
        (device.evidence / f"{prefix}-recents-state.json").write_text(
            json.dumps({"recents_surface": False, "labels": before_labels}, indent=2), encoding="utf-8"
        )
        return False
    card = next((node for node in before.iter("node") if node.get("content-desc") == "WakeGuard"), None)
    if card is None:
        return False
    stacks_before = device.shell("am", "stack", "list")
    (device.evidence / f"{prefix}-stacks-before.txt").write_text(stacks_before, encoding="utf-8")
    task_ids_before = re.findall(rf"(?m)\btaskId=(\d+):\s*{re.escape(PKG)}/", stacks_before)
    bounds = list(map(int, re.findall(r"\d+", card.get("bounds") or "")))
    if not task_ids_before or len(bounds) != 4:
        return False
    left, top, right, bottom = bounds
    x = (left + right) // 2
    # Keep the synthetic gesture inside the app card; the post-check also verifies
    # that ActivityManager no longer lists the exact task seen before the swipe.
    start_y = top + int((bottom - top) * 0.78)
    end_y = max(top + 220, 420)
    if start_y <= end_y:
        start_y = min(bottom - 100, end_y + 500)
    device.shell("input", "swipe", str(x), str(start_y), str(x), str(end_y), "180")
    time.sleep(1.5)
    after = device.dump_ui(f"{prefix}-recents-after")
    device.screenshot(f"{prefix}-recents-after")
    after_labels = dump_text(after)
    recents_still_open = "Screenshot" in after_labels and "Select" in after_labels
    card_still_visible = any(node.get("content-desc") == "WakeGuard" for node in after.iter("node"))
    stacks_after = device.shell("am", "stack", "list")
    (device.evidence / f"{prefix}-stacks-after.txt").write_text(stacks_after, encoding="utf-8")
    task_ids_after = re.findall(rf"(?m)\btaskId=(\d+):\s*{re.escape(PKG)}/", stacks_after)
    task_removed = all(task_id not in task_ids_after for task_id in task_ids_before)
    state = {
        "removal_method": "ADB input swipe in Recents",
        "recents_visible_before": True,
        "recents_visible_after": recents_still_open,
        "recents_closed_after_dismissal": not recents_still_open,
        "task_ids_before": task_ids_before,
        "task_ids_after": task_ids_after,
        "task_ids_absent_after": task_removed,
        "card_still_visible_after": card_still_visible,
    }
    (device.evidence / f"{prefix}-task-removal-state.json").write_text(json.dumps(state, indent=2), encoding="utf-8")
    # Android may close Recents after the last task card is dismissed. Task-ID
    # disappearance plus card disappearance is the removal proof; requiring the
    # Recents surface to remain open would reject a successful swipe.
    if not task_removed or card_still_visible:
        return False
    if recents_still_open:
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


def schedule(device: Device, prefix: str) -> int:
    return create_future_one_time_alarm(device, prefix)


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
        alarm_id = schedule(device, "tc01-future")
        removed_before = swipe_task(device, "tc01-future")
        device.capture("tc01-after-task-removal-alarm-manager", "dumpsys", "alarm")
        event_id = device.wait_for_service(260)
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
            "removal_method": "ADB input swipe in Recents",
            "alarm_id": alarm_id,
            "event_id": int(event_id),
            "outcome": event["outcome"],
            "playback_started": True,
            "task_removed_callback_logged": any(
                r.get("event") == "TASK_REMOVED" and r.get("eventId") == int(event_id)
                for r in tc01_records
            ),
        })
        (args.evidence / "acceptance-progress.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        cleanup = emergency_cleanup(device, "tc01-emergency-cleanup")
        if cleanup["outcome"] != "EMERGENCY_STOP" or cleanup["event_id"] != int(event_id):
            raise AssertionError(f"TC01 emergency cleanup did not close the same event: {cleanup}")
        active_event_id = None

        # Exercise the Recents card with an ADB-generated touchscreen swipe.
        alarm_id = schedule(device, "tc02-active")
        event_id = device.wait_for_service(260)
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
            service_dump = device.capture(f"tc02-active-swipe-{index}-service", "dumpsys", "activity", "services", PKG)
            audio_dump = device.capture(f"tc02-active-swipe-{index}-audio", "dumpsys", "audio")
            removal_result = {
                "attempt": index,
                "task_removed": removed,
                "removal_method": "ADB input swipe in Recents",
                "same_event_id": int(same_event["id"]),
                "service_running": service_running,
                "media_player_started": "state:started" in audio_dump and "type:android.media.MediaPlayer" in audio_dump and "com.mikuwwl.wakeguard" in audio_dump,
                "service_dump_has_foreground": "isForeground=true" in service_dump,
            }
            (args.evidence / f"tc02-active-swipe-{index}-state.json").write_text(json.dumps(removal_result, indent=2), encoding="utf-8")
            progress_case = {
                "id": "TC02_TC03",
                "status": "PARTIAL",
                "alarm_id": alarm_id,
                "event_id": int(event_id),
                "removals": removals + [removal_result],
                "same_event_retained": int(same_event["id"]) == int(event_id),
                "outcome_after_removals": same_event["outcome"],
            }
            result["cases"] = [case for case in result["cases"] if case["id"] != "TC02_TC03"] + [progress_case]
            (args.evidence / "acceptance-progress.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
            if not removed or not service_running or int(same_event["id"]) != int(event_id):
                failed = [name for name, ok in (
                    ("task_removed", removed),
                    ("service_running", service_running),
                    ("same_event_id", int(same_event["id"]) == int(event_id)),
                ) if not ok]
                raise AssertionError(f"Task swipe {index} failed checks: {', '.join(failed)}; observed {removal_result}.")
            device.start_app()
            time.sleep(0.7)
            ui = device.dump_ui(f"tc02-active-swipe-{index}-reopened")
            require_text(ui, "开始现场验证")
            removals.append({"attempt": index, "task_removed": removed, "same_event_id": int(same_event["id"]), "service_running": service_running})

        tc02_records = diagnostics(device, "tc02")
        callback_count = sum(1 for r in tc02_records if r.get("event") == "TASK_REMOVED" and r.get("eventId") == int(event_id))
        result["cases"] = [case for case in result["cases"] if case["id"] != "TC02_TC03"]
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
