package com.mikuwwl.wakeguard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikuwwl.wakeguard.alarm.AlarmCoordinator
import com.mikuwwl.wakeguard.alarm.AlarmDelivery
import com.mikuwwl.wakeguard.alarm.AlarmRegistration
import com.mikuwwl.wakeguard.alarm.AlarmRestore
import com.mikuwwl.wakeguard.data.AlarmEntity
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.data.WakeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmCoordinatorTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), WakeGuardDatabase::class.java,
    ).build()
    private val registration = FakeRegistration()
    private var time = 1_000L
    private var serviceOwned = true
    private val alarms = AlarmCoordinator(db, registration, { time }, { serviceOwned })

    @After fun close() = db.close()

    @Test fun editingDisablingAndDeletingInvalidatesPendingOccurrences() = runBlocking {
        val id = alarms.save(AlarmEntity(hour = 10, minute = 0))
        val original = registration.current.getValue(id)
        alarms.save(checkNotNull(db.alarmDao().getById(id)).copy(minute = 1))
        val edited = registration.current.getValue(id)
        assertNotEquals(original.token, edited.token)
        time = original.dueAt
        assertNull(alarms.deliver(original) { error("Edited schedule was accepted") })
        alarms.setEnabled(id, false)
        assertNull(alarms.deliver(edited) { error("Disabled schedule was accepted") })
        alarms.save(checkNotNull(db.alarmDao().getById(id)).copy(minute = 2))
        assertFalse(registration.current.containsKey(id))
        alarms.setEnabled(id, true)
        val enabled = registration.current.getValue(id)
        alarms.delete(id)
        time = enabled.dueAt
        assertNull(alarms.deliver(enabled) { error("Deleted schedule was accepted") })
    }

    @Test fun duplicateDeliveryRingsOnceAndOverlapIsRecordedAndRescheduled() = runBlocking {
        val single = alarms.save(AlarmEntity(hour = 10, minute = 0, vibration = false))
        val repeating = alarms.save(AlarmEntity(hour = 10, minute = 0, repeatMask = 127))
        val first = registration.current.getValue(single)
        val second = registration.current.getValue(repeating)
        val played = mutableListOf<Long>()
        time = first.dueAt
        val a = async(Dispatchers.Default) { alarms.deliver(first, played::add) }
        val b = async(Dispatchers.Default) { alarms.deliver(first, played::add) }
        val event = listOfNotNull(a.await(), b.await()).single()
        assertEquals(listOf(event), played)
        val missed = alarms.deliver(second, played::add)!!
        assertEquals(WakeOutcome.MISSED, db.wakeEventDao().getById(missed)?.outcome)
        assertEquals(false, db.wakeEventDao().getById(event)?.vibration)
        assertEquals(event, db.wakeEventDao().getActive()?.id)
        assertTrue(registration.current.getValue(repeating).dueAt > second.dueAt)
        assertFalse(db.alarmDao().getById(single)!!.enabled)
        assertFalse(alarms.finish(event, verified = true))
        assertTrue(alarms.finish(event, verified = false))
        alarms.whileActive(event) { error("Late prepared callback acquired a terminal event") }
        assertFalse(alarms.finish(event, verified = false))
    }

    @Test fun deniedRegistrationIsRetriedAndMissedOneTimeIsNotReplayed() = runBlocking {
        registration.allowed = false
        val unregistered = alarms.save(AlarmEntity(hour = 10, minute = 0, repeatMask = 127))
        assertNull(db.alarmDao().getById(unregistered)?.deliveryToken)
        assertTrue(db.alarmDao().getById(unregistered)!!.enabled)
        registration.allowed = true
        alarms.restore(AlarmRestore.PERMISSION_CHANGED)
        assertTrue(registration.current.containsKey(unregistered))

        val oneTime = alarms.save(AlarmEntity(hour = 10, minute = 0))
        time = registration.current.getValue(oneTime).dueAt + 1
        alarms.restore(AlarmRestore.BOOT)
        assertFalse(db.alarmDao().getById(oneTime)!!.enabled)
        assertFalse(registration.current.containsKey(oneTime))
    }

    @Test fun reopeningAfterSystemStopInterruptsButNeverReplaysATerminalEvent() = runBlocking {
        val id = alarms.save(AlarmEntity(hour = 10, minute = 0))
        val due = registration.current.getValue(id)
        time = due.dueAt
        val event = alarms.deliver(due) {}!!
        alarms.restore(AlarmRestore.FOREGROUND)
        assertEquals(event, db.wakeEventDao().getActive()?.id)
        serviceOwned = false
        alarms.restore(AlarmRestore.FOREGROUND)
        assertEquals(WakeOutcome.INTERRUPTED, db.wakeEventDao().getById(event)?.outcome)
        assertNull(db.wakeEventDao().getActive())
        alarms.whileActive(event) { error("Terminal session should not resume playback") }
    }

    private class FakeRegistration : AlarmRegistration {
        val current = mutableMapOf<Long, AlarmDelivery>()
        var allowed = true
        override fun nextTriggerAt(alarm: AlarmEntity, now: Long): Long = now + 60_000L
        override fun register(delivery: AlarmDelivery): Boolean {
            if (allowed) current[delivery.alarmId] = delivery
            return allowed
        }
        override fun isRegistered(delivery: AlarmDelivery) = current[delivery.alarmId] == delivery
        override fun cancel(alarm: AlarmEntity) {
            current.remove(alarm.id)
        }
    }
}
