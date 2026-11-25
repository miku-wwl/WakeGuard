package com.mikuwwl.wakeguard.data

import android.content.Context
import com.mikuwwl.wakeguard.alarm.AlarmCoordinator
import com.mikuwwl.wakeguard.alarm.AlarmRestore
import com.mikuwwl.wakeguard.alarm.AlarmScheduler
import com.mikuwwl.wakeguard.domain.VisionThresholds
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

    suspend fun beginVerification(eventId: Long, now: Long = System.currentTimeMillis()) =
        eventsDao.startVerification(eventId, now) == 1

    suspend fun recordFailedAttempt(eventId: Long) = eventsDao.recordFailedAttempt(eventId) == 1

    suspend fun finishVerified(eventId: Long, now: Long = System.currentTimeMillis()) =
        coordinator.finish(eventId, verified = true, now = now)

    suspend fun finishEmergency(eventId: Long, now: Long = System.currentTimeMillis()) =
        coordinator.finish(eventId, verified = false, now = now)

    fun thresholds(): VisionThresholds {
        val prefs = appContext.getSharedPreferences("wakeguard_settings", Context.MODE_PRIVATE)
        return VisionThresholds(
            sink = prefs.getFloat("threshold_sink", 0.45f),
            toothbrush = prefs.getFloat("threshold_toothbrush", 0.45f),
            toothpaste = prefs.getFloat("threshold_toothpaste", 0.93f),
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
