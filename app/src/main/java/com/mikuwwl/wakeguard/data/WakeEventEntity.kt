package com.mikuwwl.wakeguard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wake_events", indices = [Index("triggeredAt"), Index("outcome")])
data class WakeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val triggeredAt: Long,
    val verificationStartedAt: Long? = null,
    val finishedAt: Long? = null,
    val outcome: String,
    val failedAttempts: Int = 0,
)

object WakeOutcome {
    const val RINGING = "RINGING"
    const val VERIFYING = "VERIFYING"
    const val VERIFIED = "VERIFIED"
    const val EMERGENCY_STOP = "EMERGENCY_STOP"
    const val INTERRUPTED = "INTERRUPTED"
}
