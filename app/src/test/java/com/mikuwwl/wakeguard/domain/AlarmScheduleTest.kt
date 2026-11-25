package com.mikuwwl.wakeguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun springGapMovesNonexistentWallTimeForwardByTheGap() {
        val zone = ZoneId.of("America/New_York")
        val before = Instant.parse("2026-03-08T06:45:00Z").toEpochMilli()
        val expected = Instant.parse("2026-03-08T07:30:00Z").toEpochMilli()
        assertEquals(expected, AlarmScheduleCalculator.nextTriggerAt(before, AlarmSchedule(2, 30, 127), zone))
    }

    @Test fun fallOverlapUsesTheEarlierInstantOnlyOncePerDate() {
        val zone = ZoneId.of("America/New_York")
        val before = Instant.parse("2026-11-01T05:15:00Z").toEpochMilli()
        val between = Instant.parse("2026-11-01T05:45:00Z").toEpochMilli()
        val recurring = AlarmSchedule(1, 30, 127)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(), AlarmScheduleCalculator.nextTriggerAt(before, recurring, zone))
        assertEquals(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli(), AlarmScheduleCalculator.nextTriggerAt(between, recurring, zone))
    }

    @Test fun refusesOutOfRangeScheduleConfiguration() {
        assertThrows(IllegalArgumentException::class.java) { AlarmSchedule(24, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { AlarmSchedule(5, 60, 0) }
        assertThrows(IllegalArgumentException::class.java) { AlarmSchedule(5, 0, 128) }
    }
}
