package com.mikuwwl.wakeguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WakeEventDao {
    @Query("SELECT * FROM wake_events WHERE id = :id")
    suspend fun getById(id: Long): WakeEventEntity?

    @Query("SELECT * FROM wake_events WHERE outcome IN ('RINGING', 'VERIFYING') ORDER BY triggeredAt LIMIT 1")
    fun observeActive(): Flow<WakeEventEntity?>

    @Query("SELECT * FROM wake_events WHERE outcome IN ('RINGING', 'VERIFYING') ORDER BY triggeredAt LIMIT 1")
    suspend fun getActive(): WakeEventEntity?

    @Query("SELECT * FROM wake_events ORDER BY triggeredAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<WakeEventEntity>>

    @Insert
    suspend fun insert(event: WakeEventEntity): Long

    @Query("UPDATE wake_events SET outcome = 'VERIFYING', verificationStartedAt = COALESCE(verificationStartedAt, :now) WHERE id = :id AND outcome IN ('RINGING', 'VERIFYING')")
    suspend fun startVerification(id: Long, now: Long): Int

    @Query("UPDATE wake_events SET failedAttempts = failedAttempts + 1 WHERE id = :id AND outcome IN ('RINGING', 'VERIFYING')")
    suspend fun recordFailedAttempt(id: Long): Int

    @Query("UPDATE wake_events SET outcome = 'VERIFIED', finishedAt = :now WHERE id = :id AND outcome IN ('RINGING', 'VERIFYING')")
    suspend fun finishVerified(id: Long, now: Long): Int

    @Query("UPDATE wake_events SET outcome = 'EMERGENCY_STOP', finishedAt = :now WHERE id = :id AND outcome IN ('RINGING', 'VERIFYING')")
    suspend fun finishEmergency(id: Long, now: Long): Int

    @Query("UPDATE wake_events SET outcome = 'INTERRUPTED', finishedAt = :now WHERE outcome IN ('RINGING', 'VERIFYING')")
    suspend fun interruptActive(now: Long): Int
}
