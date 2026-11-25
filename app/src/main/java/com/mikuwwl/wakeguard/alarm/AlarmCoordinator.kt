package com.mikuwwl.wakeguard.alarm

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.mikuwwl.wakeguard.data.AlarmEntity
import com.mikuwwl.wakeguard.data.WakeEventEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.data.WakeOutcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal enum class AlarmRestore { FOREGROUND, BOOT, CLOCK_CHANGED, PERMISSION_CHANGED, PACKAGE_UPDATED }

/** Owns persisted occurrences and Android registrations in the application's single process. */
internal class AlarmCoordinator(
    private val db: WakeGuardDatabase,
    private val registration: AlarmRegistration,
    private val clock: () -> Long = System::currentTimeMillis,
    private val hasSystemService: () -> Boolean = { true },
    private val diagnosticsContext: Context? = null,
) {
    constructor(context: Context) : this(
        WakeGuardDatabase.get(context), AlarmScheduler(context.applicationContext),
        hasSystemService = { AlarmForegroundService.isScheduledOrRunning(context.applicationContext) },
        diagnosticsContext = context.applicationContext,
    )

    private val alarms = db.alarmDao()
    private val events = db.wakeEventDao()

    suspend fun save(alarm: AlarmEntity): Long = mutex.withLock {
        val clean = alarm.copy(scheduledAt = null, deliveryToken = null)
        val id = if (alarm.id == 0L) alarms.insert(clean) else {
            val previous = alarms.getById(alarm.id) ?: return@withLock alarm.id
            alarms.update(clean)
            registration.cancel(previous)
            alarm.id
        }
        if (alarm.enabled) schedule(clean.copy(id = id), clock())
        id
    }

    suspend fun setEnabled(alarmId: Long, enabled: Boolean) = mutex.withLock {
        val previous = alarms.getById(alarmId) ?: return@withLock
        if (previous.enabled == enabled) return@withLock
        val updated = previous.copy(enabled = enabled, scheduledAt = null, deliveryToken = null)
        alarms.update(updated)
        registration.cancel(previous)
        if (enabled) schedule(updated, clock())
    }

    suspend fun delete(alarmId: Long) = mutex.withLock {
        val previous = alarms.getById(alarmId) ?: return@withLock
        alarms.delete(alarmId)
        registration.cancel(previous)
    }

    suspend fun restore(reason: AlarmRestore) = mutex.withLock {
        val now = clock()
        val activeBeforeRestore = events.getActive()
        if (reason == AlarmRestore.BOOT) {
            val interrupted = events.interruptActive(now)
            if (interrupted > 0) {
                diagnosticsContext?.let { context -> WakeGuardDiagnostics.record(
                    context = context,
                    event = "EVENT_INTERRUPTED",
                    eventId = activeBeforeRestore?.id,
                    alarmId = activeBeforeRestore?.alarmId,
                    reason = "device_boot",
                ) }
            }
        } else if (reason == AlarmRestore.FOREGROUND && !hasSystemService() && activeBeforeRestore != null) {
            diagnosticsContext?.let { context -> WakeGuardDiagnostics.record(
                context = context,
                event = "ACTIVE_EVENT_SERVICE_ABSENT",
                eventId = activeBeforeRestore.id,
                alarmId = activeBeforeRestore.alarmId,
                reason = "foreground_reconciliation_preserved_event",
            ) }
        }
        for (alarm in alarms.getEnabled()) {
            val delivery = AlarmDelivery.from(alarm)
            val due = alarm.scheduledAt
            if (reason == AlarmRestore.FOREGROUND && delivery != null && (
                    registration.isRegistered(delivery) ||
                        (due != null && now - due in 0..DELIVERY_GRACE_MS)
                )
            ) continue
            registration.cancel(alarm)
            if (due != null && due <= now && alarm.repeatMask == 0) {
                db.withTransaction {
                    alarms.update(alarm.copy(enabled = false, scheduledAt = null, deliveryToken = null))
                    events.insert(WakeEventEntity(alarmId = alarm.id, triggeredAt = due, finishedAt = now, outcome = WakeOutcome.MISSED))
                }
            } else if (reason != AlarmRestore.CLOCK_CHANGED && due != null && due > now) {
                schedule(alarm, now, due)
            } else {
                schedule(alarm, now)
            }
        }
    }

    suspend fun deliver(delivery: AlarmDelivery, startPlayback: (Long) -> Unit): Long? = mutex.withLock {
        val now = clock()
        val alarm = alarms.getById(delivery.alarmId) ?: return@withLock null
        if (!delivery.matches(alarm, now)) return@withLock null
        val repeating = alarm.repeatMask != 0
        val consumed = alarm.copy(enabled = repeating, scheduledAt = null, deliveryToken = null)
        val event = db.withTransaction {
            val occupied = events.getActive() != null
            val row = WakeEventEntity(
                alarmId = alarm.id,
                triggeredAt = now,
                finishedAt = now.takeIf { occupied },
                outcome = if (occupied) WakeOutcome.MISSED else WakeOutcome.RINGING,
                ringtoneUri = alarm.ringtoneUri,
                vibration = alarm.vibration,
            )
            alarms.update(consumed)
            row.copy(id = events.insert(row))
        }
        diagnosticsContext?.let { context -> WakeGuardDiagnostics.record(
            context = context,
            event = "ALARM_TRIGGERED",
            eventId = event.id,
            alarmId = alarm.id,
            reason = if (event.outcome == WakeOutcome.RINGING) "accepted" else "overlapping_missed",
        ) }
        registration.cancel(alarm)
        if (repeating) schedule(consumed, now)
        if (event.outcome == WakeOutcome.RINGING) {
            try {
                startPlayback(event.id)
                Log.i(TAG, "Accepted alarm=${alarm.id} event=${event.id}")
            } catch (error: Exception) {
                events.failActive(event.id, clock())
                Log.e(TAG, "Could not start playback for event=${event.id}", error)
            }
        } else {
            Log.i(TAG, "Recorded overlapping alarm=${alarm.id} event=${event.id} as missed")
        }
        event.id
    }

    suspend fun finish(eventId: Long, verified: Boolean, now: Long = clock()): Boolean = mutex.withLock {
        val event = events.getById(eventId)
        val finished = if (verified) events.finishVerified(eventId, now) == 1 else events.finishEmergency(eventId, now) == 1
        if (finished) {
            diagnosticsContext?.let { context -> WakeGuardDiagnostics.record(
                context = context,
                event = if (verified) "VERIFIED" else "EMERGENCY_STOP",
                eventId = eventId,
                alarmId = event?.alarmId,
            ) }
        }
        finished
    }

    suspend fun fail(eventId: Long) = mutex.withLock { events.failActive(eventId, clock()) }

    // Prepared callbacks and terminal transactions share this lock.
    suspend fun whileActive(eventId: Long, play: () -> Unit) = mutex.withLock {
        if (events.getActive()?.id == eventId) play()
    }

    private suspend fun schedule(alarm: AlarmEntity, now: Long, intendedAt: Long? = null) {
        val scheduledAt = intendedAt ?: registration.nextTriggerAt(alarm, now)
        val delivery = AlarmDelivery(alarm.id, scheduledAt, UUID.randomUUID().toString())
        val token = delivery.token.takeIf { registration.register(delivery) }
        alarms.update(alarm.copy(scheduledAt = scheduledAt, deliveryToken = token))
        diagnosticsContext?.let { context -> WakeGuardDiagnostics.record(
            context = context,
            event = "ALARM_SCHEDULED",
            alarmId = alarm.id,
            reason = if (token == null) "registration_denied" else "registered",
            timestamp = now,
        ) }
        if (token == null) Log.w(TAG, "Alarm ${alarm.id} saved without exact registration")
    }

    private companion object {
        val mutex = Mutex()
        const val DELIVERY_GRACE_MS = 120_000L
        const val TAG = "WakeGuardAlarm"
    }
}
