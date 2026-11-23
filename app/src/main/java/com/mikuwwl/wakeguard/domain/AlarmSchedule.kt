package com.mikuwwl.wakeguard.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class AlarmSchedule(
    val hour: Int,
    val minute: Int,
    /** Monday is bit 0, Sunday is bit 6. A zero mask means one time. */
    val repeatMask: Int,
) {
    init {
        require(hour in 0..23)
        require(minute in 0..59)
        require(repeatMask in 0..127)
    }
}

object AlarmScheduleCalculator {
    fun nextTriggerAt(nowMillis: Long, schedule: AlarmSchedule, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        for (daysAhead in 0..7) {
            val candidate = now.toLocalDate().plusDays(daysAhead.toLong())
                .atTime(schedule.hour, schedule.minute)
                .atZone(zone)
            if (candidate.isAfter(now) && isScheduled(candidate, schedule.repeatMask)) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        error("No matching alarm day found")
    }

    private fun isScheduled(candidate: ZonedDateTime, repeatMask: Int): Boolean {
        if (repeatMask == 0) return true
        val bit = 1 shl (candidate.dayOfWeek.value - 1)
        return repeatMask and bit != 0
    }
}
