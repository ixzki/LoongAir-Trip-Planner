package com.loongair.tripplanner.data.repository

import com.loongair.tripplanner.data.local.CalendarCacheEntity
import com.loongair.tripplanner.data.local.FlightRouteEntity
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.local.TripTemplateEntity
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.data.remote.FlightNoDecoder
import com.loongair.tripplanner.data.remote.LiveFlightSeat
import com.loongair.tripplanner.util.ChinaClock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class RoundTripQuery(
    val originCity: String = "",
    val destinationFilter: String = "",       // 空字符串表示全国全部可达目的地
    val outboundDate: LocalDate,
    val outStartMinute: Int = 18 * 60,        // 默认 18:00
    val outEndMinute: Int = 23 * 60 + 59,     // 默认 23:59
    val returnDate: LocalDate,
    val retStartMinute: Int = 12 * 60,        // 默认 12:00
    val retEndMinute: Int = 23 * 60 + 59,     // 默认 23:59
    val directOnly: Boolean = false,
    val onlyCalendarAvailable: Boolean = false,
    val datePairs: List<Pair<LocalDate, LocalDate>> = emptyList()
)

data class FlightCandidate(
    val flightNo: String,
    val depTime: String,
    val arrTime: String,
    val duration: String,
    val aircraft: String,
    val stopover: String,
    val frequency: String,
    val liveSecCardLeft: Int? = null,
    val liveNormalLeft: Int? = null,
    val liveRedeemPrice: Double? = null,
    val liveQueriedAt: Long? = null
) {
    val isDirect: Boolean get() = stopover.isBlank() || stopover == "直飞"
}

data class RoundTripCandidate(
    val originCity: String,
    val destinationCity: String,
    val cityMeta: CityMeta?,
    val outboundDate: LocalDate,
    val returnDate: LocalDate,
    val outboundFlights: List<FlightCandidate>,
    val returnFlights: List<FlightCandidate>,
    val outCalendarStatus: Int?,    // 1=可兑, 0=已兑完, -1=未开放, -2=不适用, -3=已截止, -4=无航班, null=未查
    val retCalendarStatus: Int?,
    val maxStayHours: Long
) {
    val isBothCalendarAvailable: Boolean
        get() = outCalendarStatus == 1 && retCalendarStatus == 1

    val hasAnyUnavailableCalendar: Boolean
        get() = (outCalendarStatus != null && outCalendarStatus != 1) ||
            (retCalendarStatus != null && retCalendarStatus != 1)

    val outMaxSeats: Int?
        get() = outboundFlights.mapNotNull { it.liveSecCardLeft }.maxOrNull()

    val retMaxSeats: Int?
        get() = returnFlights.mapNotNull { it.liveSecCardLeft }.maxOrNull()
}

data class ReminderSuggestion(
    val label: String,
    val description: String,
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
    val isInPast: Boolean
)

object RoundTripMatcher {

    fun parseMinutes(hhmm: String): Int? {
        val clean = hhmm.trim().take(5)
        val parts = clean.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatMinutes(minutes: Int): String {
        val clamped = minutes.coerceIn(0, 23 * 60 + 59)
        return "%02d:%02d".format(clamped / 60, clamped % 60)
    }

    fun dayOfWeekLabel(dow: Int): String = when (dow) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周$dow"
    }

    /**
     * 将班期数字串转为中文执飞星期，例如 "1234567" -> "每日", "1357" -> "一三五日", "246" -> "二四六"
     * 空串表示尚未完成逐日校准，不再误标为「每日」。
     */
    fun formatChineseFrequency(frequency: String): String {
        val digits = frequency.filter { it in '1'..'7' }.toSet().sorted()
        if (digits.isEmpty()) return "待校准"
        if (digits.size == 7) return "每日"
        val map = mapOf(
            '1' to '一', '2' to '二', '3' to '三', '4' to '四',
            '5' to '五', '6' to '六', '7' to '日'
        )
        return digits.mapNotNull { map[it] }.joinToString("")
    }

