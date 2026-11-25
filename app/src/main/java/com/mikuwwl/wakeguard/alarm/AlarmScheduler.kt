package com.mikuwwl.wakeguard.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mikuwwl.wakeguard.data.AlarmEntity
import com.mikuwwl.wakeguard.domain.AlarmSchedule
import com.mikuwwl.wakeguard.domain.AlarmScheduleCalculator
import com.mikuwwl.wakeguard.presentation.MainActivity
import java.time.ZoneId

internal interface AlarmRegistration {
    fun nextTriggerAt(alarm: AlarmEntity, now: Long): Long
    fun register(delivery: AlarmDelivery): Boolean
    fun isRegistered(delivery: AlarmDelivery): Boolean
    fun cancel(alarm: AlarmEntity)
}

internal data class AlarmDelivery(val alarmId: Long, val dueAt: Long, val token: String) {
    fun matches(alarm: AlarmEntity, now: Long): Boolean =
        alarm.enabled && dueAt <= now && alarm.id == alarmId &&
            alarm.scheduledAt == dueAt && alarm.deliveryToken == token

    companion object {
        fun from(alarm: AlarmEntity): AlarmDelivery? {
            return AlarmDelivery(alarm.id, alarm.scheduledAt ?: return null, alarm.deliveryToken ?: return null)
        }
    }
}

internal class AlarmScheduler(private val context: Context) : AlarmRegistration {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    override fun nextTriggerAt(alarm: AlarmEntity, now: Long): Long =
        AlarmScheduleCalculator.nextTriggerAt(
            now, AlarmSchedule(alarm.hour, alarm.minute, alarm.repeatMask), ZoneId.systemDefault(),
        )

    override fun register(delivery: AlarmDelivery): Boolean {
        if (!canScheduleExactAlarms()) return false
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).apply {
                action = AlarmIntents.ACTION_SHOW_ALARM
                data = Uri.parse("wakeguard://scheduled/${delivery.alarmId}")
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pending = PendingIntent.getBroadcast(context, 0, triggerIntent(delivery), FLAGS)
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(delivery.dueAt, showIntent), pending)
            true
        } catch (error: SecurityException) {
            pending.cancel()
            Log.w(TAG, "Exact alarm access was denied during registration", error)
            false
        }
    }

    override fun isRegistered(delivery: AlarmDelivery): Boolean =
        canScheduleExactAlarms() && PendingIntent.getBroadcast(
            context, 0, triggerIntent(delivery), FLAGS or PendingIntent.FLAG_NO_CREATE,
        ) != null

    override fun cancel(alarm: AlarmEntity) {
        val delivery = AlarmDelivery.from(alarm)
        val pending = if (delivery != null) {
            PendingIntent.getBroadcast(context, 0, triggerIntent(delivery), FLAGS or PendingIntent.FLAG_NO_CREATE)
        } else {
            // Upgrade path for alarms enrolled before delivery tokens existed.
            PendingIntent.getBroadcast(
                context, alarm.id.toInt(),
                Intent(context, AlarmTriggerReceiver::class.java).setAction(AlarmIntents.ACTION_TRIGGER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun triggerIntent(delivery: AlarmDelivery) = Intent(context, AlarmTriggerReceiver::class.java).apply {
        action = AlarmIntents.ACTION_TRIGGER
        data = Uri.parse("wakeguard://delivery/${delivery.alarmId}/${delivery.token}")
        putExtra(AlarmIntents.EXTRA_ALARM_ID, delivery.alarmId)
        putExtra(AlarmIntents.EXTRA_SCHEDULED_AT, delivery.dueAt)
        putExtra(AlarmIntents.EXTRA_DELIVERY_TOKEN, delivery.token)
    }

    private companion object {
        const val TAG = "WakeGuardAlarm"
        const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    }
}

object AlarmIntents {
    const val EXTRA_ALARM_ID = "com.mikuwwl.wakeguard.extra.ALARM_ID"
    const val EXTRA_EVENT_ID = "com.mikuwwl.wakeguard.extra.EVENT_ID"
    const val EXTRA_SCHEDULED_AT = "com.mikuwwl.wakeguard.extra.SCHEDULED_AT"
    const val EXTRA_DELIVERY_TOKEN = "com.mikuwwl.wakeguard.extra.DELIVERY_TOKEN"
    const val ACTION_TRIGGER = "com.mikuwwl.wakeguard.action.TRIGGER"
    const val ACTION_SHOW_ALARM = "com.mikuwwl.wakeguard.action.SHOW_ALARM"
}
