#!/usr/bin/env python3
"""Exercise a scheduled unqualified CameraX session, Back/screen-off, and emergency stop."""

import json
import re
import time
from pathlib import Path

from emulator_e2e import (
    Device,
    create_one_time_alarm,
    dump_text,
    emergency_cleanup,
    prepare_permissions,
    require_text,
)


def main() -> int:
    evidence = Path(r"D:\workshop\nov\reports\round3\negative-emergency-power-key")
    device = Device("emulator-5554", evidence)
    package_list = device.shell("pm", "list", "packages", "com.mikuwwl.wakeguard")
    if "package:com.mikuwwl.wakeguard" not in package_list:
        raise RuntimeError("Install the x86_64 debug APK before this scheduled negative test.")
    prepare_permissions(device)
    device.wake()
    device.start_app()
    time.sleep(1)
    entry = device.dump_ui("00-entry")
    if "返回闹钟列表" in dump_text(entry):
        device.tap(entry, "返回闹钟列表")
        time.sleep(1)

    device.shell("logcat", "-c")
    before = device.snapshot_room("01-before")
    verified_before = int(before["stats"].get("verified_count", 0))
    create_one_time_alarm(device, 1)
    device.press(223)  # KEYCODE_SLEEP
    time.sleep(1)
    sleep_before = device.capture("02-screen-off-before-trigger", "dumpsys", "power")
    if "mWakefulness=Asleep" not in sleep_before:
        raise AssertionError("The AVD did not enter the observed asleep state before alarm delivery.")

    event_id = device.wait_for_service(110)
    device.assert_active_event(event_id, "RINGING")
    device.capture("03-ringing-service", "dumpsys", "activity", "services", "com.mikuwwl.wakeguard")
    device.notification_dump("03-ringing-notification")
    device.wake()
    device.start_app()
    time.sleep(1)
    ringing = device.dump_ui("04-ringing-ui")
    require_text(ringing, "开始现场验证")
    device.screenshot("04-ringing-ui")
    if "设置" in dump_text(ringing):
        raise AssertionError("Ordinary settings remained accessible while the alarm event was active.")

    device.press(4)
    time.sleep(0.5)
    after_ring_back = device.dump_ui("05-after-ringing-back")
    require_text(after_ring_back, "开始现场验证")
    if device.assert_active_event(event_id)["outcome"] != "RINGING" or not device.foreground_service():
        raise AssertionError("Back changed or stopped the active ringing event.")

    device.tap(after_ring_back, "开始现场验证")
    deadline = time.monotonic() + 15
    camera = None
    while time.monotonic() < deadline:
        camera = device.dump_ui("06-live-camera")
        if "拍摄验证" in dump_text(camera):
            break
        if not device.foreground_service():
            raise AssertionError("Playback stopped before emergency confirmation.")
        time.sleep(0.5)
    if camera is None or "拍摄验证" not in dump_text(camera):
        raise AssertionError(f"CameraX did not open: {dump_text(camera) if camera is not None else []}")
    device.screenshot("06-live-camera")
    time.sleep(5)
    labels = dump_text(device.dump_ui("07-unqualified-camera-before-sleep"))
    device.screenshot("07-unqualified-camera-before-sleep")
    if "已检测到" in labels or not device.foreground_service():
        raise AssertionError(f"The obviously unrelated camera scene unexpectedly qualified or playback stopped: {labels}")
    verifying = device.assert_active_event(event_id, "VERIFYING")
    log_before_sleep = device.capture("07-camera-model-logcat", "logcat", "-d", "-s", "WakeGuardVision:I", "WakeGuardAlarm:I")
    frame_lines = re.findall(r"WakeGuardVision: event camera frame=\d+ latencyMs=\d+[^\n]*", log_before_sleep)
    if not frame_lines or any(not re.search(r"sink=0(?:\.0+)?\s+toothbrush=0(?:\.0+)?\s+toothpaste=(?:0(?:\.0+)?|[0-9.eE+-]+)", line) for line in frame_lines):
        raise AssertionError(f"Expected actual low-score unrelated CameraX frames; got {frame_lines[-6:]}")

    device.press(26)  # KEYCODE_POWER: exercise the AVD's power-button screen-off path.
    time.sleep(1)
    sleeping_camera = device.capture("08-screen-off-during-camera-power", "dumpsys", "power")
    if "mWakefulness=Asleep" not in sleeping_camera:
        raise AssertionError("Screen-off did not reach the observed asleep state during CameraX verification.")
    if not device.foreground_service() or device.assert_active_event(event_id)["outcome"] != "VERIFYING":
        raise AssertionError("Screen-off during CameraX stopped playback or changed the event outcome.")

    device.wake()
    device.start_app()
    time.sleep(1)
    resumed = device.dump_ui("09-camera-resumed-after-screen-off")
    require_text(resumed, "拍摄验证")
    if "已检测到" in dump_text(resumed):
        raise AssertionError("Camera evidence survived screen-off and resume.")
    if device.assert_active_event(event_id)["outcome"] != "VERIFYING":
        raise AssertionError("The same VERIFYING event was not restored after screen-on.")

    device.press(4)
    time.sleep(0.5)
    back_to_ring = device.dump_ui("10-camera-back-to-ringing")
    require_text(back_to_ring, "开始现场验证")
    if device.assert_active_event(event_id)["outcome"] != "VERIFYING" or not device.foreground_service():
        raise AssertionError("Back from CameraX changed the active event or stopped playback.")
    device.press(4)
    time.sleep(0.5)
    repeated_back = device.dump_ui("11-repeated-back-still-ringing")
    require_text(repeated_back, "开始现场验证")
    if device.assert_active_event(event_id)["outcome"] != "VERIFYING":
        raise AssertionError("Repeated Back changed the active event.")

    device.tap(repeated_back, "开始现场验证")
    deadline = time.monotonic() + 15
    emergency_screen = None
    while time.monotonic() < deadline:
        emergency_screen = device.dump_ui("12-camera-before-emergency")
        if "紧急停止" in dump_text(emergency_screen) and "拍摄验证" in dump_text(emergency_screen):
            break
        time.sleep(0.5)
    if emergency_screen is None or "紧急停止" not in dump_text(emergency_screen):
        raise AssertionError("The camera screen did not expose the explicit emergency stop.")
    device.screenshot("12-camera-before-emergency")
    cleanup = emergency_cleanup(device, "13-emergency-stop")
    if cleanup["event_id"] != int(event_id) or cleanup["outcome"] != "EMERGENCY_STOP":
        raise AssertionError(f"Emergency stop did not terminally record the active event: {cleanup}")
    if int(cleanup["verified_count"]) != verified_before or cleanup["foreground_service_remaining"]:
        raise AssertionError(f"Emergency stop changed success statistics or left playback running: {cleanup}")

    notifications = device.notification_dump("14-notification-after-emergency")
    alarm_notification = bool(re.search(r"NotificationRecord\([^\n]*pkg=com\.mikuwwl\.wakeguard\b", notifications))
    power_after = device.capture("14-power-after-emergency", "dumpsys", "power")
    services_after = device.capture("14-services-after-emergency", "dumpsys", "activity", "services", "com.mikuwwl.wakeguard")
    logs_after = device.capture("14-release-logcat", "logcat", "-d", "-s", "WakeGuardVision:I", "WakeGuardAlarm:I")
    if alarm_notification or "Wake Locks: size=0" not in power_after or "mWakeLockSummary=0x0" not in power_after:
        raise AssertionError("Emergency cleanup left an ongoing notification or active wake lock.")
    if "(nothing)" not in services_after or f"Released playback resources event={event_id}" not in logs_after:
        raise AssertionError("Emergency cleanup left a foreground service or missed its resource-release log.")

    result = {
        "status": "PASS",
        "event_id": int(event_id),
        "outcome": "EMERGENCY_STOP",
        "screen_asleep_before_trigger": True,
        "screen_asleep_during_verification": True,
        "back_kept_event_active": True,
        "camera_permission_granted": True,
        "actual_unrelated_model_frames": frame_lines,
        "unrelated_scene_qualified": False,
        "statistics_unchanged": True,
        "alarm_notification_removed": not alarm_notification,
        "service_removed": "(nothing)" in services_after,
        "wake_locks_released": "Wake Locks: size=0" in power_after,
        "playback_release_logged": f"Released playback resources event={event_id}" in logs_after,
        "camera_verification_started_at_ms": verifying["verificationStartedAt"],
    }
    (evidence / "acceptance.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception:
        try:
            safety_device = Device("emulator-5554", Path(r"D:\workshop\nov\reports\round3\negative-emergency-power-key"))
            active = safety_device.query(
                "SELECT id FROM wake_events WHERE outcome IN ('RINGING','VERIFYING') ORDER BY id DESC LIMIT 1;"
            )
            if active:
                emergency_cleanup(safety_device, "99-automatic-emergency-cleanup")
        except Exception as cleanup_error:
            print(f"Automatic emergency cleanup could not complete: {cleanup_error}", flush=True)
        raise