    /**
     * 从 60 天放票日历为每个有班次的星期几 (1=周一..7=周日) 各挑一个最佳样本日，用于采样校准逐航班执飞星期。
     * 注意：长龙日历接口对「距今不足 3 天」及「30 天以外未开放窗口」统一返回 status = -1（无论当日是否有航班），
     * 且对 status = -1 / -3 日期调用 secCardSearchFlights 会直接报错失败；因此仅采集已实际评估排班的 status == 1 或 0！
     */
    fun pickWeeklySampleDates(
        calMap: Map<String, Int>,
        baseDate: LocalDate = ChinaClock.today()
    ): Map<Int, String> {
        val parsed = calMap.entries
            .mapNotNull { (k, v) ->
                if (v != 1 && v != 0) return@mapNotNull null
                val d = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@mapNotNull null
                if (d.isBefore(baseDate)) null else Triple(d, k, v)
            }
            .sortedBy { it.first }
        if (parsed.isEmpty()) return emptyMap()

        val result = linkedMapOf<Int, String>()
        for (dow in 1..7) {
            val allForDow = parsed.filter { it.first.dayOfWeek.value == dow }
            if (allForDow.isEmpty()) continue
            val in30 = allForDow.filter { !it.first.isAfter(baseDate.plusDays(30)) }
            val pool = in30.ifEmpty { allForDow }
            val best = pool.firstOrNull { it.third == 1 }?.second
                ?: pool.firstOrNull { it.third == 0 }?.second
                ?: pool.first().second
            result[dow] = best
        }
        return result
    }

    /**
     * 快速双模式采样：从「一三五日」与「二四六」两组中各挑 1 个最佳样本日，用于初始同步时快速区分隔日班次。
     * 注意：长龙 secCardSearchFlights 接口在当日名额已兑完 (status == 0) 时可能返回空列表，
     * 因此组内必须优先挑选 status == 1（有余票可兑）的星期样本，避免明明有可兑日却误抽中已兑完日导致班次为空。
     */
    fun pickDualPatternSampleDates(
        calMap: Map<String, Int>,
        baseDate: LocalDate = ChinaClock.today()
    ): Pair<String?, String?> {
        val byDow = pickWeeklySampleDates(calMap, baseDate)
        val oddOrder = listOf(5, 1, 3, 7)
        val evenOrder = listOf(6, 2, 4)
        val oddDate = oddOrder.firstNotNullOfOrNull { dow -> byDow[dow]?.takeIf { calMap[it] == 1 } }
            ?: oddOrder.firstNotNullOfOrNull { byDow[it] }
        val evenDate = evenOrder.firstNotNullOfOrNull { dow -> byDow[dow]?.takeIf { calMap[it] == 1 } }
            ?: evenOrder.firstNotNullOfOrNull { byDow[it] }
        return oddDate to evenDate
    }

    /**
     * 将周一至周日逐日采样得到的实时航班按航班号聚合并算出每个航班的精确执飞星期串（如 "1357" / "246" / "1234567"）
     */
    fun aggregateWeeklyFlightSchedules(
        flightsByDow: Map<Int, List<LiveFlightSeat>>
    ): List<Pair<LiveFlightSeat, String>> {
        val byKey = linkedMapOf<String, Pair<LiveFlightSeat, MutableSet<Int>>>()
        for ((dow, seats) in flightsByDow) {
            val deduped = seats.distinctBy { "${it.flightNo}|${it.depTime}" }
            for (seat in deduped) {
                val key = seat.flightNo.ifBlank { seat.depTime }
                val existing = byKey[key]
                if (existing == null) {
                    byKey[key] = seat to sortedSetOf(dow)
                } else {
                    existing.second.add(dow)
                }
            }
        }
        return byKey.values
            .map { (seat, dows) -> seat to dows.sorted().joinToString("") }
            .sortedBy { it.first.depTime }
    }

    /**
     * 从长龙 60 天放票日历反推真实每周执飞班期（例如 "1234567" / "1357" / "246"）。
     * 仅统计已开放评估窗口内的可兑 (1) 与已兑完 (0)：因为近 3 天截止期与 30 天外未开放期均固定返回 -1，不代表当日有航班。
     */
    fun deriveFrequencyFromCalendar(
        calMap: Map<String, Int>,
        baseDate: LocalDate = ChinaClock.today()
    ): String {
        if (calMap.isEmpty()) return ""
        val maxDate = baseDate.plusDays(32)
        val operatingDows = sortedSetOf<Int>()
        for ((dateStr, status) in calMap) {
            if (status != 1 && status != 0) continue
            val d = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            if (!d.isBefore(baseDate) && !d.isAfter(maxDate)) {
                operatingDows.add(d.dayOfWeek.value)
            }
        }
        return if (operatingDows.isEmpty()) "" else operatingDows.joinToString("")
    }

