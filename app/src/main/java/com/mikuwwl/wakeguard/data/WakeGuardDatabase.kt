package com.mikuwwl.wakeguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlarmEntity::class, WakeEventEntity::class], version = 2, exportSchema = true)
abstract class WakeGuardDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun wakeEventDao(): WakeEventDao

    companion object {
        @Volatile private var instance: WakeGuardDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN scheduledAt INTEGER")
                db.execSQL("ALTER TABLE alarms ADD COLUMN deliveryToken TEXT")
                db.execSQL("ALTER TABLE wake_events ADD COLUMN ringtoneUri TEXT")
                db.execSQL("ALTER TABLE wake_events ADD COLUMN vibration INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    UPDATE wake_events SET
                    ringtoneUri = (SELECT ringtoneUri FROM alarms WHERE alarms.id = wake_events.alarmId),
                    vibration = COALESCE((SELECT vibration FROM alarms WHERE alarms.id = wake_events.alarmId), 0)
                    WHERE outcome IN ('RINGING', 'VERIFYING')
                """.trimIndent())
            }
        }

        fun get(context: Context): WakeGuardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WakeGuardDatabase::class.java,
                "wakeguard.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
