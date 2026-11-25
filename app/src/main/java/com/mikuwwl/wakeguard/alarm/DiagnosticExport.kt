package com.mikuwwl.wakeguard.alarm

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.FileProvider
import com.mikuwwl.wakeguard.data.WakeEventEntity
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class DiagnosticWakeEvent(
    val id: Long,
    val alarmId: Long,
    val outcome: String,
    val triggeredAt: Long,
    val finishedAt: Long?,
) {
    companion object {
        fun from(event: WakeEventEntity?) = event?.let {
            DiagnosticWakeEvent(it.id, it.alarmId, it.outcome, it.triggeredAt, it.finishedAt)
        }
    }
}

internal data class DiagnosticProcessExit(
    val timestamp: Long,
    val reason: String,
    val status: Int,
    val importance: Int,
    val pid: Int,
    val descriptionAvailable: Boolean,
)

internal data class DiagnosticExportSnapshot(
    val exportedAt: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val publicStates: Map<String, String>,
    val activeEvent: DiagnosticWakeEvent?,
    val latestEvent: DiagnosticWakeEvent?,
    val foregroundServiceRunning: Boolean?,
    val processExits: List<DiagnosticProcessExit>,
    val lifecycleEvents: List<DiagnosticRecord>,
)

internal object DiagnosticExportFormatter {
    fun format(snapshot: DiagnosticExportSnapshot): String = jsonObject(
        listOf(
            "schema_version" to "1",
            "exported_at_utc" to quote(Instant.ofEpochMilli(snapshot.exportedAt).toString()),
            "app" to jsonObject(
                listOf(
                    "version_name" to quote(snapshot.appVersionName),
                    "version_code" to snapshot.appVersionCode.toString(),
                ),
                1,
            ),
            "device" to jsonObject(
                listOf(
                    "manufacturer" to quote(snapshot.manufacturer),
                    "model" to quote(snapshot.model),
                    "android_version" to quote(snapshot.androidVersion),
                    "api_level" to snapshot.apiLevel.toString(),
                ),
                1,
            ),
            "public_android_states" to jsonObject(snapshot.publicStates.map { it.key to quote(it.value) }, 1),
            "originos_proprietary_settings" to jsonObject(
                listOf(
                    "status" to quote("manual_inspection_required"),
                    "items" to jsonArray(
                        listOf("autostart", "background_high_power", "recents_card_lock").map(::quote),
                        2,
                    ),
                ),
                1,
            ),
            "active_wake_event" to formatWakeEvent(snapshot.activeEvent, 1),
            "most_recent_wake_event" to formatWakeEvent(snapshot.latestEvent, 1),
            "foreground_service_running" to (snapshot.foregroundServiceRunning?.toString() ?: "null"),
            "application_exit_info" to jsonArray(snapshot.processExits.map { exit ->
                jsonObject(
                    listOf(
                        "timestamp" to exit.timestamp.toString(),
                        "reason" to quote(exit.reason),
                        "status" to exit.status.toString(),
                        "importance" to exit.importance.toString(),
                        "pid" to exit.pid.toString(),
                        // Android's free-form description is deliberately omitted from exports.
                        "description_available_but_redacted" to exit.descriptionAvailable.toString(),
                    ),
                    2,
                )
            }, 1),
            "recent_lifecycle_events" to jsonArray(snapshot.lifecycleEvents.sortedBy { it.timestamp }.map { record ->
                jsonObject(
                    listOf(
                        "timestamp" to record.timestamp.toString(),
                        "event" to quote(record.event.take(48)),
                        "event_id" to (record.eventId?.toString() ?: "null"),
                        "alarm_id" to (record.alarmId?.toString() ?: "null"),
                        "pid" to record.pid.toString(),
                        "reason" to (sanitizeReason(record.event, record.reason)?.let(::quote) ?: "null"),
                    ),
                    2,
                )
            }, 1),
            "privacy_note" to quote("No serial number, IMEI, Android ID, account, location, photo, camera frame, or unrelated app data is collected. OriginOS-only switches require manual inspection."),
        ),
        0,
    ) + "\n"