    /**
     * 从 60 天放票日历挑选最适合调用 secCardSearchFlights 拉取实时班次时刻的样本日期：
     * 仅选开放兑换窗口内的可兑 (1) 或已兑完 (0)，避免对未开放 (-1) 日期发起必定报错的请求。
     */
    fun pickBestSampleDate(
        calMap: Map<String, Int>,
        baseDate: LocalDate = ChinaClock.today()
    ): String? {
        val entries = calMap.entries
            .mapNotNull { (k, v) ->
                if (v != 1 && v != 0) return@mapNotNull null
                val d = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@mapNotNull null
                if (d.isBefore(baseDate)) null else Triple(d, k, v)
            }
            .sortedBy { it.first }
        if (entries.isEmpty()) return null
        val inWindow = entries.filter { !it.first.isAfter(baseDate.plusDays(30)) }
        val pool = inWindow.ifEmpty { entries }
        return pool.firstOrNull { it.third == 1 }?.second
            ?: pool.firstOrNull { it.third == 0 }?.second
    }

    /**
     * 长龙畅飞卡官方适用航班日期区间（2026/2027 整年有效窗口）：
     * - 2026-10-25 ~ 2027-01-21
     * - 2027-03-03 ~ 2027-04-27
     * - 2027-05-07 ~ 2027-06-30
     * - 2027-09-01 ~ 2027-09-26
     * - 2027-10-11 ~ 2027-10-30
     */
    val OFFICIAL_VALID_DATE_WINDOWS: List<Pair<LocalDate, LocalDate>> = listOf(
        LocalDate.of(2026, 10, 25) to LocalDate.of(2027, 1, 21),
        LocalDate.of(2027, 3, 3) to LocalDate.of(2027, 4, 27),
        LocalDate.of(2027, 5, 7) to LocalDate.of(2027, 6, 30),
        LocalDate.of(2027, 9, 1) to LocalDate.of(2027, 9, 26),
        LocalDate.of(2027, 10, 11) to LocalDate.of(2027, 10, 30)
    )

    val OFFICIAL_CARD_END_DATE: LocalDate = LocalDate.of(2027, 10, 30)

    fun isWithinOfficialChangfeiDates(date: LocalDate): Boolean =
        OFFICIAL_VALID_DATE_WINDOWS.any { (start, end) -> !date.isBefore(start) && !date.isAfter(end) }

    fun officialBlackoutDates(fromDate: LocalDate = LocalDate.of(2026, 10, 25)): Set<String> {
        val out = sortedSetOf<String>()
        var cur = if (fromDate.isBefore(LocalDate.of(2026, 10, 25))) LocalDate.of(2026, 10, 25) else fromDate
        while (!cur.isAfter(OFFICIAL_CARD_END_DATE)) {
            if (!isWithinOfficialChangfeiDates(cur)) {
                out.add(cur.toString())
            }
            cur = cur.plusDays(1)
        }
        return out
    }

    /**
     * 判断 60 天放票日历是否证明该航线已停航或移出当前畅飞卡适用范围：
     * - 只要出现任意一天「可兑 (1)」或「已兑完/约满 (0)」，说明该航线仍在畅飞卡范围内（仅名额紧俏），绝不误判为失效；
     * - 自动排除官方已知全局屏蔽/春运暑运等不可用日期（excludedGlobalDates），绝不因整卡进入春运/暑运而误删航线；
     * - 仅在剩余日期满足「明确无航班 (-4 >= 14)」或「在适用窗口内明确长期不适用 (-2 >= 20 且 -4 + -2 >= 20)」时才判定失效。
     */
    fun isRouteInvalidByCalendar(
        calMap: Map<String, Int>,
        excludedGlobalDates: Set<String> = officialBlackoutDates()
    ): Boolean {
        if (calMap.size < 14) return false
        if (calMap.values.any { it == 1 || it == 0 }) return false
        val effectiveEntries = calMap.filterKeys { it !in excludedGlobalDates }
        if (effectiveEntries.size < 14) return false
        val noFlightDays = effectiveEntries.values.count { it == -4 }
        val inapplicableDays = effectiveEntries.values.count { it == -2 }
        return noFlightDays >= 14 || (inapplicableDays >= 20 && noFlightDays + inapplicableDays >= 20)
    }

