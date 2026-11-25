package com.loongair.tripplanner

import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.remote.LiveFlightSeat
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.account.isAllowedCaptchaUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
