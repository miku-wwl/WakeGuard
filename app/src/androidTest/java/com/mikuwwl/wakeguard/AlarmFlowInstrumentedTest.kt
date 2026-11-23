package com.mikuwwl.wakeguard

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.data.WakeEventEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.data.WakeOutcome
import com.mikuwwl.wakeguard.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
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
    }

    @Test fun userCanEditAndDeleteAnAlarm() = runBlocking {
        composeRule.onNodeWithTag("add_alarm").performClick()
        composeRule.onNodeWithTag("alarm_label").performTextReplacement("Morning test")
        composeRule.onNodeWithTag("save_alarm").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Morning test").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("edit_alarm").performClick()
        composeRule.onNodeWithTag("alarm_label").performTextReplacement("Edited morning test")
        composeRule.onNodeWithTag("save_alarm").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Edited morning test").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("Edited morning test", database.alarmDao().getEnabled().single().label)

        composeRule.onNodeWithTag("delete_alarm").performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("还没有闹钟").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(database.alarmDao().getEnabled().isEmpty())
    }

    @Test fun activeAlarmCanEnterLiveCameraVerification() = runBlocking {
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
            listOf("vision_sink_score", "vision_toothbrush_score", "vision_toothpaste_score").all { tag ->
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.onNodeWithTag("camera_preview").assertIsDisplayed()
    }

    @Test fun verificationAndEmergencyOutcomesAreIdempotent() = runBlocking {
        val dao = database.wakeEventDao()
        val verifiedId = dao.insert(WakeEventEntity(alarmId = 101, triggeredAt = 1_000, outcome = WakeOutcome.RINGING))
        assertEquals(1, dao.startVerification(verifiedId, 2_000))
        assertEquals(1, dao.finishVerified(verifiedId, 3_000))
        assertEquals(0, dao.finishVerified(verifiedId, 4_000))
        assertEquals(WakeOutcome.VERIFIED, dao.getById(verifiedId)?.outcome)

        val emergencyId = dao.insert(WakeEventEntity(alarmId = 102, triggeredAt = 5_000, outcome = WakeOutcome.RINGING))
        assertEquals(1, dao.finishEmergency(emergencyId, 6_000))
        assertEquals(0, dao.finishVerified(emergencyId, 7_000))
        assertEquals(WakeOutcome.EMERGENCY_STOP, dao.getById(emergencyId)?.outcome)
    }
}
