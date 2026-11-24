package com.loongair.tripplanner.ui.routes

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.loongair.tripplanner.data.local.SeatSnapshotEntity
import com.loongair.tripplanner.data.remote.LoongAirApi
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.RouteCalendarDetailUiState
import com.loongair.tripplanner.ui.components.SoftCard
import com.loongair.tripplanner.ui.components.TonePillBadge
import com.loongair.tripplanner.ui.components.chhersShadow
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.bounceOnTouch
import com.loongair.tripplanner.ui.theme.pressScale
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun RouteCalendarDetailPage(
    detailState: RouteCalendarDetailUiState,
    onBack: () -> Unit,
    onRefreshCalendar: () -> Unit,
    onCalibrateSchedule: () -> Unit,
    onSelectDate: (String) -> Unit,
    onRefreshDateSeats: (String) -> Unit,
    onJumpToRoundTrip: (fromCity: String, toCity: String) -> Unit
) {
    val route = detailState.route ?: return
    val p = LocalLoongPalette.current
    val view = LocalView.current

    DisposableEffect(view, p.lightStatusBarIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            controller?.isAppearanceLightStatusBars = p.lightStatusBarIcons
        }
    }

    val today = remember { LocalDate.now() }
    val fortyDays = remember(today) { (0L until 40L).map { today.plusDays(it) } }
    val groupedByMonth = remember(fortyDays) { fortyDays.groupBy { YearMonth.from(it) } }
    val visibleDateKeys = remember(fortyDays) { fortyDays.map { it.toString() }.toSet() }
    val visibleCalendar = remember(detailState.calendarByDate, visibleDateKeys) {
        detailState.calendarByDate.filterKeys { it in visibleDateKeys }
    }

    val selectedDate = detailState.selectedDate
        ?.takeIf { it in visibleDateKeys }
        ?: fortyDays.first().toString()
    val selectedStatus = visibleCalendar[selectedDate]
    val selectedSeats = detailState.seatsByDate[selectedDate].orEmpty()
    val isQueryingSelected = detailState.queryingSeatsDate == selectedDate

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBg),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.cardGap)
    ) {
        // 1. 仿 chher-public LargeTitle 二级页顶栏
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 14.dp, end = AppSpace.screen, top = 8.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .chhersShadow(cornerRadius = 19.dp, floating = false, tint = p.shadowTint)
                            .clip(CircleShape)
                            .background(p.cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "返回",
                            tint = p.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(start = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${route.fromCity} → ${route.toCity}",
                        style = LoongType.largeTitle.copy(fontSize = 28.sp),
                        color = p.textPrimary
                    )
                    Text(
                        text = "${route.fromAirport} (${route.fromIata}) → ${route.toAirport} (${route.toIata})",
                        style = LoongType.footnote,
                        color = p.textSecondary
                    )
                }
            }
        }

        // 2. 班次时刻概览 + 校准与查往返操作卡
        item {
            Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                SoftCard(cornerRadius = AppRadii.card, floating = true, contentPadding = 16.dp) {
                    if (route.flights.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.subtleSurface)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                route.flights.forEachIndexed { idx, f ->
                                    if (idx > 0) HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                                    val freqLabel = RoundTripMatcher.formatChineseFrequency(f.frequency)
                                    val cleanNo = LoongAirApi.decodeFlightNo(f.flightNo)
                                        ?: f.flightNo.takeUnless { it.contains("&") }
                                        ?: "GJ班次"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${f.depTime}-${f.arrTime}",
                                            style = LoongType.tabularMeta.copy(fontWeight = FontWeight.Bold),
                                            color = p.textPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            cleanNo,
                                            style = LoongType.tabularMeta.copy(fontWeight = FontWeight.SemiBold),
                                            color = p.textPrimary,
                                            maxLines = 1
                                        )
                                        TonePillBadge(
                                            text = freqLabel,
                                            backgroundColor = p.primarySoftBg,
                                            contentColor = p.primary,
                                            cornerRadius = 7.dp
                                        )
                                        Text(
                                            "${f.stopover} · ${f.aircraft.ifBlank { "A320" }}",
                                            style = LoongType.footnote.copy(fontSize = 12.sp),
                                            color = p.textTertiary,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshCalendar,
                            enabled = !detailState.isRefreshingCalendar && !detailState.isCalibratingSchedule,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, p.separator),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .bounceOnTouch(enabled = !detailState.isRefreshingCalendar)
                        ) {
                            if (detailState.isRefreshingCalendar) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = p.primary)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("刷新日历", style = LoongType.footnoteStrong, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onCalibrateSchedule,
                            enabled = !detailState.isCalibratingSchedule && !detailState.isRefreshingCalendar,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, p.separator),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(42.dp)
                                .bounceOnTouch(enabled = !detailState.isCalibratingSchedule)
                        ) {
                            if (detailState.isCalibratingSchedule) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = p.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("校准中", style = LoongType.footnoteStrong, maxLines = 1)
                            } else {
                                Icon(Icons.Default.EventRepeat, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("校准班期", style = LoongType.footnoteStrong, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { onJumpToRoundTrip(route.fromCity, route.toCity) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .height(42.dp)
                                .bounceOnTouch()
                        ) {
                            Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("搜往返", style = LoongType.footnoteStrong, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 3. 当前选中日期的实时班次与余票详情卡
        item {
            Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                SelectedDateSeatsCard(
                    dateStr = selectedDate,
                    calendarStatus = selectedStatus,
                    seats = selectedSeats,
                    isQuerying = isQueryingSelected,
                    onRefreshSeats = { onRefreshDateSeats(selectedDate) }
                )
            }
        }

        // 4. 近 40 天按月余票网格日历
        groupedByMonth.forEach { (ym, datesInMonth) ->
            item(key = "month_${ym.year}_${ym.monthValue}") {
                Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                    MonthAvailabilityCard(
                        yearMonth = ym,
                        datesInMonth = datesInMonth,
                        calendarByDate = visibleCalendar,
                        seatsByDate = detailState.seatsByDate,
                        selectedDate = selectedDate,
                        onSelectDate = onSelectDate
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDateSeatsCard(
    dateStr: String,
    calendarStatus: Int?,
    seats: List<SeatSnapshotEntity>,
    isQuerying: Boolean,
    onRefreshSeats: () -> Unit
) {
    val p = LocalLoongPalette.current
    val parsedDate = remember(dateStr) { runCatching { LocalDate.parse(dateStr) }.getOrNull() }
    val dowLabel = parsedDate?.let { RoundTripMatcher.dayOfWeekLabel(it.dayOfWeek.value) }.orEmpty()

    SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = p.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "$dateStr $dowLabel",
                    style = LoongType.headline.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"),
                    color = p.textPrimary
                )
            }

            Box(
                modifier = Modifier
                    .pressScale(
                        scaleDown = AppPressScale.CONTROL,
                        haptic = true,
                        enabled = !isQuerying,
                        onClick = onRefreshSeats
                    )
                    .clip(RoundedCornerShape(AppRadii.full))
                    .background(p.primarySoftBg)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isQuerying) "查询中..." else "查余票",
                    style = LoongType.caption,
                    color = p.primary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            isQuerying -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = p.primary)
                    Text("正在拉取该日期的实时航班与畅飞卡余票...", style = LoongType.footnote, color = p.textSecondary)
                }
            }

            seats.isNotEmpty() -> {
                // 同一航班多舱位聚合展示，并按解码后航班号去重防止历史混淆行重复叠加
                val grouped = remember(seats) {
                    seats.groupBy { s ->
                        val clean = LoongAirApi.decodeFlightNo(s.flightNo)
                            ?: s.flightNo.takeUnless { it.contains("&") }
                            ?: s.flightNo
                        "$clean|${s.depTime}"
                    }.values.map { list ->
                        val cleanRows = list.filterNot { it.flightNo.contains("&") }.ifEmpty { list }
                        val byCabin = cleanRows.groupBy { it.cabinCode.ifBlank { "M" } }
                            .values.map { group -> group.maxBy { it.queriedAt } }
                        val head = byCabin.first()
                        val secTotal = byCabin.sumOf { it.secCardLeft }
                        val normalMax = byCabin.maxOf { it.normalLeft }
                        val minPrice = byCabin.map { it.redeemPrice }.filter { it > 0 }.minOrNull() ?: 0.0
                        Triple(head, secTotal to normalMax, minPrice)
                    }.sortedBy { it.first.depTime }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (head, counts, minPrice) ->
                        val (secLeft, normalLeft) = counts
                        val cleanNo = LoongAirApi.decodeFlightNo(head.flightNo)
                            ?: head.flightNo.takeUnless { it.contains("&") }
                            ?: "GJ班次"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(p.subtleSurface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        "${head.depTime} → ${head.arrTime}",
                                        style = LoongType.tabularTitle.copy(fontSize = 15.sp),
                                        color = p.textPrimary
                                    )
                                    Text(
                                        cleanNo,
                                        style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                                        color = p.primary
                                    )
                                }
                                val priceText = if (minPrice > 0) " · 现金参考 ¥${minPrice.toInt()}" else ""
                                Text(
                                    "${head.stopover} · ${head.aircraft.ifBlank { "A320" }}$priceText",
                                    style = LoongType.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                                    color = p.textTertiary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                TonePillBadge(
                                    text = if (secLeft > 0) "畅飞余 ${secLeft}张" else "畅飞无票",
                                    backgroundColor = if (secLeft > 0) p.successBg else p.dangerBg,
                                    contentColor = if (secLeft > 0) p.success else p.danger,
                                    cornerRadius = 8.dp
                                )
                                Text(
                                    "普通余 $normalLeft",
                                    style = LoongType.caption.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = p.textTertiary
                                )
                            }
                        }
                    }
                }
            }

            calendarStatus == -4 -> {
                Text("官方日历显示该日暂无此航线航班。", style = LoongType.footnote, color = p.textSecondary)
            }

            calendarStatus == -2 -> {
                Text("该日期不适用当前畅飞卡产品。", style = LoongType.footnote, color = p.textSecondary)
            }

            else -> {
                Text(
                    "点击下方日历中的任意日期或右上角「查余票」，即可查看该日每个航班的实时畅飞卡余票。",
                    style = LoongType.footnote,
                    color = p.textSecondary
                )
            }
        }
    }
}

