package com.mikuwwl.wakeguard.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}
