package com.loongair.tripplanner

import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.remote.BundledChangfeiNetwork
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.LiveFlightSeat
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.account.isAllowedCaptchaUrl
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class SecondPassFixesTest {

    @Test
    fun multiCabinAggregation_sumsSecCardSeatsAcrossCabinsWithoutOverwrite() {
        val route = FlightRouteEntity(
            fromCity = "杭州",
            toCity = "郑州",
            fromIata = "HGH",
            toIata = "CGO",
            flightNo = "GJ8069",
            depTime = "06:50",
            arrTime = "08:40",
            duration = "1h50m",
            aircraft = "A319",
            frequency = "1357",
            stopover = "直飞",
            season = "official"
        )
        // 同一航班返回 3 个不同舱位（最后一个 Y 舱畅飞余票为 0），不得覆盖前两个可兑舱位
        val snapshots = listOf(
            SeatSnapshotEntity("杭州", "郑州", "2026-12-04", "GJ8069", "06:50", "08:40", "A319", "直飞", secCardLeft = 3, normalLeft = 4, redeemPrice = 260.0, cabinCode = "V", queriedAt = 100L),
            SeatSnapshotEntity("杭州", "郑州", "2026-12-04", "GJ8069", "06:50", "08:40", "A319", "直飞", secCardLeft = 2, normalLeft = 9, redeemPrice = 0.0, cabinCode = "T", queriedAt = 100L),
            SeatSnapshotEntity("杭州", "郑州", "2026-12-04", "GJ8069", "06:50", "08:40", "A319", "直飞", secCardLeft = 0, normalLeft = 7, redeemPrice = 580.0, cabinCode = "Y", queriedAt = 100L)
        )

        val merged = RoundTripMatcher.mergeWithLiveSeats(listOf(route), snapshots)
        assertEquals(1, merged.size)
        assertEquals(5, merged[0].liveSecCardLeft)
        assertEquals(9, merged[0].liveNormalLeft)
        assertEquals(260.0, merged[0].liveRedeemPrice ?: 0.0, 0.01)
    }

    @Test
    fun matchPlannedFlightSecLeft_sumsTargetFlightCabinsAndReturnsNullWhenCancelled() {
        val liveSeats = listOf(
            LiveFlightSeat("GJ8069", "06:50", "08:40", "1h50m", "A319", "直飞", 260.0, secCardLeft = 3, normalLeft = 5, cabinCode = "V"),
            LiveFlightSeat("GJ8069", "06:50", "08:40", "1h50m", "A319", "直飞", 580.0, secCardLeft = 2, normalLeft = 8, cabinCode = "M"),
            LiveFlightSeat("GJ8781", "14:00", "16:00", "2h00m", "A321", "直飞", 320.0, secCardLeft = 6, normalLeft = 9, cabinCode = "V")
        )

        assertEquals(5, RoundTripMatcher.matchPlannedFlightSecLeft(liveSeats, "GJ8069", "06:50"))
        // 当日停飞未匹配到的航班返回 null，绝不误把 GJ8781 的 6 张算到 GJ9999 上
        assertNull(RoundTripMatcher.matchPlannedFlightSecLeft(liveSeats, "GJ9999", "21:30"))
    }

    @Test
    fun captchaUrlAllowlist_permitsOnlyLocalAssetAndOfficialYidunHttps() {
        assertTrue(isAllowedCaptchaUrl("file:///android_asset/yidun_captcha.html"))
        assertTrue(isAllowedCaptchaUrl("https://cstaticdun.126.net/load.min.js"))
        assertTrue(isAllowedCaptchaUrl("https://acstatic-dun.126.net/api/v2/getconf"))
        assertTrue(isAllowedCaptchaUrl("https://dun.163.com/trial"))

        assertFalse(isAllowedCaptchaUrl("http://cstaticdun.126.net/load.min.js"))
        assertFalse(isAllowedCaptchaUrl("file:///sdcard/Download/payload.html"))
        assertFalse(isAllowedCaptchaUrl("file:///android_asset/../shared_prefs/auth.xml"))
        assertFalse(isAllowedCaptchaUrl("https://evil-126.net/phish"))
        assertFalse(isAllowedCaptchaUrl("javascript:alert(1)"))
        assertFalse(isAllowedCaptchaUrl(null))
    }

    @Test
    fun buildReminderSuggestions_usesBeijingZoneRegardlessOfSystemDefaultTimeZone() {
        val prevTz = TimeZone.getDefault()
        try {
            // 将系统默认时区临时切到 UTC，验证提醒时间仍严格锚定北京时间 09:55
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val plan = TripPlanEntity(
                fromCity = "杭州",
                toCity = "丽江",
                outboundDate = "2026-12-25",
                outboundFlightNo = "GJ8859",
                outboundDepTime = "07:15",
                outboundArrTime = "10:45",
                outboundStopover = "直飞",
                returnDate = "2026-12-27",
                returnFlightNo = "GJ8860",
                returnDepTime = "11:45",
                returnArrTime = "14:55",
                returnStopover = "直飞"
            )
            val suggestions = RoundTripMatcher.buildReminderSuggestions(plan, nowMillis = 1_700_000_000_000L)
            val expectedBeijingOpenMillis = ZonedDateTime.of(
                2026, 11, 25, 9, 55, 0, 0,
                ZoneId.of("Asia/Shanghai")
            ).toInstant().toEpochMilli()

            assertEquals(expectedBeijingOpenMillis, suggestions.first().triggerAtMillis)
        } finally {
            TimeZone.setDefault(prevTz)
        }
    }

    @Test
    fun threeCharacterChineseCities_resolveToAsciiIataInsteadOfChineseText() {
        // 回归验证：3 个汉字的城市名绝不可因 Char.isLetter() == true 被原样当成三字码返回
        assertEquals("HRB", CityIataMapper.toApiIata("哈尔滨"))
        assertEquals("DYG", CityIataMapper.toApiIata("张家界"))
        assertEquals("AKU", CityIataMapper.toApiIata("阿克苏"))
        assertEquals("AAT", CityIataMapper.toApiIata("阿勒泰"))
        assertEquals("ACF", CityIataMapper.toApiIata("阿拉尔"))
        assertEquals("SJW", CityIataMapper.toApiIata("石家庄"))
        assertEquals("HLD", CityIataMapper.toApiIata("海拉尔"))
        assertEquals("NBS", CityIataMapper.toApiIata("长白山"))
        assertEquals("NBS", CityIataMapper.toApiIata("白山"))
        assertEquals("BAR", CityIataMapper.toApiIata("博鳌"))
        assertEquals("HRB", CityIataMapper.toApiIata("hrb"))
        assertFalse(CityIataMapper.isAsciiIataCode("哈尔滨"))
        assertTrue(CityIataMapper.isAsciiIataCode("HRB"))
    }

    @Test
    fun dualPatternSampling_ignoresUnopenedMinusOneDatesAndPrioritizesAvailableStatus() {
        val base = LocalDate.of(2026, 11, 25) // 周三
        // 模拟一三五日航线（如杭州->东营）：近3天 (11-25..27) 与 30天外 (12-26 周六) 返回 -1 未开放，二四六返回 -4 无班
        val calDongying = mapOf(
            "2026-11-25" to -1,
            "2026-11-26" to -1,
            "2026-11-27" to -1,
            "2026-11-28" to -4, // 周六无班
            "2026-11-29" to 1,  // 周日可兑
            "2026-11-30" to 0,  // 周一兑完
            "2026-12-01" to -4, // 周二无班
            "2026-12-02" to 1,  // 周三可兑
            "2026-12-03" to -4, // 周四无班
            "2026-12-04" to 0,  // 周五兑完
            "2026-12-26" to -1  // 31天后周六未开放，绝不可误抽！
        )
        assertEquals("1357", RoundTripMatcher.deriveFrequencyFromCalendar(calDongying, base))
        val (oddDate, evenDate) = RoundTripMatcher.pickDualPatternSampleDates(calDongying, base)
        // 组内优先挑 status == 1（周三 12-02 或周日 11-29），绝不挑 status == 0 的周五 12-04；且二四六组为 null
        assertEquals("2026-12-02", oddDate)
        assertNull(evenDate)
    }

    @Test
    fun routeInvalidityDetection_prunesSuspendedRoutesWhilePreservingSoldOutAndUnopened() {
        val base = LocalDate.of(2026, 11, 25)
        // 换季停航航线（如威海->长春：27天全为 -4 无班，31天为 -1 未开放）-> 判定失效自动剔除
        val suspendedCal = buildMap {
            for (i in 0..2) put(base.plusDays(i.toLong()).toString(), -1)
            for (i in 3..29) put(base.plusDays(i.toLong()).toString(), -4)
            for (i in 30..57) put(base.plusDays(i.toLong()).toString(), -1)
        }
        assertTrue(RoundTripMatcher.isRouteInvalidByCalendar(suspendedCal))

        // 冬季热门全部售罄航线（如杭州->三亚：27天为 0 已兑完）-> 有效航线，绝不误删！
        val soldOutCal = buildMap {
            for (i in 0..2) put(base.plusDays(i.toLong()).toString(), -1)
            for (i in 3..29) put(base.plusDays(i.toLong()).toString(), 0)
            for (i in 30..57) put(base.plusDays(i.toLong()).toString(), -1)
        }
        assertFalse(RoundTripMatcher.isRouteInvalidByCalendar(soldOutCal))

        // 节假日/春运短时屏蔽窗（如 12 天 -2 不适用，其余为 -1 未开放）-> 有效航线，绝不误删为移出范围！
        val holidayBlackoutCal = buildMap {
            for (i in 0..11) put(base.plusDays(i.toLong()).toString(), -2)
            for (i in 12..35) put(base.plusDays(i.toLong()).toString(), -1)
        }
        assertFalse(RoundTripMatcher.isRouteInvalidByCalendar(holidayBlackoutCal))

        // 全评估周期全部被标 -2 不适用（如子卡产品不覆盖该航线）-> 移出范围剔除，并标注精确原因
        val fullInapplicableCal = (0..25).associate { base.plusDays(it.toLong()).toString() to -2 }
        assertTrue(RoundTripMatcher.isRouteInvalidByCalendar(fullInapplicableCal))
        assertTrue(RoundTripMatcher.resolveCalendarInvalidationReason(fullInapplicableCal).contains("超出当前畅飞卡适用范围"))
        assertTrue(RoundTripMatcher.resolveCalendarInvalidationReason(suspendedCal).contains("无执飞排班"))

        // 尚未开放评估的全 -1 日历 -> 不误删
        val unopenedCal = (0..30).associate { base.plusDays(it.toLong()).toString() to -1 }
        assertFalse(RoundTripMatcher.isRouteInvalidByCalendar(unopenedCal))

        assertTrue(RoundTripMatcher.isInapplicableRouteErrorMessage("当前次卡不适用该航线"))
        assertFalse(RoundTripMatcher.isInapplicableRouteErrorMessage("长龙会员会话已过期"))
        assertFalse(RoundTripMatcher.isInapplicableRouteErrorMessage("请求过于频繁，请稍后再试"))
    }

    @Test
    fun bundledChangfeiNetworkAsset_isCompleteSymmetricAndFullyMapped() {
        val candidates = listOf(
            File("src/main/assets/bundled_changfei_network.json"),
            File("app/src/main/assets/bundled_changfei_network.json")
        )
        val assetFile = candidates.firstOrNull { it.exists() }
        assertNotNull("bundled_changfei_network.json should exist", assetFile)
        val parsed = testJson.decodeFromString<BundledChangfeiNetwork>(assetFile!!.readText(Charsets.UTF_8))

        assertTrue(parsed.version >= 20261125)
        assertTrue(parsed.unavailableDates.containsAll(listOf("2027-01-22", "2027-01-23", "2027-01-24")))
        assertEquals(listOf("2027-01-22 ~ 2027-01-24"), RoundTripMatcher.formatDateRanges(parsed.unavailableDates))
        assertEquals(75, parsed.airports.size)
        assertEquals(410, parsed.routes.size)
        assertTrue(parsed.flights.size >= 440)

        // 校验全部 410 条航线严格对称、三字码合法、且每座城市都有地理坐标
        val pairSet = parsed.routes.map { it.fromCity to it.toCity }.toSet()
        assertEquals(parsed.routes.size, pairSet.size)
        for (r in parsed.routes) {
            assertTrue("Reverse of ${r.fromCity}->${r.toCity} must exist", (r.toCity to r.fromCity) in pairSet)
            assertTrue(CityIataMapper.isAsciiIataCode(r.fromIata))
            assertTrue(CityIataMapper.isAsciiIataCode(r.toIata))
            assertNotNull("Missing geoCoords for ${r.fromCity}", CityIataMapper.geoCoordsFor(r.fromCity))
            assertNotNull("Missing geoCoords for ${r.toCity}", CityIataMapper.geoCoordsFor(r.toCity))
        }

        // 校验全部航班时刻无未解码字符、无空班期
        for (f in parsed.flights) {
            assertFalse("Obfuscated flightNo: ${f.flightNo}", f.flightNo.contains("&"))
            assertTrue("Flight frequency must be non-empty: ${f.flightNo}", f.frequency.any { it in '1'..'7' })
        }
    }

    companion object {
        private val testJson = Json { ignoreUnknownKeys = true }
    }
}