    /** Drops free-form exit descriptions and strips path-like or identifying text from log reasons. */
    fun sanitizeReason(event: String, reason: String?): String? {
        if (reason.isNullOrBlank()) return null
        var safe = reason.replace(Regex("[\\r\\n\\t\\u0000-\\u001f]"), " ").take(320)
        if (event == "PROCESS_EXIT_INFO") {
            val descriptionStart = safe.indexOf("; description=", ignoreCase = true)
            if (descriptionStart >= 0) safe = safe.substring(0, descriptionStart) + "; description=[redacted]"
        }
        safe = safe.replace(
            Regex("(?i)\\b(serial(?:number)?|imei|android[ _-]*id)\\s*[:=]\\s*[^,; ]+"),
            "$1=[redacted]",
        )
        safe = safe.replace(Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), "[redacted-email]")
        safe = safe.replace(Regex("(?:[A-Za-z]:[\\\\/][^\\s,;]+|/(?:[^/\\s,;]+/)+[^/\\s,;]*)"), "[redacted-path]")
        return safe.take(320)
    }

    private fun formatWakeEvent(event: DiagnosticWakeEvent?, indent: Int): String = event?.let {
        jsonObject(
            listOf(
                "event_id" to it.id.toString(),
                "alarm_id" to it.alarmId.toString(),
                "outcome" to quote(it.outcome),
                "triggered_at" to it.triggeredAt.toString(),
                "finished_at" to (it.finishedAt?.toString() ?: "null"),
            ),
            indent,
        )
    } ?: "null"

    private fun jsonObject(fields: List<Pair<String, String>>, indent: Int): String {
        if (fields.isEmpty()) return "{}"
        val pad = "  ".repeat(indent)
        val childPad = "  ".repeat(indent + 1)
        return fields.joinToString(",\n", prefix = "{\n", postfix = "\n$pad}") { (key, value) ->
            "$childPad${quote(key)}: $value"
        }
    }

    private fun jsonArray(items: List<String>, indent: Int): String {
        if (items.isEmpty()) return "[]"
        val pad = "  ".repeat(indent)
        val childPad = "  ".repeat(indent + 1)
        return items.joinToString(",\n", prefix = "[\n", postfix = "\n$pad]") { "$childPad$it" }
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}

internal object WakeGuardDiagnosticExporter {
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun export(
        context: Context,
        activeEvent: WakeEventEntity?,
        latestEvent: WakeEventEntity?,
        exportedAt: Long = System.currentTimeMillis(),
    ): File {
        val appInfo = runCatching { packageInfo(context) }.getOrNull()
        val snapshot = DiagnosticExportSnapshot(
            exportedAt = exportedAt,
            appVersionName = appInfo?.versionName?.take(80) ?: "unknown",
            appVersionCode = appInfo?.longVersionCode ?: -1L,
            manufacturer = Build.MANUFACTURER.take(80),
            model = Build.MODEL.take(120),
            androidVersion = Build.VERSION.RELEASE.take(40),
            apiLevel = Build.VERSION.SDK_INT,
            publicStates = publicAndroidStates(context),
            activeEvent = DiagnosticWakeEvent.from(activeEvent),
            latestEvent = DiagnosticWakeEvent.from(latestEvent),
            foregroundServiceRunning = runCatching { AlarmForegroundService.isScheduledOrRunning(context) }.getOrNull(),
            processExits = processExitMetadata(context),
            lifecycleEvents = WakeGuardDiagnostics.readRecent(context, 400),
        )
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, "wakeguard-diagnostics-${fileTimestamp.format(Instant.ofEpochMilli(exportedAt))}-${UUID.randomUUID().toString().take(8)}.json")
        file.writeText(DiagnosticExportFormatter.format(snapshot), Charsets.UTF_8)
        return file
    }

    fun createShareChooser(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "分享 WakeGuard 诊断日志").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun publicAndroidStates(context: Context): Map<String, String> {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        return linkedMapOf(
            "exact_alarm" to safeState("unavailable") {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "not_required"
                else if (alarmManager.canScheduleExactAlarms()) "allowed" else "not_allowed"
            },
            "notifications" to safeState("unavailable") {
                val runtimeAllowed = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (runtimeAllowed && notificationManager.areNotificationsEnabled()) "allowed" else "not_allowed"
            },
            "full_screen_intent" to safeState("unavailable") {
                if (Build.VERSION.SDK_INT < 34) "not_required"
                else if (notificationManager.canUseFullScreenIntent()) "allowed" else "not_allowed"
            },
            "camera_permission" to safeState("unavailable") {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) "allowed" else "not_allowed"
            },
            "battery_optimization" to safeState("unavailable") {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) "not_available"
                else if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) "exempted" else "not_exempted_or_restricted"
            },
            "background_restriction" to safeState("unavailable") {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) "not_available"
                else if (activityManager.isBackgroundRestricted) "restricted" else "not_detected"
            },
        )
    }

    @Suppress("NewApi")
    private fun processExitMetadata(context: Context): List<DiagnosticProcessExit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 10)
                .map { exit ->
                    DiagnosticProcessExit(
                        timestamp = exit.timestamp,
                        reason = WakeGuardDiagnostics.processExitReasonName(exit.reason),
                        status = exit.status,
                        importance = exit.importance,
                        pid = exit.pid,
                        descriptionAvailable = !exit.description.isNullOrBlank(),
                    )
                }
        }.getOrDefault(emptyList())
    }

    private inline fun safeState(fallback: String, read: () -> String): String = runCatching(read).getOrDefault(fallback)
}
