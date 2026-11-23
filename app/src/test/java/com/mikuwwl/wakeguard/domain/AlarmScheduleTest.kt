package com.mikuwwl.wakeguard.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmScheduleTest {
    private val auckland = ZoneId.of("Pacific/Auckland")

    @Test fun oneTimeAlarmUsesTodayWhenStillAhead() {
        val now = ZonedDateTime.of(2026, 11, 23, 6, 59, 0, 0, auckland).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 23, 7, 0, 0, 0, auckland).toInstant().toEpochMilli()
        assertEquals(expected, AlarmScheduleCalculator.nextTriggerAt(now, AlarmSchedule(7, 0, 0), auckland))
    }

    @Test fun oneTimeAlarmAtItsMinuteMovesToTomorrow() {
        val now = ZonedDateTime.of(2026, 11, 23, 7, 0, 0, 0, auckland).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 24, 7, 0, 0, 0, auckland).toInstant().toEpochMilli()
        assertEquals(expected, AlarmScheduleCalculator.nextTriggerAt(now, AlarmSchedule(7, 0, 0), auckland))
    }

    @Test fun weekdaysSkipWeekendAndCrossIntoMonday() {
        val friday = ZonedDateTime.of(2026, 11, 27, 8, 0, 0, 0, auckland).toInstant().toEpochMilli()
        val monday = ZonedDateTime.of(2026, 11, 30, 6, 30, 0, 0, auckland).toInstant().toEpochMilli()
        assertEquals(monday, AlarmScheduleCalculator.nextTriggerAt(friday, AlarmSchedule(6, 30, 0b0011111), auckland))
    }

    @Test fun selectedLocalHourFollowsTheProvidedTimeZone() {
        val now = Instant.parse("2026-11-22T17:00:00Z").toEpochMilli()
        val expected = ZonedDateTime.of(2026, 11, 23, 7, 15, 0, 0, auckland).toInstant().toEpochMilli()
        val actual = AlarmScheduleCalculator.nextTriggerAt(now, AlarmSchedule(7, 15, 127), auckland)
        assertEquals(expected, actual)
    }
}
