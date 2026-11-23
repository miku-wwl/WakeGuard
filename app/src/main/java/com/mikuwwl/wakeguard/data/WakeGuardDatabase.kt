package com.mikuwwl.wakeguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AlarmEntity::class, WakeEventEntity::class], version = 1, exportSchema = true)
abstract class WakeGuardDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun wakeEventDao(): WakeEventDao

    companion object {
        @Volatile private var instance: WakeGuardDatabase? = null

        fun get(context: Context): WakeGuardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                WakeGuardDatabase::class.java,
                "wakeguard.db",
            ).build().also { instance = it }
        }
    }
}
