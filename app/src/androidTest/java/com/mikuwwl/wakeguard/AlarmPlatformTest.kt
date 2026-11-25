package com.mikuwwl.wakeguard

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.alarm.AlarmCoordinator
import com.mikuwwl.wakeguard.alarm.AlarmForegroundService
import com.mikuwwl.wakeguard.alarm.AlarmRegistration
import com.mikuwwl.wakeguard.alarm.AlarmScheduler
import com.mikuwwl.wakeguard.data.AlarmEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.data.WakeOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmPlatformTest {
    @Test fun exactAlarmStartsAudioWithRingtoneFallbackAndEmergencyReleasesIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageId = context.packageName
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }

        shell("cmd appops set $packageId SCHEDULE_EXACT_ALARM allow")
        automation.grantRuntimePermission(packageId, Manifest.permission.POST_NOTIFICATIONS)
        val scheduler = AlarmScheduler(context)
        assertTrue("Emulator must grant exact-alarm access", scheduler.canScheduleExactAlarms())
        val database = WakeGuardDatabase.get(context)
        database.clearAllTables()
        val fastClock = object : AlarmRegistration by scheduler {
            override fun nextTriggerAt(alarm: AlarmEntity, now: Long) = now + 7_000L
        }
        val coordinator = AlarmCoordinator(database, fastClock)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val alarmId = coordinator.save(AlarmEntity(
            hour = 7, minute = 0, vibration = false,
            ringtoneUri = "content://$packageId.missing/invalid-tone",
        ))
        try {
            assertNotNull(database.alarmDao().getById(alarmId)?.deliveryToken)
            val event = withTimeout(35_000L) {
                var current = database.wakeEventDao().getActive()
                while (current == null || notifications.activeNotifications.none { it.id == 4101 }) {
                    delay(250)
                    current = database.wakeEventDao().getActive()
                }
                current
            }
            assertEquals(alarmId, event.alarmId)
            withTimeout(20_000L) {
                while (!shell("logcat -d -s WakeGuardAlarm:I").contains("Playback active event=${event.id} source=2 vibration=false") &&
                    !shell("logcat -d -s WakeGuardAlarm:I").contains("Playback active event=${event.id} source=3 vibration=false")) {
                    delay(350)
                }
            }
            assertTrue(AlarmForegroundService.isScheduledOrRunning(context))
            assertTrue(coordinator.finish(event.id, verified = false))
            withTimeout(15_000L) {
                while (notifications.activeNotifications.any { it.id == 4101 } ||
                    AlarmForegroundService.isScheduledOrRunning(context)) delay(250)
            }
            assertEquals(WakeOutcome.EMERGENCY_STOP, database.wakeEventDao().getById(event.id)?.outcome)
        } finally {
            database.wakeEventDao().getActive()?.let { coordinator.finish(it.id, verified = false) }
            coordinator.delete(alarmId)
        }
    }
}
