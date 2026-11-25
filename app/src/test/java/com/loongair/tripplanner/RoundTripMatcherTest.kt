package com.loongair.tripplanner

import com.loongair.tripplanner.data.local.CalendarCacheEntity
import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.local.TripTemplateEntity
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.LiveFlightSeat
import com.loongair.tripplanner.data.remote.LoongAirApi
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.data.repository.RoundTripQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RoundTripMatcherTest {

    @Test
    fun resolveTemplateDates_fridayEveningToSunday_andNextWeek() {
        // 2026-11-24 is Tuesday
        val tuesday = LocalDate.of(2026, 11, 24)
        val friSun = TripTemplateEntity(
            name = "周五晚 → 周日",
            outDayOfWeek = 5,
            outStartMinute = 18 * 60,
            outEndMinute = 23 * 60 + 59,
            retOffsetDays = 2,
            retStartMinute = 12 * 60,
            retEndMinute = 23 * 60 + 59,
            weekOffset = 0
        )
        val (out1, ret1) = RoundTripMatcher.resolveTemplateDates(friSun, tuesday)
        assertEquals(LocalDate.of(2026, 11, 27), out1)
        assertEquals(LocalDate.of(2026, 11, 29), ret1)

        val nextWeek = friSun.copy(weekOffset = 1)
        val (out2, ret2) = RoundTripMatcher.resolveTemplateDates(nextWeek, tuesday)
        assertEquals(LocalDate.of(2026, 12, 4), out2)
        assertEquals(LocalDate.of(2026, 12, 6), ret2)
    }

    @Test
    fun matchRoundTrips_filtersByTimeWindowFrequencyAndSortsByCalendarAvailability() {
        val friday = LocalDate.of(2026, 11, 27) // DayOfWeek = 5
        val sunday = LocalDate.of(2026, 11, 29) // DayOfWeek = 7

        val outbound = listOf(
            // 杭州->成都 周五晚直飞 (匹配)
            FlightRouteEntity(1, "杭州", "成都", "HGH", "CTU", "GJ8801", "19:30", "22:30", "3h", "A320", "1357", "直飞", ""),
            // 杭州->成都 周五早 (不在晚间时段)
            FlightRouteEntity(2, "杭州", "成都", "HGH", "CTU", "GJ8802", "08:00", "11:00", "3h", "A320", "1234567", "直飞", ""),
            // 杭州->广州 周五晚直飞 (匹配且日历可兑)
            FlightRouteEntity(3, "杭州", "广州", "HGH", "CAN", "GJ8901", "20:00", "22:20", "2h20m", "A321", "1234567", "直飞", ""),
            // 杭州->三亚 周二四六执飞 (周五不飞，应过滤)
            FlightRouteEntity(4, "杭州", "三亚", "HGH", "SYX", "GJ8701", "19:00", "22:00", "3h", "A320", "246", "直飞", "")
        )
        val inbound = listOf(
            FlightRouteEntity(5, "成都", "杭州", "CTU", "HGH", "GJ8806", "18:00", "20:50", "2h50m", "A320", "1234567", "直飞", ""),
            FlightRouteEntity(6, "广州", "杭州", "CAN", "HGH", "GJ8906", "17:00", "19:15", "2h15m", "A321", "1234567", "直飞", ""),
            FlightRouteEntity(7, "三亚", "杭州", "SYX", "HGH", "GJ8706", "16:00", "19:00", "3h", "A320", "1234567", "直飞", "")
        )

        val outCals = mapOf(
            "广州" to CalendarCacheEntity("杭州", "广州", friday.toString(), 1, 0L)
        )
        val inCals = mapOf(
            "广州" to CalendarCacheEntity("广州", "杭州", sunday.toString(), 1, 0L)
        )

        val results = RoundTripMatcher.matchRoundTrips(
            query = RoundTripQuery(
                originCity = "杭州",
                outboundDate = friday,
                outStartMinute = 18 * 60,
                outEndMinute = 23 * 60 + 59,
                returnDate = sunday,
                retStartMinute = 12 * 60,
                retEndMinute = 23 * 60 + 59
            ),
            allOutboundRoutes = outbound,
            allInboundRoutes = inbound,
            outboundCalendars = outCals,
            inboundCalendars = inCals,
            seatOverlay = emptyMap(),
            cityMetaMap = emptyMap(),
            today = LocalDate.of(2026, 11, 24)
        )

        assertEquals(2, results.size)
        // 广州去返均日历可兑，应排第 1
        assertEquals("广州", results[0].destinationCity)
        assertTrue(results[0].isBothCalendarAvailable)
        // 成都只应包含 19:30 的晚班，不含 08:00 的早班
        assertEquals("成都", results[1].destinationCity)
        assertEquals(1, results[1].outboundFlights.size)
        assertEquals("GJ8801", results[1].outboundFlights[0].flightNo)
        assertTrue(results[1].maxStayHours in 40..46)

        // 当日期进入起飞前 3 天内（如 today=2026-11-25 时搜索 2026-11-27），原始 -1 或过期缓存均应归一化为 -3（已截止）
        val cutoffResults = RoundTripMatcher.matchRoundTrips(
            query = RoundTripQuery(
                originCity = "杭州",
                outboundDate = friday,
                outStartMinute = 18 * 60,
                outEndMinute = 23 * 60 + 59,
                returnDate = sunday,
                retStartMinute = 12 * 60,
                retEndMinute = 23 * 60 + 59
            ),
            allOutboundRoutes = outbound,
            allInboundRoutes = inbound,
            outboundCalendars = mapOf(
                "广州" to CalendarCacheEntity("杭州", "广州", friday.toString(), -1, 0L),
                "成都" to CalendarCacheEntity("杭州", "成都", friday.toString(), 1, 0L)
            ),
            inboundCalendars = inCals,
            seatOverlay = emptyMap(),
            cityMetaMap = emptyMap(),
            today = LocalDate.of(2026, 11, 25)
        )
        assertEquals(-3, cutoffResults.first { it.destinationCity == "广州" }.outCalendarStatus)
        assertEquals(-3, cutoffResults.first { it.destinationCity == "成都" }.outCalendarStatus)
        // 周日 2026-11-29 距 2026-11-25 为 4 天（>= 3天），保持原始状态 1
        assertEquals(1, cutoffResults.first { it.destinationCity == "广州" }.retCalendarStatus)
    }

    @Test
    fun buildReminderSuggestions_generates30DayOpenAndCutoffAlerts() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = LocalDateTime.of(2026, 11, 24, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val plan = TripPlanEntity(
            id = 1,
            fromCity = "杭州",
            toCity = "成都",
            outboundDate = "2027-01-15",
            outboundFlightNo = "GJ8801",
            outboundDepTime = "19:30",
            outboundArrTime = "22:30",
            outboundStopover = "直飞",
            returnDate = "2027-01-17",
            returnFlightNo = "GJ8806",
            returnDepTime = "18:00",
            returnArrTime = "20:50",
            returnStopover = "直飞"
        )
        val suggestions = RoundTripMatcher.buildReminderSuggestions(plan, nowMillis = now, zoneId = zone)
        assertTrue(suggestions.size >= 3)
        val outOpen = suggestions.first { it.label == "去程开抢前5分钟" }
        assertFalse(outOpen.isInPast)
        assertTrue(outOpen.description.contains("2026-12-16 09:55"))
        val retOpen = suggestions.first { it.label == "返程开抢前5分钟" }
        assertTrue(retOpen.description.contains("2026-12-18 09:55"))
    }

    @Test
    fun formatChineseFrequency_formatsDailyAndWeekdays() {
        assertEquals("每日", RoundTripMatcher.formatChineseFrequency("1234567"))
        assertEquals("待校准", RoundTripMatcher.formatChineseFrequency(""))
        assertEquals("一三五日", RoundTripMatcher.formatChineseFrequency("1357"))
        assertEquals("二四六", RoundTripMatcher.formatChineseFrequency("246"))
        assertEquals("一二四六", RoundTripMatcher.formatChineseFrequency("1246"))
    }

    @Test
    fun multiDayAndAnyTemplates_resolveAllMatchingDatePairs() {
        val tuesday = LocalDate.of(2026, 11, 24)
        val multiTpl = TripTemplateEntity(
            name = "周四/五/六去",
            outDayOfWeek = 4,
            outDaysOfWeek = "456",
            outStartMinute = 18 * 60,
            outEndMinute = 23 * 60 + 59,
            retOffsetDays = 2,
            retStartMinute = 12 * 60,
            retEndMinute = 23 * 60 + 59
        )
        val multiPairs = RoundTripMatcher.resolveTemplateDatePairs(multiTpl, tuesday)
        assertEquals(3, multiPairs.size)
        assertEquals(LocalDate.of(2026, 11, 26), multiPairs[0].first)
        assertEquals(LocalDate.of(2026, 11, 27), multiPairs[1].first)
        assertEquals(LocalDate.of(2026, 11, 28), multiPairs[2].first)
        assertEquals("周四、五、六", RoundTripMatcher.formatTemplateOutDaysLabel(multiTpl))

        val anyTpl = multiTpl.copy(name = "任意日", outDayOfWeek = 0, outDaysOfWeek = "ANY")
        val anyPairs = RoundTripMatcher.resolveTemplateDatePairs(anyTpl, tuesday)
        assertEquals(7, anyPairs.size)
        assertEquals(LocalDate.of(2026, 11, 25), anyPairs.first().first)
        assertEquals("任意", RoundTripMatcher.formatTemplateOutDaysLabel(anyTpl))
    }

    @Test
    fun aggregateWeeklyFlightSchedules_splitsDifferentFlightsAcrossWeekdays() {
        val f1357 = LiveFlightSeat("GJ8124", "15:00", "21:30", "6h30m", "A320", "经停武汉", 0.0, 2, 5, "M")
        val f246 = LiveFlightSeat("GJ8148", "15:00", "21:15", "6h15m", "A320", "经停宁波", 0.0, 1, 4, "M")
        val byDow = mapOf(
            1 to listOf(f1357),
            2 to listOf(f246),
            3 to listOf(f1357),
            4 to listOf(f246),
            5 to listOf(f1357),
            6 to listOf(f246),
            7 to listOf(f1357)
        )
        val aggregated = RoundTripMatcher.aggregateWeeklyFlightSchedules(byDow).associate { it.first.flightNo to it.second }
        assertEquals("1357", aggregated["GJ8124"])
        assertEquals("246", aggregated["GJ8148"])
        assertEquals("一三五日", RoundTripMatcher.formatChineseFrequency(aggregated["GJ8124"]!!))
        assertEquals("二四六", RoundTripMatcher.formatChineseFrequency(aggregated["GJ8148"]!!))
    }

    @Test
    fun dualAirportCities_stayDistinctByCode() {
        assertEquals("成都天府", CityIataMapper.disambiguateCityName("成都", "TFU"))
        assertEquals("成都双流", CityIataMapper.disambiguateCityName("成都", "CTU"))
        assertEquals("北京首都", CityIataMapper.disambiguateCityName("北京", "PEK"))
        assertEquals("北京大兴", CityIataMapper.disambiguateCityName("北京", "PKX"))
        assertEquals("上海浦东", CityIataMapper.disambiguateCityName("上海", "PVG"))
        assertEquals("上海虹桥", CityIataMapper.disambiguateCityName("上海", "SHA"))
        assertEquals("遵义新舟", CityIataMapper.disambiguateCityName("遵义", "ZYI"))
        assertEquals("遵义茅台", CityIataMapper.disambiguateCityName("遵义", "WMT"))
        assertEquals("TFU", CityIataMapper.toApiIata("成都天府"))
        assertEquals("CTU", CityIataMapper.toApiIata("成都双流"))
    }

    @Test
    fun deriveFrequencyFromCalendar_extractsOperatingWeekdays() {
        // 2026-11-23 is Monday (1)
        val base = LocalDate.of(2026, 11, 23)
        val cal = mutableMapOf<String, Int>()
        for (i in 0..27) {
            val d = base.plusDays(i.toLong())
            val dow = d.dayOfWeek.value
            // Mon/Wed/Fri/Sun operate (status 1 or 0), Tue/Thu/Sat have no flight (-4)
            cal[d.toString()] = if (dow in setOf(1, 3, 5, 7)) {
                if (dow == 5) 1 else 0
            } else {
                -4
            }
        }
        assertEquals("1357", RoundTripMatcher.deriveFrequencyFromCalendar(cal, baseDate = base))
        assertEquals("一三五日", RoundTripMatcher.formatChineseFrequency(RoundTripMatcher.deriveFrequencyFromCalendar(cal, baseDate = base)))
    }

    @Test
    fun pickBestSampleDate_prioritizesAvailableThenSoldOutWithin30Days() {
        val base = LocalDate.of(2026, 11, 24)
        val cal1 = mapOf(
            "2026-11-25" to -4,
            "2026-11-26" to 0,
            "2026-11-28" to 1,
            "2026-12-01" to -1
        )
        assertEquals("2026-11-28", RoundTripMatcher.pickBestSampleDate(cal1, baseDate = base))

        val cal2 = mapOf(
            "2026-11-25" to -4,
            "2026-11-26" to -2,
            "2026-11-27" to 0,
            "2026-12-01" to -1
        )
        assertEquals("2026-11-27", RoundTripMatcher.pickBestSampleDate(cal2, baseDate = base))

        val calNoFlights = mapOf(
            "2026-11-25" to -4,
            "2026-11-26" to -2
        )
        assertEquals(null, RoundTripMatcher.pickBestSampleDate(calNoFlights, baseDate = base))
    }

    @Test
    fun decodeFlightNo_decodesLoongAirDpObfuscationAndPlainStrings() {
        // Real gateway responses observed from secCardSearchFlights
        assertEquals("GJ8859", LoongAirApi.decodeFlightNo("168&248&250&306&344&425"))
        assertEquals("GJ8859", LoongAirApi.decodeFlightNo("168&248&250&306&344&425", floatPrice = 19))
        assertEquals("GJ8700", LoongAirApi.decodeFlightNo("195&275&277&328&341&389"))
        assertEquals("GJ8700", LoongAirApi.decodeFlightNo("195&275&277&328&341&389", floatPrice = 46))
        // Plain valid flight numbers stay intact
        assertEquals("GJ8995", LoongAirApi.decodeFlightNo("GJ8995"))
        // Unparseable garbage returns null (never shown raw)
        assertEquals(null, LoongAirApi.decodeFlightNo("12&34&abc"))
    }

    @Test
    fun cityIataMapper_resolvesPinyinIataAndChinese() {
        assertEquals("杭州", CityIataMapper.toCityName("hangzhou"))
        assertEquals("杭州", CityIataMapper.toCityName("HGH"))
        assertEquals("HGH", CityIataMapper.toApiIata("hangzhou"))
        assertEquals("CAN", CityIataMapper.toApiIata("guangzhou"))
    }
}
