package com.mikuwwl.wakeguard.alarm

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.mikuwwl.wakeguard.R
import com.mikuwwl.wakeguard.WakeGuardApplication
import com.mikuwwl.wakeguard.data.WakeEventEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlarmForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = PlaybackOwner<AlarmPlayback>()
    private val coordinator by lazy { AlarmCoordinator(applicationContext) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var observing = false
    private var latestStartId = 0
    private var activeEventId: Long? = null
    private var activeAlarmId: Long? = null
    private var taskRemovedCallbackObserved = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WakeGuardDiagnostics.record(this, "SERVICE_CREATED")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val requested = intent?.getLongExtra(AlarmIntents.EXTRA_EVENT_ID, -1L)?.takeIf { it > 0L }
        val startReason = intent?.getStringExtra(AlarmIntents.EXTRA_START_REASON)
            ?: if (intent == null) "sticky_null_intent" else "start_intent"
        WakeGuardDiagnostics.record(this, "SERVICE_STARTED", eventId = requested, reason = startReason)
        if (observing) return START_STICKY
        observing = true
        startForeground(NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        serviceScope.launch {
            WakeGuardDatabase.get(applicationContext).wakeEventDao().observeActive().collect { event ->
                if (event == null) {
                    activeEventId = null
                    activeAlarmId = null
                    owner.close()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(latestStartId)
                } else {
                    activeEventId = event.id
                    activeAlarmId = event.alarmId
                    if (intent == null || startReason == AlarmIntents.START_REASON_USER_CONFIRMED_RESUME) {
                        WakeGuardDiagnostics.record(
                            this@AlarmForegroundService,
                            "PROCESS_RECOVERED",
                            eventId = event.id,
                            alarmId = event.alarmId,
                            reason = startReason,
                        )
                    }
                    owner.start(event.id) { AlarmPlayback(event) }?.let { session ->
                        if (requested != event.id) Log.i(TAG, "Recovered persisted event=${event.id}")
                        notifications.notify(NOTIFICATION_ID, notification(event))
                        session.start()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        taskRemovedCallbackObserved = true
        WakeGuardDiagnostics.record(
            this,
            "TASK_REMOVED",
            eventId = activeEventId,
            alarmId = activeAlarmId,
            reason = if (activeEventId == null) "no_cached_active_event" else "activity_task_removed_playback_retained",
        )
        // The started foreground service and its event-owned player outlive the Activity task.
        // Do not stop the service or schedule a self-restart from this callback.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        WakeGuardDiagnostics.record(
            this,
            "SERVICE_DESTROYED",
            eventId = activeEventId,
            alarmId = activeAlarmId,
            reason = if (taskRemovedCallbackObserved) "after_task_removed_callback_observed" else "callback_cause_unspecified",
        )
        serviceScope.cancel()
        owner.close()
        observing = false
        activeEventId = null
        activeAlarmId = null
        super.onDestroy()
    }

    private fun notification(event: WakeEventEntity?): Notification {
        val builder = Notification.Builder(this, WakeGuardApplication.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wakeguard)
            .setContentTitle(if (event == null) "WakeGuard 正在恢复闹钟" else "WakeGuard 正在响铃")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
        if (event != null) {
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).apply {
                    action = AlarmIntents.ACTION_SHOW_ALARM
                    data = Uri.parse("wakeguard://ringing/${event.id}")
                    putExtra(AlarmIntents.EXTRA_EVENT_ID, event.id)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentText("打开应用完成起床验证").setContentIntent(open)
            if (Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent()) {
                builder.setFullScreenIntent(open, true)
            }
        }
        return builder.build()
    }

    private inner class AlarmPlayback(private val event: WakeEventEntity) : AutoCloseable {
        private val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        private var player: MediaPlayer? = null
        private var wakeLock: PowerManager.WakeLock? = null
        private var vibrator: Vibrator? = null
        private var sources = emptyList<Uri>()
        private var sourceIndex = 0
        private var closed = false

        fun start() {
            wakeLock = runCatching {
                getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:wakeguard-alarm")
                    .apply { acquire(3 * 60 * 60 * 1_000L) }
            }.onFailure { Log.w(TAG, "Could not acquire alarm wake lock", it) }.getOrNull()

            if (event.vibration) startVibration()
            val systemDefault = runCatching {
                RingtoneManager.getActualDefaultRingtoneUri(this@AlarmForegroundService, RingtoneManager.TYPE_ALARM)
            }.getOrNull()
            sources = listOfNotNull(
                event.ringtoneUri?.takeIf(String::isNotBlank)?.let(Uri::parse),
                systemDefault,
                Uri.parse("android.resource://$packageName/${R.raw.backup_alarm}"),
            ).distinct()
            prepareNextSource()
        }

        private fun startVibration() {
            @Suppress("DEPRECATION")
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else getSystemService(Vibrator::class.java)
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 700), 0)
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    vibrator?.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(effect, audio)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Vibration unavailable for event=${event.id}", error)
            }
        }

        private fun prepareNextSource() {
            releasePlayer()
            while (owner.owns(this) && sourceIndex < sources.size) {
                val uri = sources[sourceIndex++]
                try {
                    val candidate = MediaPlayer().also { player = it }
                    candidate.setAudioAttributes(audio)
                    candidate.setDataSource(this@AlarmForegroundService, uri)
                    candidate.isLooping = true
                    candidate.setOnPreparedListener { ready ->
                        serviceScope.launch {
                            coordinator.whileActive(event.id) {
                                if (owner.owns(this@AlarmPlayback) && player === ready) {
                                    try {
                                        ready.start()
                                        Log.i(TAG, "Playback active event=${event.id} source=$sourceIndex vibration=${event.vibration}")
                                        WakeGuardDiagnostics.record(
                                            this@AlarmForegroundService,
                                            "PLAYBACK_STARTED",
                                            eventId = event.id,
                                            alarmId = event.alarmId,
                                            reason = "media_player_prepared",
                                        )
                                    } catch (_: IllegalStateException) {
                                        prepareNextSource()
                                    }
                                }
                            }
                        }
                    }
                    candidate.setOnErrorListener { failed, _, _ ->
                        if (owner.owns(this) && player === failed) {
                            Log.w(TAG, "Playback source $sourceIndex failed for event=${event.id}")
                            serviceScope.launch {
                                coordinator.whileActive(event.id) {
                                    if (owner.owns(this@AlarmPlayback) && player === failed) prepareNextSource()
                                }
                            }
                        }
                        true
                    }
                    candidate.prepareAsync()
                    return
                } catch (_: Exception) {
                    Log.w(TAG, "Playback source $sourceIndex unavailable for event=${event.id}")
                    releasePlayer()
                }
            }
            if (owner.owns(this)) serviceScope.launch { coordinator.fail(event.id) }
        }

        private fun releasePlayer() {
            val old = player
            player = null
            old?.setOnPreparedListener(null)
            old?.setOnErrorListener(null)
            old?.release()
        }

        override fun close() {
            if (closed) return
            closed = true
            releasePlayer()
            vibrator?.cancel()
            vibrator = null
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            Log.i(TAG, "Released playback resources event=${event.id}")
            WakeGuardDiagnostics.record(
                this@AlarmForegroundService,
                "PLAYBACK_RELEASED",
                eventId = event.id,
                alarmId = event.alarmId,
                reason = "playback_owner_closed",
            )
        }
    }

    companion object {
        private const val TAG = "WakeGuardAlarm"
        private const val NOTIFICATION_ID = 4101

        // Android restricts this API to our own services on supported API levels.
        @Suppress("DEPRECATION")
        internal fun isScheduledOrRunning(context: Context): Boolean =
            context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == AlarmForegroundService::class.java.name && it.started }
    }
}
