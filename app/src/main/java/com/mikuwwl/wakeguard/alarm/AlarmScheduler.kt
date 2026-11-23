package com.mikuwwl.wakeguard.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mikuwwl.wakeguard.data.AlarmEntity
import com.mikuwwl.wakeguard.domain.AlarmSchedule
import com.mikuwwl.wakeguard.domain.AlarmScheduleCalculator
import com.mikuwwl.wakeguard.presentation.MainActivity
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    fun nextTriggerAt(alarm: AlarmEntity, nowMillis: Long = System.currentTimeMillis()): Long =
        AlarmScheduleCalculator.nextTriggerAt(
            nowMillis,
            AlarmSchedule(alarm.hour, alarm.minute, alarm.repeatMask),
            ZoneId.systemDefault(),
        )

    fun schedule(alarm: AlarmEntity): Boolean {
        if (!alarm.enabled || !canScheduleExactAlarms()) return false
        val triggerAt = nextTriggerAt(alarm)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = AlarmIntents.ACTION_SHOW_ALARM
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            Intent(context, AlarmTriggerReceiver::class.java).apply {
                action = AlarmIntents.ACTION_TRIGGER
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), triggerIntent)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(alarmId: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, AlarmTriggerReceiver::class.java).apply {
                action = AlarmIntents.ACTION_TRIGGER
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) manager.cancel(pending)
        pending?.cancel()
    }

    suspend fun scheduleEnabledAlarms() {
        val alarms = com.mikuwwl.wakeguard.data.WakeGuardDatabase.get(context).alarmDao().getEnabled()
        alarms.forEach(::schedule)
    }
}

object AlarmIntents {
    const val EXTRA_ALARM_ID = "com.mikuwwl.wakeguard.extra.ALARM_ID"
    const val EXTRA_EVENT_ID = "com.mikuwwl.wakeguard.extra.EVENT_ID"
    const val ACTION_TRIGGER = "com.mikuwwl.wakeguard.action.TRIGGER"
    const val ACTION_SHOW_ALARM = "com.mikuwwl.wakeguard.action.SHOW_ALARM"
    const val ACTION_STOP = "com.mikuwwl.wakeguard.action.STOP_ALARM"
}
