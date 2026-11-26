package com.loongair.tripplanner

import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.repository.mergeSampledWeekdays
import com.loongair.tripplanner.data.repository.mergeSingleDayObservedSeats
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyScheduleMergeTest {
    private fun flight(no: String, dep: String, days: String) = FlightRouteEntity(
        fromCity = "杭州", toCity = "银川", fromIata = "HGH", toIata = "INC", flightNo = no,
        depTime = dep, arrTime = "19:00", duration = "", aircraft = "", frequency = days,
        stopover = "直飞", season = "bundled"
    )

    @Test
    fun partialWeekKeepsUnobservedDaysAndSeparatesChangedTimes() {
        val old = listOf(flight("GJ8001", "13:00", "12345"), flight("GJ8002", "15:00", "13"))
        val observed = listOf(flight("GJ8001", "16:00", "1"), flight("GJ8002", "15:00", "1"))
        val merged = mergeSampledWeekdays(old, observed, setOf(1))
        assertEquals(mapOf("13:00" to "2345", "15:00" to "13", "16:00" to "1"),
            merged.associate { it.depTime to it.frequency })
    }

    @Test
    fun aVerifiedWeekMayReplaceTheWholePreviousSchedule() {
        val observed = listOf(flight("GJ8002", "17:00", "246"))
        assertEquals(observed, mergeSampledWeekdays(listOf(flight("GJ8001", "07:00", "")),
            observed, (1..7).toSet()))
    }

    @Test
    fun singleDaySeatRefreshMatchesFlightTimeSlotWithoutCorruptingOtherDaysOrTimes() {
        val mondayUsual = flight("GJ8123", "14:00", "13")
        val tuesdayLater = flight("GJ8123", "17:00", "2")
        val otherFlight = flight("GJ8888", "09:00", "1")

        // 1. Observing GJ8123 at 14:00 on Monday keeps Tuesday's 17:00 slot untouched ("2") and preserves otherFlight.
        val afterUsual = mergeSingleDayObservedSeats(
            knownRoutes = listOf(mondayUsual, tuesdayLater, otherFlight),
            observed = listOf(flight("GJ8123", "14:00", "1")),
            dow = 1
        )
        assertEquals(
            mapOf(("GJ8123" to "14:00") to "13", ("GJ8123" to "17:00") to "2", ("GJ8888" to "09:00") to "1"),
            afterUsual.associate { (it.flightNo to it.depTime) to it.frequency }
        )

        // 2. Observing GJ8123 rescheduled to 17:00 on Monday removes '1' from 14:00 ("13"->"3") and adds '1' to 17:00.
        val afterReschedule = mergeSingleDayObservedSeats(
            knownRoutes = listOf(mondayUsual, tuesdayLater, otherFlight),
            observed = listOf(flight("GJ8123", "17:00", "1")),
            dow = 1
        )
        assertEquals(
            mapOf(("GJ8123" to "14:00") to "3", ("GJ8123" to "17:00") to "12", ("GJ8888" to "09:00") to "1"),
            afterReschedule.associate { (it.flightNo to it.depTime) to it.frequency }
        )

        // 3. If a single-day slot was Monday-only and GJ8123 is observed at a new time on Monday, drop the stale slot.
        val afterDropStale = mergeSingleDayObservedSeats(
            knownRoutes = listOf(flight("GJ8123", "14:00", "1")),
            observed = listOf(flight("GJ8123", "18:00", "1")),
            dow = 1
        )
        assertEquals(
            mapOf(("GJ8123" to "18:00") to "1"),
            afterDropStale.associate { (it.flightNo to it.depTime) to it.frequency }
        )
    }
}

