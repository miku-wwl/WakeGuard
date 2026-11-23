package com.mikuwwl.wakeguard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.mikuwwl.wakeguard.data.WakeEventEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.data.WakeOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId <= 0L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = WakeGuardDatabase.get(context)
                val alarm = db.alarmDao().getById(alarmId) ?: return@launch
                val active = db.wakeEventDao().getActive()
                val eventId = when {
                    active == null -> db.wakeEventDao().insert(
                        WakeEventEntity(alarmId = alarmId, triggeredAt = System.currentTimeMillis(), outcome = WakeOutcome.RINGING),
                    )
                    active.alarmId == alarmId -> active.id
                    else -> {
                        Log.w(TAG, "Skipping overlapping alarm $alarmId while event ${active.id} is active")
                        return@launch
                    }
                }
                if (alarm.repeatMask == 0 && alarm.enabled) {
                    db.alarmDao().update(alarm.copy(enabled = false))
                } else if (alarm.enabled) {
                    AlarmScheduler(context).schedule(alarm)
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmForegroundService::class.java).apply {
                        putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                        putExtra(AlarmIntents.EXTRA_EVENT_ID, eventId)
                    },
                )
                Log.i(TAG, "Triggered alarm=$alarmId event=$eventId")
            } catch (error: Exception) {
                Log.e(TAG, "Could not start alarm $alarmId", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "WakeGuardAlarm" }
}