@Composable
private fun MonthAvailabilityCard(
    yearMonth: YearMonth,
    datesInMonth: List<LocalDate>,
    calendarByDate: Map<String, Int>,
    seatsByDate: Map<String, List<SeatSnapshotEntity>>,
    selectedDate: String,
    onSelectDate: (String) -> Unit
) {
    val p = LocalLoongPalette.current
    val firstDate = datesInMonth.first()
    val leadingBlanks = (firstDate.dayOfWeek.value - 1).coerceIn(0, 6)
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + datesInMonth
    val rows = remember(cells) { cells.chunked(7) }

    SoftCard(cornerRadius = AppRadii.card, contentPadding = 14.dp) {
        Text(
            text = "${yearMonth.year}年${yearMonth.monthValue}月",
            style = LoongType.headline.copy(fontFeatureSettings = "tnum"),
            color = p.textPrimary
        )

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Text(
                    text = w,
                    style = LoongType.caption,
                    color = p.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val date = week.getOrNull(col)
                        if (date == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val dateStr = date.toString()
                            val status = calendarByDate[dateStr]
                            val cachedSeats = seatsByDate[dateStr]
                            val liveSum = cachedSeats?.let { list ->
                                list.groupBy { s ->
                                    val clean = LoongAirApi.decodeFlightNo(s.flightNo)
                                        ?: s.flightNo.takeUnless { it.contains("&") }
                                        ?: s.flightNo
                                    "$clean|${s.depTime}"
                                }.values.sumOf { flightRows ->
                                    val cleanRows = flightRows.filterNot { it.flightNo.contains("&") }.ifEmpty { flightRows }
                                    cleanRows.groupBy { it.cabinCode.ifBlank { "M" } }
                                        .values.sumOf { g -> g.maxBy { it.queriedAt }.secCardLeft }
                                }
                            }
                            val isSel = selectedDate == dateStr

                            val (cellBg, dayColor, subText, subColor) = when {
                                liveSum != null && liveSum > 0 -> Quad(p.successBg, p.textPrimary, "余$liveSum", p.success)
                                liveSum != null && liveSum == 0 -> Quad(p.dangerBg, p.textPrimary, "余0", p.danger)
                                status == 1 -> Quad(p.successBg, p.textPrimary, "可兑", p.success)
                                status == 0 -> Quad(p.dangerBg.copy(alpha = 0.7f), p.textSecondary, "兑完", p.danger)
                                status == -1 -> Quad(p.warningBg.copy(alpha = 0.7f), p.textSecondary, "未开", p.warning)
                                status == -3 -> Quad(p.subtleSurface, p.textTertiary, "截止", p.textTertiary)
                                status == -2 -> Quad(p.subtleSurface, p.textTertiary, "不适", p.textTertiary)
                                status == -4 -> Quad(p.subtleSurface.copy(alpha = 0.55f), p.textTertiary.copy(alpha = 0.55f), "无班", p.textTertiary.copy(alpha = 0.55f))
                                else -> Quad(p.subtleSurface.copy(alpha = 0.7f), p.textSecondary, "·", p.textTertiary)
                            }
                            val animBorder by animateColorAsState(
                                targetValue = if (isSel) p.primary else Color.Transparent,
                                animationSpec = AppMotion.fade(150),
                                label = "calCellBorder_$dateStr"
                            )
                            val shape = RoundedCornerShape(10.dp)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressScale(
                                        scaleDown = AppPressScale.CONTROL,
                                        haptic = true,
                                        onClick = { onSelectDate(dateStr) }
                                    )
                                    .clip(shape)
                                    .background(cellBg)
                                    .border(1.5.dp, animBorder, shape),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${date.dayOfMonth}",
                                    style = LoongType.tabularMeta.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSel || status == 1) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSel) p.primary else dayColor,
                                    maxLines = 1
                                )
                                Text(
                                    text = subText,
                                    style = LoongType.caption.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (status == 1 || (liveSum ?: 0) > 0) FontWeight.SemiBold else FontWeight.Normal,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = subColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