    /**
     * 区分具体失效原因文案内涵：区分「全月停航无班」与「超出当前畅飞卡适用范围」。
     */
    fun resolveCalendarInvalidationReason(
        calMap: Map<String, Int>,
        excludedGlobalDates: Set<String> = officialBlackoutDates()
    ): String {
        val effectiveEntries = calMap.filterKeys { it !in excludedGlobalDates }
        val noFlightDays = effectiveEntries.values.count { it == -4 }
        val inapplicableDays = effectiveEntries.values.count { it == -2 }
        return when {
            inapplicableDays > noFlightDays && inapplicableDays >= 20 -> "超出当前畅飞卡适用范围或全期屏蔽 (-2)"
            noFlightDays >= 14 -> "当前航季连续多周无执飞排班 (-4)"
            else -> "已移出畅飞卡可用排班或停航"
        }
    }

    /**
     * 将零散 YYYY-MM-DD 日期合并为连续区间文本（例如 ["2027-01-22","2027-01-23","2027-01-24"] -> ["2027-01-22 ~ 2027-01-24"]）。
     */
    fun formatDateRanges(dates: Collection<String>): List<String> {
        val parsed = dates.mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            .distinct()
            .sorted()
        if (parsed.isEmpty()) return emptyList()
        val ranges = mutableListOf<String>()
        var start = parsed.first()
        var prev = start
        for (i in 1 until parsed.size) {
            val cur = parsed[i]
            if (cur == prev.plusDays(1)) {
                prev = cur
            } else {
                ranges.add(if (start == prev) start.toString() else "$start ~ $prev")
                start = cur
                prev = cur
            }
        }
        ranges.add(if (start == prev) start.toString() else "$start ~ $prev")
        return ranges
    }

