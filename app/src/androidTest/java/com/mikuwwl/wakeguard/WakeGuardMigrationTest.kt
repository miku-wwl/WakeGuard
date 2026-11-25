package com.mikuwwl.wakeguard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikuwwl.wakeguard.data.WakeGuardDatabase
import com.mikuwwl.wakeguard.data.WakeOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeGuardMigrationTest {
    @Test fun v1AlarmsAndWakeHistorySurviveWithActivePlaybackSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "wakeguard-migration-test.db"
        val file = context.getDatabasePath(name)
        context.deleteDatabase(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL("CREATE TABLE alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, repeatMask INTEGER NOT NULL, enabled INTEGER NOT NULL, vibration INTEGER NOT NULL, ringtoneUri TEXT)")
            v1.execSQL("CREATE TABLE wake_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, alarmId INTEGER NOT NULL, triggeredAt INTEGER NOT NULL, verificationStartedAt INTEGER, finishedAt INTEGER, outcome TEXT NOT NULL, failedAttempts INTEGER NOT NULL)")
            v1.execSQL("CREATE INDEX index_wake_events_triggeredAt ON wake_events (triggeredAt)")
            v1.execSQL("CREATE INDEX index_wake_events_outcome ON wake_events (outcome)")
            v1.execSQL("INSERT INTO alarms VALUES (7, 'saved in v1', 6, 15, 31, 1, 1, 'content://original/alarm')")
            v1.execSQL("INSERT INTO wake_events VALUES (10, 7, 1000, 2000, 3000, 'VERIFIED', 2)")
            v1.execSQL("INSERT INTO wake_events VALUES (11, 7, 4000, NULL, NULL, 'RINGING', 0)")
            v1.version = 1
        }
        val v2 = Room.databaseBuilder(context, WakeGuardDatabase::class.java, name)
            .addMigrations(WakeGuardDatabase.MIGRATION_1_2).build()
        try {
            val saved = checkNotNull(v2.alarmDao().getById(7))
            assertEquals("saved in v1", saved.label)
            assertEquals(31, saved.repeatMask)
            assertTrue(saved.enabled)
            assertNull(saved.deliveryToken)
            val history = checkNotNull(v2.wakeEventDao().getById(10))
            assertEquals(WakeOutcome.VERIFIED, history.outcome)
            assertEquals(2, history.failedAttempts)
            val active = checkNotNull(v2.wakeEventDao().getActive())
            assertEquals(11L, active.id)
            assertEquals("content://original/alarm", active.ringtoneUri)
            assertTrue(active.vibration)
        } finally {
            v2.close()
            context.deleteDatabase(name)
        }
        Unit
    }
}
