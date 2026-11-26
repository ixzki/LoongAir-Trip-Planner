package com.loongair.tripplanner.alarm

import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal object PlanReminderTimes {
    private fun flightDates(plan: TripPlanEntity) =
        listOf(plan.outboundDate, plan.returnDate).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun hasRedeemableLeg(plan: TripPlanEntity, at: LocalDateTime): Boolean =
        flightDates(plan).any { !RoundTripMatcher.isRedemptionClosed(it, at) }

    fun nextCalendarTime(plan: TripPlanEntity, now: LocalDateTime): LocalDateTime? {
        val reminderTime = LocalTime.of(9, 55)
        val todayMorning = now.toLocalDate().atTime(reminderTime)
        val nextMorning = if (todayMorning.isAfter(now)) todayMorning else todayMorning.plusDays(1)
        return flightDates(plan).mapNotNull { flightDate ->
            val opening = flightDate.minusDays(30).atTime(reminderTime)
            val candidate = maxOf(opening, nextMorning)
            candidate.takeUnless { RoundTripMatcher.isRedemptionClosed(flightDate, it) }
        }.minOrNull()
    }
}
