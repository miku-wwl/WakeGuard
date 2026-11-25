package com.mikuwwl.wakeguard.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportFormatterTest {
    @Test fun exportIncludesReliabilityEventsAndRedactsFreeFormExitText() {
        val snapshot = DiagnosticExportSnapshot(
            exportedAt = 1_700_000_000_000,
            appVersionName = "0.1.0-alpha.4-rc.1",
            appVersionCode = 4,
            manufacturer = "iQOO",
            model = "Neo9 Pro",
            androidVersion = "16",
            apiLevel = 36,
            publicStates = linkedMapOf("exact_alarm" to "allowed", "camera_permission" to "not_allowed"),
            activeEvent = null,
            latestEvent = DiagnosticWakeEvent(42, 13, "EMERGENCY_STOP", 1_699_999_900_000, 1_699_999_910_000),
            foregroundServiceRunning = false,
            processExits = listOf(
                DiagnosticProcessExit(1_699_999_800_000, "CRASH", 9, 100, 456, descriptionAvailable = true),
            ),
            lifecycleEvents = listOf(
                DiagnosticRecord(
                    timestamp = 1_699_999_800_000,
                    event = "PROCESS_EXIT_INFO",
                    eventId = 42,
                    alarmId = 13,
                    pid = 456,
                    reason = "CRASH; status=9; importance=100; description=/data/user/0/private user@example.com serial=SERIAL-PRIVATE android_id=ANDROID-PRIVATE\ncontinued",
                ),
                DiagnosticRecord(1_699_999_700_000, "TASK_REMOVED", 42, 13, 455, "activity_task_removed_playback_retained"),
            ),
        )

        val json = DiagnosticExportFormatter.format(snapshot)

        assertTrue(json.contains("\"version_name\": \"0.1.0-alpha.4-rc.1\""))
        assertTrue(json.contains("\"event_id\": 42"))
        assertTrue(json.contains("\"outcome\": \"EMERGENCY_STOP\""))
        assertTrue(json.contains("\"foreground_service_running\": false"))
        assertTrue(json.contains("\"description_available_but_redacted\": true"))
        assertTrue(json.contains("description=[redacted]"))
        assertTrue(json.contains("TASK_REMOVED"))
        assertFalse(json.contains("/data/user/0/private"))
        assertFalse(json.contains("user@example.com"))
        assertFalse(json.contains("SERIAL-PRIVATE"))
        assertFalse(json.contains("ANDROID-PRIVATE"))
        assertFalse(json.contains("continued"))
    }
}
