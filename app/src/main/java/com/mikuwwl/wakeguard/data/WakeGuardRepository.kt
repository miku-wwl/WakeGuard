package com.mikuwwl.wakeguard.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mikuwwl.wakeguard.alarm.AlarmCoordinator
import com.mikuwwl.wakeguard.alarm.AlarmForegroundService
import com.mikuwwl.wakeguard.alarm.AlarmIntents
import com.mikuwwl.wakeguard.alarm.AlarmRestore
import com.mikuwwl.wakeguard.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class WakeGuardRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = WakeGuardDatabase.get(appContext)
    private val alarmsDao = database.alarmDao()
    private val eventsDao = database.wakeEventDao()
    private val scheduler = AlarmScheduler(appContext)
    private val coordinator = AlarmCoordinator(appContext)

    val alarms: Flow<List<AlarmEntity>> = alarmsDao.observeAll()
    val activeEvent: Flow<WakeEventEntity?> = eventsDao.observeActive()
    val recentEvents: Flow<List<WakeEventEntity>> = eventsDao.observeRecent()

    fun canScheduleExactAlarms() = scheduler.canScheduleExactAlarms()

    suspend fun saveAlarm(alarm: AlarmEntity): Long = coordinator.save(alarm)
    suspend fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = coordinator.setEnabled(alarm.id, enabled)
    suspend fun deleteAlarm(alarmId: Long) = coordinator.delete(alarmId)
    suspend fun restoreOnForeground() = coordinator.restore(AlarmRestore.FOREGROUND)

    suspend fun resumeActiveAlarm(eventId: Long): Boolean {
        val active = eventsDao.getActive() ?: return false
        if (active.id != eventId) return false
        return runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, AlarmForegroundService::class.java)
                    .putExtra(AlarmIntents.EXTRA_EVENT_ID, eventId)
                    .putExtra(AlarmIntents.EXTRA_START_REASON, AlarmIntents.START_REASON_USER_CONFIRMED_RESUME),
            )
            true
        }.getOrDefault(false)
    }

    suspend fun beginVerification(eventId: Long, now: Long = System.currentTimeMillis()) =
        eventsDao.startVerification(eventId, now) == 1

    suspend fun recordFailedAttempt(eventId: Long) = eventsDao.recordFailedAttempt(eventId) == 1

    suspend fun finishVerified(eventId: Long, now: Long = System.currentTimeMillis()) =
        coordinator.finish(eventId, verified = true, now = now)

    suspend fun finishEmergency(eventId: Long, now: Long = System.currentTimeMillis()) =
        coordinator.finish(eventId, verified = false, now = now)

}
