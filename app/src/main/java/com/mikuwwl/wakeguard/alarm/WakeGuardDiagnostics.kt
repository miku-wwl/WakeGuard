package com.mikuwwl.wakeguard.alarm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.io.File

internal data class DiagnosticRecord(
    val timestamp: Long,
    val event: String,
    val eventId: Long? = null,
    val alarmId: Long? = null,
    val pid: Int,
    val reason: String? = null,
)

/** A small, local JSONL ring buffer. It contains WakeGuard lifecycle metadata only. */
internal class BoundedDiagnosticLog(private val file: File) {
    fun append(record: DiagnosticRecord) {
        synchronized(FILE_LOCK) {
            file.parentFile?.mkdirs()
            val line = JSONObject().apply {
                put("timestamp", record.timestamp)
                put("event", record.event.take(48))
                put("eventId", record.eventId ?: JSONObject.NULL)
                put("alarmId", record.alarmId ?: JSONObject.NULL)
                put("pid", record.pid)
                put("reason", record.reason?.take(MAX_REASON_CHARS) ?: JSONObject.NULL)
            }.toString()
            file.appendText("$line\n", Charsets.UTF_8)
            val lines = file.readLines(Charsets.UTF_8).filter(String::isNotBlank)
            if (lines.size > MAX_ENTRIES || file.length() > MAX_BYTES) {
                var kept = lines.takeLast(MAX_ENTRIES)
                while (kept.size > 1 && kept.sumOf { it.toByteArray(Charsets.UTF_8).size + 1L } > MAX_BYTES) {
                    kept = kept.drop(1)
                }
                file.writeText(kept.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
            }
        }
    }

    fun readLast(limit: Int): List<DiagnosticRecord> = synchronized(FILE_LOCK) {
        runCatching {
            if (!file.isFile) emptyList()
            else file.readLines(Charsets.UTF_8).asReversed().asSequence().filter(String::isNotBlank).take(limit)
                .mapNotNull { line ->
                    runCatching {
                        val json = JSONObject(line)
                        DiagnosticRecord(
                            timestamp = json.getLong("timestamp"),
                            event = json.getString("event"),
                            eventId = json.optLongOrNull("eventId"),
                            alarmId = json.optLongOrNull("alarmId"),
                            pid = json.getInt("pid"),
                            reason = json.optString("reason").takeUnless { it == "null" || it.isBlank() },
                        )
                    }.getOrNull()
                }.toList()
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key).takeIf { it != 0L }

    private companion object {
        val FILE_LOCK = Any()
        const val MAX_ENTRIES = 400
        const val MAX_BYTES = 256 * 1024L
        const val MAX_REASON_CHARS = 160
    }
}

internal object WakeGuardDiagnostics {
    private const val FILE_NAME = "wakeguard-diagnostics.jsonl"
    private const val TAG = "WakeGuardDiagnostics"

    fun record(
        context: Context,
        event: String,
        eventId: Long? = null,
        alarmId: Long? = null,
        reason: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        pid: Int = Process.myPid(),
    ) {
        runCatching {
            log(context).append(DiagnosticRecord(timestamp, event, eventId, alarmId, pid, reason))
        }.onFailure { Log.w(TAG, "Could not append local lifecycle diagnostic", it) }
    }

    fun latestProcessExitSummary(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "Android 11 以下不提供进程退出历史"
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
                ?: return "暂无系统进程退出记录"
            val time = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(exit.timestamp))
            val suffix = if (exit.reason == android.app.ApplicationExitInfo.REASON_USER_REQUESTED) {
                "；此原因可能表示最近任务移除或系统设置中的强行停止"
            } else ""
            "${exitReasonName(exit.reason)} · $time$suffix"
        }.getOrDefault("系统退出历史不可读取")
    }

    fun recordLatestProcessExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
                ?: return
            val alreadyRecorded = log(context).readLast(400).any {
                it.event == "PROCESS_EXIT_INFO" && it.timestamp == exit.timestamp
            }
            if (alreadyRecorded) return
            val description = exit.description?.take(96)?.replace('\n', ' ')
            record(
                context = context,
                event = "PROCESS_EXIT_INFO",
                reason = "${exitReasonName(exit.reason)}; status=${exit.status}; importance=${exit.importance}; description=${description.orEmpty()}",
                timestamp = exit.timestamp,
                pid = exit.pid,
            )
        }.onFailure { Log.w(TAG, "Could not inspect previous process exit", it) }
    }

    private fun log(context: Context) = BoundedDiagnosticLog(File(context.applicationContext.filesDir, FILE_NAME))

    private fun exitReasonName(reason: Int): String = when (reason) {
        android.app.ApplicationExitInfo.REASON_ANR -> "ANR"
        android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        android.app.ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        android.app.ApplicationExitInfo.REASON_OTHER -> "OTHER"
        android.app.ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        else -> "UNKNOWN($reason)"
    }
}
