package com.mikuwwl.wakeguard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmIntents.ACTION_TRIGGER) return
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        val dueAt = intent.getLongExtra(AlarmIntents.EXTRA_SCHEDULED_AT, -1L)
        val token = intent.getStringExtra(AlarmIntents.EXTRA_DELIVERY_TOKEN)
        if (alarmId <= 0L || dueAt <= 0L || token.isNullOrBlank()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmCoordinator(context).deliver(AlarmDelivery(alarmId, dueAt, token)) { eventId ->
                    ContextCompat.startForegroundService(
                        context, Intent(context, AlarmForegroundService::class.java)
                            .putExtra(AlarmIntents.EXTRA_EVENT_ID, eventId),
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not start alarm $alarmId", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "WakeGuardAlarm" }
}
