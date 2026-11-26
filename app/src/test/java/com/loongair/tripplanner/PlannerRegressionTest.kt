package com.loongair.tripplanner

import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.remote.LiveFlightSeat
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.data.repository.RoundTripQuery
import com.loongair.tripplanner.util.ChinaClock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PlannerRegressionTest {
    private val day = LocalDate.of(2026, 12, 1)
    private val query = RoundTripQuery("徐州", "哈尔滨", day, 0, 1439, day, 0, 1439)
    private val out = FlightRouteEntity(1, "徐州", "哈尔滨", "XUZ", "HRB", "GJ8787",
        "13:35", "16:10", "2h35m", "A320", "1234567", "直飞", "bundled")
    private val back = out.copy(fromCity = "哈尔滨", toCity = "徐州", fromIata = "HRB", toIata = "XUZ",
        flightNo = "GJ8788", depTime = "15:30", arrTime = "18:10")

    private fun match(
        search: RoundTripQuery = query,
        outbound: List<FlightRouteEntity> = listOf(out),
        returning: List<FlightRouteEntity> = listOf(back),
        seats: List<SeatSnapshotEntity> = emptyList()
    ) = RoundTripMatcher.matchRoundTrips(search, outbound, returning, emptyMap(), emptyMap(),
        mapOf("徐州|哈尔滨|${search.outboundDate}" to seats), emptyMap(), today = day.minusDays(6))

    @Test fun rejectsReturnBeforeArrivalAndChecksEachSelectedPair() {
        assertTrue(match().isEmpty())
        val early = out.copy(flightNo = "GJ8100", depTime = "08:00", arrTime = "10:40")
        val late = back.copy(flightNo = "GJ8101", depTime = "17:00")
        val candidate = match(outbound = listOf(out, early), returning = listOf(back, late)).single()
        val lateOut = candidate.outboundFlights.last()
        assertFalse(RoundTripMatcher.isFeasibleFlightPair(day, lateOut, day, candidate.returnFlights.first()))
        assertEquals(50L, RoundTripMatcher.stayMinutes(day, lateOut, day, candidate.returnFlights.last()))
        assertEquals(6L, candidate.maxStayHours)
    }

    @Test fun overnightArrivalUsesTheArrivalDayAndRejectsZeroMinuteConnection() {
        val redEye = out.copy(depTime = "22:00", arrTime = "01:10+1")
        val nextDay = query.copy(returnDate = day.plusDays(1))
        assertTrue(match(nextDay, listOf(redEye), listOf(back.copy(depTime = "01:10"))).isEmpty())
        val candidate = match(nextDay, listOf(redEye), listOf(back.copy(depTime = "01:30"))).single()
        assertEquals(20L, RoundTripMatcher.stayMinutes(day, candidate.outboundFlights.single(),
            nextDay.returnDate, candidate.returnFlights.single()))
    }

    @Test fun datedScheduleChangesTimeFrequencyAndStopoverBeforeFiltering() {
        val scheduled = out.copy(depTime = "14:00", arrTime = "16:00", frequency = "1")
        val observed = SeatSnapshotEntity("徐州", "哈尔滨", day.toString(), out.flightNo,
            "17:00", "19:00", "A320", "经停", 6, 8, 100.0, "V", 10L)
        val nextDay = query.copy(returnDate = day.plusDays(1))
        assertTrue(match(nextDay.copy(outEndMinute = 16 * 60), listOf(scheduled), seats = listOf(observed)).isEmpty())
        assertTrue(match(nextDay.copy(directOnly = true), listOf(scheduled), seats = listOf(observed)).isEmpty())
        val flight = match(nextDay, listOf(scheduled), seats = listOf(observed)).single().outboundFlights.single()
        assertEquals("17:00", flight.depTime)
        assertEquals("19:00", flight.arrTime)
        assertEquals(6, flight.liveSecCardLeft)
    }

    @Test fun matchingSeatsNeverBorrowAnotherNumberAtTheSameTime() {
        val other = LiveFlightSeat("GJ8781", "14:00", "16:00", "2h", "A320", "直飞", 100.0, 6, 8, "V")
        assertNull(RoundTripMatcher.matchPlannedFlightSecLeft(listOf(other), "GJ9999", "14:00"))
        assertEquals(6, RoundTripMatcher.matchPlannedFlightSecLeft(listOf(other), "GJ8781", "13:55"))
        assertNull(RoundTripMatcher.matchPlannedFlightSecLeft(listOf(other, other.copy(flightNo = "GJ8782")), "", "14:00"))
    }

    @Test fun weeklyScheduleKeepsDistinctDepartureAndArrivalVariantsForTheSameNumber() {
        val usual = LiveFlightSeat("GJ8123", "14:00", "16:00", "2h", "A320", "直飞", 100.0, 6, 8, "V")
        val later = usual.copy(depTime = "17:00", arrTime = "19:00")
        val longer = usual.copy(arrTime = "16:30")
        val samples = mapOf(1 to listOf(usual, usual.copy(cabinCode = "M")),
            2 to listOf(later), 3 to listOf(usual), 4 to listOf(longer))
        val schedules = RoundTripMatcher.aggregateWeeklyFlightSchedules(samples)
            .associate { (f, days) -> (f.depTime to f.arrTime) to days }
        assertEquals(mapOf(("14:00" to "16:00") to "13", ("17:00" to "19:00") to "2",
            ("14:00" to "16:30") to "4"), schedules)
    }

    @Test fun cutoffChangesAtExactly2340BeijingTime() {
        val deadline = day.minusDays(3).atTime(23, 40)
        assertEquals(1, RoundTripMatcher.normalizeCalendarStatus(1, day, deadline.minusSeconds(1)))
        assertEquals(-3, RoundTripMatcher.normalizeCalendarStatus(1, day, deadline))
        assertEquals(-3, RoundTripMatcher.normalizeCalendarStatus(1, day, deadline.plusMinutes(1)))
        assertEquals(-4, RoundTripMatcher.normalizeCalendarStatus(-4, day, deadline))
    }

    @Test fun recentCalendarExcludesExpiredCachedSeatsAndDatesBeyondTheFortiethDay() {
        val closing = day.plusDays(3).toString()
        val last = day.plusDays(39).toString()
        val calendar = mapOf(day.plusDays(40).toString() to 1, last to 1, closing to 0,
            day.minusDays(1).toString() to 1, day.plusDays(2).toString() to 1)
        val deadline = day.atTime(23, 40)
        val before = RoundTripMatcher.calendarWindow(calendar, deadline.minusSeconds(1), emptySet())
        assertEquals(1, before.availableDaysCount)
        assertEquals(1, before.soldOutDaysCount)
        val after = RoundTripMatcher.calendarWindow(calendar, deadline, emptySet())
        assertEquals(0, after.soldOutDaysCount)
        assertEquals(last, after.nearestAvailableDate)
        assertEquals(setOf(day.plusDays(2).toString(), closing, last), after.days.keys)
        assertEquals(-3, after.days[closing])
    }

    @Test fun nextMorningReminderUsesInjectedClockAndOmitsClosedLegs() {
        val plan = TripPlanEntity(fromCity = "徐州", toCity = "哈尔滨", outboundDate = day.toString(),
            outboundFlightNo = "GJ8787", outboundDepTime = "13:35", outboundArrTime = "16:10",
            outboundStopover = "直飞", returnDate = day.plusDays(5).toString(), returnFlightNo = "GJ8788",
            returnDepTime = "15:30", returnArrTime = "18:10", returnStopover = "直飞")
        fun suggestions(at: LocalDateTime) = RoundTripMatcher.buildReminderSuggestions(plan,
            at.atZone(ChinaClock.ZONE).toInstant().toEpochMilli()).filter { it.label.contains("明早") }
        val reminder = suggestions(day.minusDays(2).atTime(12, 0)).single()
        val expected = day.minusDays(1).atTime(9, 55).atZone(ChinaClock.ZONE).toInstant().toEpochMilli()
        assertEquals(expected, reminder.triggerAtMillis)
        assertTrue(reminder.message.contains("返程"))
        assertFalse(reminder.message.contains("去程"))
        assertTrue(suggestions(day.plusDays(5).atStartOfDay()).isEmpty())
    }
}
