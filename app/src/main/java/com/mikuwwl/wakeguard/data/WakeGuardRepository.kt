package com.mikuwwl.wakeguard.data

import android.content.Context
import com.mikuwwl.wakeguard.alarm.AlarmScheduler
import com.mikuwwl.wakeguard.domain.VisionThresholds
import kotlinx.coroutines.flow.Flow

class WakeGuardRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = WakeGuardDatabase.get(appContext)
    private val alarmsDao = database.alarmDao()
    private val eventsDao = database.wakeEventDao()
    private val scheduler = AlarmScheduler(appContext)

    val alarms: Flow<List<AlarmEntity>> = alarmsDao.observeAll()
    val activeEvent: Flow<WakeEventEntity?> = eventsDao.observeActive()
    val recentEvents: Flow<List<WakeEventEntity>> = eventsDao.observeRecent()

    fun canScheduleExactAlarms() = scheduler.canScheduleExactAlarms()

    fun nextTriggerAt(alarm: AlarmEntity) = scheduler.nextTriggerAt(alarm)

    suspend fun saveAlarm(alarm: AlarmEntity): Long {
        val id = if (alarm.id == 0L) alarmsDao.insert(alarm) else {
            alarmsDao.update(alarm)
            alarm.id
        }
        alarmsDao.getById(id)?.let(scheduler::schedule)
        return id
    }

    suspend fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        val updated = alarm.copy(enabled = enabled)
        alarmsDao.update(updated)
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(alarm.id)
    }

    suspend fun deleteAlarm(alarmId: Long) {
        scheduler.cancel(alarmId)
        alarmsDao.delete(alarmId)
    }

    suspend fun rescheduleEnabledAlarms() = scheduler.scheduleEnabledAlarms()

    suspend fun activeEventNow() = eventsDao.getActive()

    suspend fun beginVerification(eventId: Long, now: Long = System.currentTimeMillis()) =
        eventsDao.startVerification(eventId, now) == 1

    suspend fun recordFailedAttempt(eventId: Long) = eventsDao.recordFailedAttempt(eventId) == 1

    suspend fun finishVerified(eventId: Long, now: Long = System.currentTimeMillis()) =
        eventsDao.finishVerified(eventId, now) == 1

    suspend fun finishEmergency(eventId: Long, now: Long = System.currentTimeMillis()) =
        eventsDao.finishEmergency(eventId, now) == 1

    suspend fun interruptActive(now: Long = System.currentTimeMillis()) = eventsDao.interruptActive(now)

    fun thresholds(): VisionThresholds {
        val prefs = appContext.getSharedPreferences("wakeguard_settings", Context.MODE_PRIVATE)
        return VisionThresholds(
            sink = prefs.getFloat("threshold_sink", 0.45f),
            toothbrush = prefs.getFloat("threshold_toothbrush", 0.45f),
            toothpaste = prefs.getFloat("threshold_toothpaste", 0.80f),
        ).sanitized()
    }

    fun saveThresholds(thresholds: VisionThresholds) {
        val safe = thresholds.sanitized()
        appContext.getSharedPreferences("wakeguard_settings", Context.MODE_PRIVATE).edit()
            .putFloat("threshold_sink", safe.sink)
            .putFloat("threshold_toothbrush", safe.toothbrush)
            .putFloat("threshold_toothpaste", safe.toothpaste)
            .apply()
    }
}
