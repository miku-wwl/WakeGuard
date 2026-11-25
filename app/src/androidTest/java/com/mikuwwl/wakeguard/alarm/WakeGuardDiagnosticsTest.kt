package com.mikuwwl.wakeguard.alarm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WakeGuardDiagnosticsTest {
    @Test fun lifecycleLogIsLocalParseableAndBounded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "round4-diagnostics-test.jsonl")
        file.delete()
        val log = BoundedDiagnosticLog(file)
        repeat(450) { index ->
            log.append(
                DiagnosticRecord(
                    timestamp = 1_000L + index,
                    event = if (index == 449) "TASK_REMOVED" else "SERVICE_STARTED",
                    eventId = 27L,
                    alarmId = 13L,
                    pid = 99,
                    reason = "test:$index",
                ),
            )
        }
        val records = log.readLast(500)
        assertEquals(400, records.size)
        assertEquals("TASK_REMOVED", records.first().event)
        assertEquals(1_449L, records.first().timestamp)
        assertEquals(27L, records.first().eventId)
        assertEquals(13L, records.first().alarmId)
        assertTrue(file.length() < 256 * 1024)
        file.delete()
    }

    @Test fun userInitiatedExportUsesScopedShareUriAndDoesNotTouchAlarmService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val active = com.mikuwwl.wakeguard.data.WakeEventEntity(
            id = 71,
            alarmId = 13,
            triggeredAt = 1_700_000_000_000,
            outcome = com.mikuwwl.wakeguard.data.WakeOutcome.RINGING,
        )
        val serviceBefore = AlarmForegroundService.isScheduledOrRunning(context)
        val file = WakeGuardDiagnosticExporter.export(context, active, active, exportedAt = 1_700_000_000_500)

        try {
            assertTrue(file.canonicalPath.startsWith(File(context.cacheDir, "diagnostics").canonicalPath))
            val json = file.readText(Charsets.UTF_8)
            assertTrue(json.contains("\"event_id\": 71"))
            assertTrue(json.contains("\"outcome\": \"RINGING\""))

            val provider = context.packageManager.getProviderInfo(
                ComponentName(context, WakeGuardFileProvider::class.java),
                PackageManager.GET_META_DATA,
            )
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)

            val chooser = WakeGuardDiagnosticExporter.createShareChooser(context, file)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val sendIntent = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            assertEquals(Intent.ACTION_SEND, sendIntent.action)
            assertEquals("application/json", sendIntent.type)
            assertTrue(sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = requireNotNull(IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals("content", uri.scheme)
            val sharedText = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertEquals(json, sharedText)
            assertEquals(serviceBefore, AlarmForegroundService.isScheduledOrRunning(context))
        } finally {
            file.delete()
        }
    }
}
