package com.loongair.tripplanner

import com.loongair.tripplanner.alarm.PlanReminderTimes
import com.loongair.tripplanner.alarm.REMINDER_EARLY_TOLERANCE_MS
import com.loongair.tripplanner.alarm.ReminderTriggerDecision
import com.loongair.tripplanner.alarm.evaluateReminderTrigger
import com.loongair.tripplanner.data.local.ReminderEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PlanReminderTimesTest {
    private val plan = TripPlanEntity(
        fromCity = "北京首都", toCity = "杭州", outboundDate = "2026-12-25",
        outboundFlightNo = "GJ8888", outboundDepTime = "10:25", outboundArrTime = "12:35", outboundStopover = "直飞",
        returnDate = "2026-12-27", returnFlightNo = "GJ8987", returnDepTime = "16:10", returnArrTime = "18:35", returnStopover = "直飞"
    )

    @Test
    fun exportOnOpeningDateBefore0955UsesToday() {
        val early = LocalDateTime.parse("2026-11-25T09:00")
        assertEquals(early.withMinute(55), PlanReminderTimes.nextCalendarTime(plan, early))
        assertEquals(early.plusDays(1).withMinute(55), PlanReminderTimes.nextCalendarTime(plan, early.withHour(10)))
    }

    @Test
    fun returnLegCanStillBeRemindedAfterOutboundCloses() {
        val afterOutboundCutoff = LocalDateTime.parse("2026-12-22T23:40")
        assertEquals(LocalDateTime.parse("2026-12-23T09:55"), PlanReminderTimes.nextCalendarTime(plan, afterOutboundCutoff))
    }

    @Test
    fun pastLastBookingDeadlineIsNeverExportedOrScheduled() {
        val lastMinute = LocalDateTime.parse("2026-12-24T23:39")
        assertTrue(PlanReminderTimes.hasRedeemableLeg(plan, lastMinute))
        assertNull(PlanReminderTimes.nextCalendarTime(plan, lastMinute))
        assertFalse(PlanReminderTimes.hasRedeemableLeg(plan, lastMinute.plusMinutes(1)))
        assertNull(PlanReminderTimes.nextCalendarTime(plan, lastMinute.plusDays(5)))
    }

    @Test
    fun evaluateReminderTriggerHandlesToleranceAndReschedulesEarlyWakeups() {
        val targetAt = 1_700_000_000_000L
        val base = ReminderEntity(
            id = 1L,
            planId = 1L,
            triggerAtMillis = targetAt,
            title = "t",
            message = "m",
            label = "l",
            enabled = true,
            fired = false
        )
        assertEquals(ReminderTriggerDecision.SKIP, evaluateReminderTrigger(base.copy(enabled = false), targetAt))
        assertEquals(ReminderTriggerDecision.SKIP, evaluateReminderTrigger(base.copy(fired = true), targetAt))
        assertEquals(ReminderTriggerDecision.FIRE, evaluateReminderTrigger(base, targetAt))
        assertEquals(ReminderTriggerDecision.FIRE, evaluateReminderTrigger(base, targetAt + 1000L))
        assertEquals(ReminderTriggerDecision.FIRE, evaluateReminderTrigger(base, targetAt - REMINDER_EARLY_TOLERANCE_MS))
        assertEquals(
            ReminderTriggerDecision.RESCHEDULE,
            evaluateReminderTrigger(base, targetAt - REMINDER_EARLY_TOLERANCE_MS - 1L)
        )
    }
}
