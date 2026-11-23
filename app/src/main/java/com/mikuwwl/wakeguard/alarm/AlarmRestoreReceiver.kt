package com.mikuwwl.wakeguard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = WakeGuardDatabase.get(context)
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    db.wakeEventDao().interruptActive(System.currentTimeMillis())
                }
                AlarmScheduler(context).scheduleEnabledAlarms()
                Log.i(TAG, "Rescheduled alarms after $action")
            } catch (error: Exception) {
                Log.e(TAG, "Alarm restore failed after $action", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "WakeGuardRestore" }
}
