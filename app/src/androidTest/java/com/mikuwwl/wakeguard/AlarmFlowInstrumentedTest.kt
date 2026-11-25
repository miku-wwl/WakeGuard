package com.mikuwwl.wakeguard

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.data.WakeEventEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.alarm.AlarmForegroundService
import com.mikuwwl.wakeguard.data.WakeOutcome
import com.mikuwwl.wakeguard.alarm.WakeGuardDiagnosticExporter
import com.mikuwwl.wakeguard.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmFlowInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val database by lazy { WakeGuardDatabase.get(ApplicationProvider.getApplicationContext()) }

    @Before fun clearLocalData() {
        runBlocking(Dispatchers.IO) { database.clearAllTables() }
        composeRule.waitForIdle()
    }

    @Test fun alarmPlaybackServiceIsNotStoppedWithItsActivityTask() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, AlarmForegroundService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, serviceInfo.foregroundServiceType)
        assertFalse(serviceInfo.flags and ServiceInfo.FLAG_STOP_WITH_TASK != 0)
    }

    @Test fun userCanCreateAndPersistAnAlarm() = runBlocking {
        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("save_alarm").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("WakeGuard alarm").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("alarm_list").assertIsDisplayed()
        val alarms = database.alarmDao().getEnabled()
        assertEquals(1, alarms.size)
        assertEquals(127, alarms.single().repeatMask)
        composeRule.onNodeWithTag("alarm_enabled").performClick()
        composeRule.waitUntil(10_000) { runBlocking { database.alarmDao().getEnabled().isEmpty() } }
        composeRule.onNodeWithTag("alarm_enabled").performClick()
        composeRule.waitUntil(10_000) { runBlocking { database.alarmDao().getEnabled().size == 1 } }
    }

    @Test fun settingsExposeLiveAlarmReliabilityAndManualOemCheck() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("闹钟可靠性检查").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("前台闹钟服务").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("需用户手动检查").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("export_diagnostics").performScrollTo().assertIsDisplayed()
    }

    @Test fun userCanEditAndDeleteAnAlarm() = runBlocking {
        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("alarm_label").performTextReplacement("Morning test")
        composeRule.onNodeWithTag("save_alarm").performClick()
        // The editor's text field also exposes its current value as text. Wait for
        // the alarm list itself so this cannot pass before save navigates home.
        waitForAlarmCard("Morning test")

        composeRule.onNodeWithTag("edit_alarm").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("alarm_label").performTextReplacement("Edited morning test")
        composeRule.onNodeWithTag("save_alarm").performClick()
        waitForAlarmCard("Edited morning test")
        assertEquals("Edited morning test", database.alarmDao().getEnabled().single().label)

        composeRule.onNodeWithTag("delete_alarm").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("还没有闹钟").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(database.alarmDao().getEnabled().isEmpty())
    }

    private fun waitForAlarmCard(label: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("alarm_list").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun activeAlarmCanEnterLiveCameraVerification() {
        runBlocking {
            val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(appContext.packageName, Manifest.permission.CAMERA)
            database.wakeEventDao().insert(
                WakeEventEntity(alarmId = 103, triggeredAt = System.currentTimeMillis(), outcome = WakeOutcome.RINGING),
            )
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("开始现场验证").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("start_verification").performClick()
            composeRule.waitUntil(30_000) {
                listOf("vision_sink_status", "vision_dental_status").all { tag ->
                    composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
                }
            }
            composeRule.onNodeWithTag("camera_preview").assertIsDisplayed()
        }
    }

    @Test fun backReturnsFromCameraButCannotDismissTheActiveAlarm() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(appContext.packageName, Manifest.permission.CAMERA)
        val eventId = database.wakeEventDao().insert(
            WakeEventEntity(alarmId = 104, triggeredAt = System.currentTimeMillis(), outcome = WakeOutcome.RINGING),
        )
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("开始现场验证").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithTag("export_diagnostics").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("start_verification").performClick()
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithTag("camera_preview").fetchSemanticsNodes().isNotEmpty()
        }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("开始现场验证").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("紧急停止（记录为未完成）").assertIsDisplayed()
        assertEquals(WakeOutcome.VERIFYING, database.wakeEventDao().getById(eventId)?.outcome)

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("开始现场验证").assertIsDisplayed()
        assertEquals(WakeOutcome.VERIFYING, database.wakeEventDao().getById(eventId)?.outcome)
        assertEquals(eventId, database.wakeEventDao().getActive()?.id)
    }

    @Test fun verificationAndEmergencyOutcomesAreIdempotent() = runBlocking {
        val dao = database.wakeEventDao()
        val verifiedId = dao.insert(WakeEventEntity(alarmId = 101, triggeredAt = 1_000, outcome = WakeOutcome.RINGING))
        assertEquals(0, dao.finishVerified(verifiedId, 1_500))
        assertEquals(1, dao.startVerification(verifiedId, 2_000))
        assertEquals(1, dao.finishVerified(verifiedId, 3_000))
        assertEquals(0, dao.finishVerified(verifiedId, 4_000))
        assertEquals(WakeOutcome.VERIFIED, dao.getById(verifiedId)?.outcome)

        val emergencyId = dao.insert(WakeEventEntity(alarmId = 102, triggeredAt = 5_000, outcome = WakeOutcome.RINGING))
        assertEquals(1, dao.finishEmergency(emergencyId, 6_000))
        assertEquals(0, dao.finishVerified(emergencyId, 7_000))
        assertEquals(WakeOutcome.EMERGENCY_STOP, dao.getById(emergencyId)?.outcome)
    }

    @Test fun userCanResumeTheSamePersistedEventAndEmergencyStopIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val eventId = database.wakeEventDao().insert(
            WakeEventEntity(alarmId = 105, triggeredAt = System.currentTimeMillis(), outcome = WakeOutcome.RINGING),
        )
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("resume_alarm_service").fetchSemanticsNodes().isNotEmpty()
        }
        val activeBeforeExport = database.wakeEventDao().getById(eventId)!!
        val serviceBeforeExport = AlarmForegroundService.isScheduledOrRunning(context)
        val exportFile = withContext(Dispatchers.IO) {
            WakeGuardDiagnosticExporter.export(context, activeBeforeExport, activeBeforeExport)
        }
        try {
            assertEquals(WakeOutcome.RINGING, database.wakeEventDao().getById(eventId)?.outcome)
            assertEquals(eventId, database.wakeEventDao().getActive()?.id)
            assertEquals(serviceBeforeExport, AlarmForegroundService.isScheduledOrRunning(context))
            assertTrue(exportFile.exists())
        } finally {
            exportFile.delete()
        }

        composeRule.onNodeWithTag("resume_alarm_service").assertIsDisplayed().performClick()
        composeRule.waitUntil(10_000) {
            com.mikuwwl.wakeguard.alarm.AlarmForegroundService.isScheduledOrRunning(context)
        }
        assertEquals(eventId, database.wakeEventDao().getActive()?.id)
        assertEquals(WakeOutcome.RINGING, database.wakeEventDao().getById(eventId)?.outcome)

        composeRule.onNodeWithText("紧急停止（记录为未完成）").performClick()
        composeRule.onNodeWithText("紧急停止").performClick()
        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                database.wakeEventDao().getById(eventId)?.outcome == WakeOutcome.EMERGENCY_STOP
            }
        }
        assertTrue(database.wakeEventDao().getActive() == null)
        assertFalse(com.mikuwwl.wakeguard.data.WakeGuardRepository(context).resumeActiveAlarm(eventId))
    }

}