    /**
     * 判断长龙接口业务报错文案是否明确指出该航线已取消或不在当前畅飞卡适用航线池内
     * （绝不误匹配网络波动、限流或登录过期错误）。
     */
    fun isInapplicableRouteErrorMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val keywords = listOf("不适用", "不支持该航线", "不在适用", "无此航线", "航线不存在", "不可兑换该航线")
        return keywords.any { message.contains(it) }
    }

    fun matchesDayOfWeek(frequency: String, date: LocalDate): Boolean {
        val clean = frequency.trim()
        if (clean.isEmpty()) return true
        val dowChar = date.dayOfWeek.value.toString()
        return clean.contains(dowChar)
    }

    fun isInTimeWindow(depTime: String, startMinute: Int, endMinute: Int): Boolean {
        val min = parseMinutes(depTime) ?: return false
        return min in startMinute..endMinute
    }

    /**
     * 解析模板的多选去程星期几；空集合表示「任意」
     */
    fun parseTemplateOutDays(template: TripTemplateEntity): Set<Int> {
        val raw = template.outDaysOfWeek.trim().uppercase()
        if (raw == "ANY") return emptySet()
        if (raw.isNotEmpty()) {
            val set = raw.filter { it in '1'..'7' }.map { it - '0' }.toSortedSet()
            return if (set.size >= 7) emptySet() else set
        }
        return if (template.outDayOfWeek in 1..7) setOf(template.outDayOfWeek) else emptySet()
    }

    fun isAnyDayTemplate(template: TripTemplateEntity): Boolean =
        parseTemplateOutDays(template).isEmpty()

    fun formatTemplateOutDaysLabel(days: Set<Int>, isAny: Boolean = days.isEmpty()): String {
        if (isAny || days.isEmpty() || days.size >= 7) return "任意"
        if (days.size == 1) return dayOfWeekLabel(days.first())
        val shortMap = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日")
        return "周" + days.sorted().mapNotNull { shortMap[it] }.joinToString("、")
    }

    fun formatTemplateOutDaysLabel(template: TripTemplateEntity): String {
        val days = parseTemplateOutDays(template)
        return formatTemplateOutDaysLabel(days, isAny = days.isEmpty())
    }

    /**
     * 根据用户模板推算本次周期内所有匹配的 (去程日期, 返程日期) 列表（支持单选、多选与「任意」）
     */
    fun resolveTemplateDatePairs(
        template: TripTemplateEntity,
        baseDate: LocalDate = ChinaClock.today()
    ): List<Pair<LocalDate, LocalDate>> {
        val days = parseTemplateOutDays(template)
        val retOffset = template.retOffsetDays.coerceAtLeast(0).toLong()
        val weekShift = template.weekOffset.coerceAtLeast(0).toLong()

        // 单选保持经典的 nextOrSame 行为
        if (days.size == 1) {
            val targetDow = DayOfWeek.of(days.first())
            var outDate = baseDate.plusDays(1).with(TemporalAdjusters.nextOrSame(targetDow))
            if (weekShift > 0) outDate = outDate.plusWeeks(weekShift)
            return listOf(outDate to outDate.plusDays(retOffset))
        }

        // 多选或「任意」：在未来 7 天窗口（按 weekOffset 平移）内展开全部满足条件的出发日
        val windowStart = baseDate.plusDays(1 + weekShift * 7L)
        return (0L..6L).mapNotNull { offset ->
            val d = windowStart.plusDays(offset)
            if (days.isEmpty() || d.dayOfWeek.value in days) {
                d to d.plusDays(retOffset)
            } else null
        }.ifEmpty {
            listOf(windowStart to windowStart.plusDays(retOffset))
        }
    }

    /**
     * 根据用户模板推算最近一次匹配的去程日期与返程日期
     */
    fun resolveTemplateDates(
        template: TripTemplateEntity,
        baseDate: LocalDate = ChinaClock.today()
    ): Pair<LocalDate, LocalDate> = resolveTemplateDatePairs(template, baseDate).first()

    /**
     * 将接口原始或本地缓存日历状态归一化为界面语义状态：
     * - 官方适用窗口外（或全局屏蔽期）且未显式放票/约满 (1/0) 且非无航班 (-4) -> -2（不可用）
     * - 距起飞不足 3 天（已过前 3 天 23:40 兑换截止期）且非无航班 (-4) 与不可用 (-2) -> -3（已截止）
     */
    fun normalizeCalendarStatus(
        rawStatus: Int?,
        date: LocalDate,
        today: LocalDate = ChinaClock.today(),
        globalUnavailableDates: Set<String> = emptySet()
    ): Int? {
        if (rawStatus == -4) return -4
        val isBlackout = !isWithinOfficialChangfeiDates(date) || date.toString() in globalUnavailableDates
        if (isBlackout && rawStatus != 1 && rawStatus != 0) return -2
        if (rawStatus == -2) return -2
        if (date.isBefore(today.plusDays(3))) return -3
        return rawStatus
    }

    /**
     * 离线+缓存综合往返行程配对引擎
     */
    fun matchRoundTrips(
        query: RoundTripQuery,
        allOutboundRoutes: List<FlightRouteEntity>,
        allInboundRoutes: List<FlightRouteEntity>,
        outboundCalendars: Map<String, CalendarCacheEntity>,
        inboundCalendars: Map<String, CalendarCacheEntity>,
        seatOverlay: Map<String, List<SeatSnapshotEntity>>,
        cityMetaMap: Map<String, CityMeta>,
        today: LocalDate = ChinaClock.today(),
        globalUnavailableDates: Set<String> = emptySet()
    ): List<RoundTripCandidate> {
        val outByDest = allOutboundRoutes
            .filter { r ->
                (query.destinationFilter.isBlank() || r.toCity == query.destinationFilter) &&
                    matchesDayOfWeek(r.frequency, query.outboundDate) &&
                    isInTimeWindow(r.depTime, query.outStartMinute, query.outEndMinute) &&
                    (!query.directOnly || r.stopover.isBlank() || r.stopover == "直飞")
            }
            .groupBy { it.toCity }

        val inByOrigin = allInboundRoutes
            .filter { r ->
                (query.destinationFilter.isBlank() || r.fromCity == query.destinationFilter) &&
                    matchesDayOfWeek(r.frequency, query.returnDate) &&
                    isInTimeWindow(r.depTime, query.retStartMinute, query.retEndMinute) &&
                    (!query.directOnly || r.stopover.isBlank() || r.stopover == "直飞")
            }
            .groupBy { it.fromCity }

        val commonDestinations = outByDest.keys.intersect(inByOrigin.keys)
        val outDateStr = query.outboundDate.toString()
        val retDateStr = query.returnDate.toString()

        val candidates = commonDestinations.mapNotNull { dest ->
            val outCal = normalizeCalendarStatus(
                outboundCalendars[dest]?.status,
                query.outboundDate,
                today,
                globalUnavailableDates
            )
            val retCal = normalizeCalendarStatus(
                inboundCalendars[dest]?.status,
                query.returnDate,
                today,
                globalUnavailableDates
            )
            if (query.onlyCalendarAvailable && !(outCal == 1 && retCal == 1)) {
                return@mapNotNull null
            }

            val outSeats = seatOverlay["${query.originCity}|$dest|$outDateStr"].orEmpty()
            val retSeats = seatOverlay["$dest|${query.originCity}|$retDateStr"].orEmpty()

            val outCandidates = mergeWithLiveSeats(outByDest[dest].orEmpty(), outSeats)
            val retCandidates = mergeWithLiveSeats(inByOrigin[dest].orEmpty(), retSeats)

            if (outCandidates.isEmpty() || retCandidates.isEmpty()) return@mapNotNull null

            val stayHours = estimateMaxStayHours(
                query.outboundDate,
                outCandidates.first(),
                query.returnDate,
                retCandidates.last()
            )
            if (stayHours < 0) return@mapNotNull null

            RoundTripCandidate(
                originCity = query.originCity,
                destinationCity = dest,
                cityMeta = cityMetaMap[dest],
                outboundDate = query.outboundDate,
                returnDate = query.returnDate,
                outboundFlights = outCandidates,
                returnFlights = retCandidates,
                outCalendarStatus = outCal,
                retCalendarStatus = retCal,
                maxStayHours = stayHours
            )
        }

        return candidates.sortedWith(
            compareByDescending<RoundTripCandidate> { it.isBothCalendarAvailable }
                .thenByDescending { (it.outCalendarStatus == 1) || (it.retCalendarStatus == 1) }
                .thenBy { it.hasAnyUnavailableCalendar }
                .thenByDescending { (it.outMaxSeats ?: -1) > 0 && (it.retMaxSeats ?: -1) > 0 }
                .thenByDescending { it.outboundFlights.size + it.returnFlights.size }
                .thenBy { it.destinationCity }
        )
    }

    data class AggregatedFlightSnapshot(
        val cleanFlightNo: String,
        val depTime: String,
        val arrTime: String,
        val aircraft: String,
        val stopover: String,
        val secCardLeft: Int,
        val normalLeft: Int,
        val minRedeemPrice: Double,
        val queriedAt: Long
    )

    /**
     * 将同一航班多个舱位的余票快照聚合为单条航班级余票：
     * - 按解码后的航班号 + 起飞时间分组；
     * - 同一舱位若有历史重复行取最新 queriedAt；
     * - 跨舱位对 secCardLeft 求和、normalLeft 取最大、正数价格取最低。
     */
    fun aggregateFlightSnapshots(snapshots: List<SeatSnapshotEntity>): List<AggregatedFlightSnapshot> {
        if (snapshots.isEmpty()) return emptyList()
        return snapshots.groupBy { s ->
            val clean = FlightNoDecoder.decode(s.flightNo)
                ?: s.flightNo.takeUnless { it.contains("&") }
                ?: s.flightNo
            "$clean|${s.depTime}"
        }.values.map { list ->
            val cleanRows = list.filterNot { it.flightNo.contains("&") }.ifEmpty { list }
            val byCabin = cleanRows.groupBy { it.cabinCode.ifBlank { "M" } }
                .values.map { g -> g.maxBy { it.queriedAt } }
            val head = byCabin.first()
            val cleanNo = FlightNoDecoder.decode(head.flightNo)
                ?: head.flightNo.takeUnless { it.contains("&") }
                ?: head.flightNo
            AggregatedFlightSnapshot(
                cleanFlightNo = cleanNo,
                depTime = head.depTime,
                arrTime = head.arrTime,
                aircraft = head.aircraft,
                stopover = head.stopover,
                secCardLeft = byCabin.sumOf { it.secCardLeft },
                normalLeft = byCabin.maxOf { it.normalLeft },
                minRedeemPrice = byCabin.map { it.redeemPrice }.filter { it > 0 }.minOrNull() ?: 0.0,
                queriedAt = byCabin.maxOf { it.queriedAt }
            )
        }.sortedBy { it.depTime }
    }

    /**
     * 针对已保存行程计划中的目标航班，汇总其实时多舱位畅飞卡余票；
     * 若当日未查到该航班（如停飞或调时），返回 null 而非误取其他航班余票。
     */
    fun matchPlannedFlightSecLeft(
        seats: List<LiveFlightSeat>,
        targetFlightNo: String,
        targetDepTime: String
    ): Int? {
        if (seats.isEmpty()) return null
        val normTargetNo = FlightNoDecoder.decode(targetFlightNo) ?: targetFlightNo.trim().uppercase()
        val byNo = seats.filter {
            val normSeatNo = FlightNoDecoder.decode(it.flightNo) ?: it.flightNo.trim().uppercase()
            normSeatNo.isNotBlank() && normSeatNo == normTargetNo
        }
        val matched = byNo.ifEmpty {
            seats.filter { it.depTime == targetDepTime }
        }
        if (matched.isEmpty()) return null
        return matched.groupBy { it.cabinCode.ifBlank { "M" } }
            .values
            .sumOf { group -> group.maxOf { it.secCardLeft } }
    }

    internal fun mergeWithLiveSeats(
        routes: List<FlightRouteEntity>,
        snapshots: List<SeatSnapshotEntity>
    ): List<FlightCandidate> {
        val aggregated = aggregateFlightSnapshots(snapshots)
        val snapByNo = aggregated.associateBy { it.cleanFlightNo }
        val snapByDep = aggregated.associateBy { it.depTime }
        return routes.sortedBy { it.depTime }.map { r ->
            val cleanRouteNo = FlightNoDecoder.decode(r.flightNo) ?: r.flightNo
            val snap = snapByNo[cleanRouteNo] ?: snapByDep[r.depTime]
            FlightCandidate(
                flightNo = cleanRouteNo,
                depTime = r.depTime,
                arrTime = r.arrTime,
                duration = r.duration,
                aircraft = snap?.aircraft?.ifBlank { r.aircraft } ?: r.aircraft,
                stopover = snap?.stopover?.ifBlank { r.stopover } ?: r.stopover,
                frequency = r.frequency,
                liveSecCardLeft = snap?.secCardLeft,
                liveNormalLeft = snap?.normalLeft,
                liveRedeemPrice = snap?.minRedeemPrice,
                liveQueriedAt = snap?.queriedAt
            )
        }
    }

    private fun estimateMaxStayHours(
        outDate: LocalDate,
        earliestOut: FlightCandidate,
        retDate: LocalDate,
        latestRet: FlightCandidate
    ): Long {
        val outArrMin = parseMinutes(earliestOut.arrTime) ?: parseMinutes(earliestOut.depTime) ?: 0
        val plusDay = if (earliestOut.arrTime.contains("+1")) 1L else 0L
        val outArrDt = LocalDateTime.of(
            outDate.plusDays(plusDay),
            LocalTime.of(outArrMin / 60, outArrMin % 60)
        )
        val retDepMin = parseMinutes(latestRet.depTime) ?: 0
        val retDepDt = LocalDateTime.of(
            retDate,
            LocalTime.of(retDepMin / 60, retDepMin % 60)
        )
        return Duration.between(outArrDt, retDepDt).toHours()
    }

    /**
     * 为行程计划生成智能抢票提醒时间建议（结合长龙畅飞卡提前30天10:00放票、提前3天截止规则）
     */
    fun buildReminderSuggestions(
        plan: TripPlanEntity,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ChinaClock.ZONE
    ): List<ReminderSuggestion> {
        val outDate = runCatching { LocalDate.parse(plan.outboundDate) }.getOrNull() ?: return emptyList()
        val retDate = runCatching { LocalDate.parse(plan.returnDate) }.getOrNull() ?: outDate

        fun toMillis(date: LocalDate, hour: Int, minute: Int): Long =
            LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        val list = mutableListOf<ReminderSuggestion>()
        val routeTitle = "${plan.fromCity} ⇄ ${plan.toCity}"

        // 1. 去程提前30天开抢前5分钟 (09:55)
        val outOpenMillis = toMillis(outDate.minusDays(30), 9, 55)
        list.add(
            ReminderSuggestion(
                label = "去程开抢前5分钟",
                description = "${outDate.minusDays(30)} 09:55（长龙畅飞卡通常提前30天10:00放票）",
                triggerAtMillis = outOpenMillis,
                title = "⏰ 准备抢去程票：$routeTitle",
                message = "去程 ${plan.outboundDate} ${plan.outboundFlightNo} (${plan.outboundDepTime}起飞) 即将开放兑换，请打开长龙航空准备抢票！",
                isInPast = outOpenMillis <= nowMillis
            )
        )

        // 2. 返程提前30天开抢前5分钟 (若与去程不同天)
        if (retDate != outDate) {
            val retOpenMillis = toMillis(retDate.minusDays(30), 9, 55)
            list.add(
                ReminderSuggestion(
                    label = "返程开抢前5分钟",
                    description = "${retDate.minusDays(30)} 09:55（返程放票前5分钟提醒）",
                    triggerAtMillis = retOpenMillis,
                    title = "⏰ 准备抢返程票：$routeTitle",
                    message = "返程 ${plan.returnDate} ${plan.returnFlightNo} (${plan.returnDepTime}起飞) 即将开放兑换，别忘了锁定返程！",
                    isInPast = retOpenMillis <= nowMillis
                )
            )
        }

        // 3. 截止前提醒（提前4天 20:00，防错过提前3天截止线）
        val cutoffMillis = toMillis(outDate.minusDays(4), 20, 0)
        list.add(
            ReminderSuggestion(
                label = "兑换截止前提醒",
                description = "${outDate.minusDays(4)} 20:00（距起飞前3天截止还剩1晚）",
                triggerAtMillis = cutoffMillis,
                title = "⚠️ 即将截止兑换：$routeTitle",
                message = "${plan.outboundDate} 出发的行程即将到达提前3天预约截止线，请尽快确认出票！",
                isInPast = cutoffMillis <= nowMillis
            )
        )

        // 4. 次日早 09:55 刷回流票提醒
        val tomorrowMorning = toMillis(LocalDate.now(zoneId).plusDays(1), 9, 55)
        list.add(
            ReminderSuggestion(
                label = "明早09:55刷余票",
                description = "${LocalDate.now(zoneId).plusDays(1)} 09:55（适合蹲临时退改回流票）",
                triggerAtMillis = tomorrowMorning,
                title = "🔎 刷畅飞卡余票：$routeTitle",
                message = "点击查看 ${plan.outboundDate} 去 / ${plan.returnDate} 回 最新畅飞卡余票情况。",
                isInPast = tomorrowMorning <= nowMillis
            )
        )

        return list
    }

    fun defaultTemplates(): List<TripTemplateEntity> = listOf(
        TripTemplateEntity(
            name = "周五晚 → 周日",
            outDayOfWeek = 5,
            outStartMinute = 18 * 60,
            outEndMinute = 23 * 60 + 59,
            retOffsetDays = 2,
            retStartMinute = 12 * 60,
            retEndMinute = 23 * 60 + 59,
            weekOffset = 0,
            directOnly = false,
            sortOrder = 1
        ),
        TripTemplateEntity(
            name = "周六早 → 周日",
            outDayOfWeek = 6,
            outStartMinute = 6 * 60,
            outEndMinute = 13 * 60,
            retOffsetDays = 1,
            retStartMinute = 14 * 60,
            retEndMinute = 23 * 60 + 59,
            weekOffset = 0,
            directOnly = false,
            sortOrder = 2
        ),
        TripTemplateEntity(
            name = "下周五晚 → 下周日",
            outDayOfWeek = 5,
            outStartMinute = 18 * 60,
            outEndMinute = 23 * 60 + 59,
            retOffsetDays = 2,
            retStartMinute = 12 * 60,
            retEndMinute = 23 * 60 + 59,
            weekOffset = 1,
            directOnly = false,
            sortOrder = 3
        ),
        TripTemplateEntity(
            name = "周四晚 → 周日",
            outDayOfWeek = 4,
            outStartMinute = 18 * 60,
            outEndMinute = 23 * 60 + 59,
            retOffsetDays = 3,
            retStartMinute = 12 * 60,
            retEndMinute = 23 * 60 + 59,
            weekOffset = 0,
            directOnly = false,
            sortOrder = 4
        )
    )
}
