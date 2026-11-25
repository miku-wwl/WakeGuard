package com.mikuwwl.wakeguard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val reason = when (action) {
            Intent.ACTION_BOOT_COMPLETED -> AlarmRestore.BOOT
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> AlarmRestore.CLOCK_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmRestore.PACKAGE_UPDATED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> AlarmRestore.PERMISSION_CHANGED
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmCoordinator(context).restore(reason)
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
