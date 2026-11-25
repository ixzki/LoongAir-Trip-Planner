package com.loongair.tripplanner.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.data.repository.FlightCandidate
import com.loongair.tripplanner.data.repository.RoundTripCandidate
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.components.SoftCard
import com.loongair.tripplanner.ui.components.TonePillBadge
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.bounceOnTouch
import com.loongair.tripplanner.ui.theme.pressHighlight
import com.loongair.tripplanner.ui.theme.pressScale

@Composable
internal fun RoundTripCandidateCard(
    candidate: RoundTripCandidate,
    showDatesBadge: Boolean = false,
    isExpanded: Boolean,
    isVerifying: Boolean,
    onToggleExpand: () -> Unit,
    onVerifyLiveSeats: () -> Unit,
    onSavePlan: (FlightCandidate, FlightCandidate) -> Unit
) {
    val p = LocalLoongPalette.current
    var selectedOutIdx by remember(candidate.destinationCity, candidate.outboundDate) { mutableStateOf(0) }
    var selectedRetIdx by remember(candidate.destinationCity, candidate.returnDate) { mutableStateOf(0) }

    val safeOut = candidate.outboundFlights.getOrElse(selectedOutIdx) { candidate.outboundFlights.first() }
    val safeRet = candidate.returnFlights.getOrElse(selectedRetIdx) { candidate.returnFlights.first() }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = AppMotion.select(),
        label = "expandChevron"
    )

    val stayText = if (candidate.maxStayHours >= 24) {
        "可玩约 ${candidate.maxStayHours / 24}天${candidate.maxStayHours % 24}小时"
    } else {
        "可玩约 ${candidate.maxStayHours}小时"
    }

    SoftCard(
        cornerRadius = AppRadii.card,
        borderColor = if (candidate.isBothCalendarAvailable) p.success.copy(alpha = 0.35f) else null,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(scaleDown = AppPressScale.CONTENT, onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        candidate.destinationCity,
                        style = LoongType.headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = p.textPrimary
                    )
                    candidate.cityMeta?.province?.let { prov ->
                        Text(prov, style = LoongType.footnote, color = p.textTertiary)
                    }
                    TonePillBadge(
                        text = stayText,
                        backgroundColor = p.subtleSurface,
                        contentColor = p.textSecondary,
                        cornerRadius = 8.dp
                    )
                }
                if (showDatesBadge) {
                    val outDow = RoundTripMatcher.dayOfWeekLabel(candidate.outboundDate.dayOfWeek.value)
                    val retDow = RoundTripMatcher.dayOfWeekLabel(candidate.returnDate.dayOfWeek.value)
                    Text(
                        "${candidate.outboundDate.toString().substring(5)} $outDow 去 → ${candidate.returnDate.toString().substring(5)} $retDow 回",
                        style = LoongType.footnoteStrong.copy(fontSize = 12.5.sp, fontFeatureSettings = "tnum"),
                        color = p.primary
                    )
                } else {
                    candidate.cityMeta?.tags?.take(3)?.takeIf { it.isNotEmpty() }?.let { tags ->
                        Text(
                            tags.joinToString(" · "),
                            style = LoongType.footnote.copy(fontSize = 12.5.sp),
                            color = p.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = p.textTertiary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalendarStatusPill("去", candidate.outCalendarStatus, candidate.outMaxSeats)
            CalendarStatusPill("返", candidate.retCalendarStatus, candidate.retMaxSeats)
            Spacer(Modifier.weight(1f))
            Text(
                "去${candidate.outboundFlights.size}班 · 返${candidate.returnFlights.size}班",
                style = LoongType.footnote.copy(fontFeatureSettings = "tnum"),
                color = p.textTertiary
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(scaleDown = AppPressScale.CONTENT, onClick = onToggleExpand)
                .clip(RoundedCornerShape(14.dp))
                .background(p.subtleSurface)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "去 ${safeOut.depTime}→${safeOut.arrTime}  ${safeOut.flightNo}",
                    style = LoongType.tabularMeta.copy(fontWeight = FontWeight.SemiBold),
                    color = p.textPrimary
                )
                Text(
                    "返 ${safeRet.depTime}→${safeRet.arrTime}  ${safeRet.flightNo}",
                    style = LoongType.tabularMeta.copy(fontWeight = FontWeight.SemiBold),
                    color = p.textPrimary
                )
            }
            Text(
                if (isExpanded) "收起班次" else "选班次 / 查余票",
                style = LoongType.footnoteStrong,
                color = p.primary
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = AppMotion.reveal()) + fadeIn(animationSpec = AppMotion.fade(200)),
            exit = shrinkVertically(animationSpec = AppMotion.reveal()) + fadeOut(animationSpec = AppMotion.fade(140))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = AppSpace.hairline,
                    color = p.separator
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.size(16.dp), tint = p.primary)
                    Text(
                        "去程 ${candidate.outboundDate}（${candidate.originCity} → ${candidate.destinationCity}）",
                        style = LoongType.filter.copy(fontFeatureSettings = "tnum"),
                        color = p.textPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    candidate.outboundFlights.forEachIndexed { idx, f ->
                        FlightOptionRow(
                            flight = f,
                            selected = idx == selectedOutIdx,
                            onClick = { selectedOutIdx = idx }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FlightLand, contentDescription = null, modifier = Modifier.size(16.dp), tint = p.success)
                    Text(
                        "返程 ${candidate.returnDate}（${candidate.destinationCity} → ${candidate.originCity}）",
                        style = LoongType.filter.copy(fontFeatureSettings = "tnum"),
                        color = p.textPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    candidate.returnFlights.forEachIndexed { idx, f ->
                        FlightOptionRow(
                            flight = f,
                            selected = idx == selectedRetIdx,
                            onClick = { selectedRetIdx = idx }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onVerifyLiveSeats,
                        enabled = !isVerifying,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, p.separator),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .bounceOnTouch(enabled = !isVerifying)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = p.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("查询中...", style = LoongType.filter)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("实时查余票", style = LoongType.filter)
                        }
                    }

                    Button(
                        onClick = { onSavePlan(safeOut, safeRet) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .bounceOnTouch()
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("加入行程", style = LoongType.filter)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarStatusPill(prefix: String, status: Int?, liveMaxSeats: Int?) {
    val p = LocalLoongPalette.current
    val (text, bg, fg) = when {
        liveMaxSeats != null && liveMaxSeats > 0 -> Triple("${prefix}余${liveMaxSeats}张", p.successBg, p.success)
        liveMaxSeats != null && liveMaxSeats == 0 -> Triple("${prefix}畅飞无票", p.dangerBg, p.danger)
        status == 1 -> Triple("${prefix}可兑", p.successBg, p.success)
        status == 0 -> Triple("${prefix}已兑完", p.dangerBg, p.danger)
        status == -1 -> Triple("${prefix}未开放", p.warningBg, p.warning)
        status == -2 -> Triple("${prefix}不适用", p.dangerBg, p.danger)
        status == -3 -> Triple("${prefix}已截止", p.dangerBg, p.danger)
        status == -4 -> Triple("${prefix}无航班", p.subtleSurface, p.textSecondary)
        else -> Triple("${prefix}待查", p.subtleSurface, p.textSecondary)
    }
    TonePillBadge(
        text = text,
        backgroundColor = bg,
        contentColor = fg,
        cornerRadius = 8.dp
    )
}

@Composable
private fun FlightOptionRow(
    flight: FlightCandidate,
    selected: Boolean,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    val bg by animateColorAsState(
        targetValue = if (selected) p.primarySoftBg.copy(alpha = 0.72f) else p.subtleSurface,
        animationSpec = AppMotion.fade(),
        label = "flightRowBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) p.primary else Color.Transparent,
        animationSpec = AppMotion.fade(),
        label = "flightRowBorder"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(scaleDown = AppPressScale.CONTENT, haptic = true, onClick = onClick),
        color = bg,
        border = BorderStroke(1.2.dp, borderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${flight.depTime} - ${flight.arrTime}",
                        style = LoongType.tabularTitle,
                        color = p.textPrimary
                    )
                    Text(flight.flightNo, style = LoongType.footnoteStrong, color = p.primary)
                    Text(flight.stopover, style = LoongType.caption.copy(fontWeight = FontWeight.Normal), color = p.textSecondary)
                }
                Text(
                    "${flight.aircraft.ifBlank { "A320" }} · ${flight.duration.ifBlank { "定期航班" }} · ${RoundTripMatcher.formatChineseFrequency(flight.frequency)}",
                    style = LoongType.footnote.copy(fontSize = 12.sp),
                    color = p.textTertiary
                )
            }
            if (flight.liveSecCardLeft != null) {
                val hasSeat = flight.liveSecCardLeft > 0
                Surface(
                    color = if (hasSeat) p.successBg else p.dangerBg,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            if (hasSeat) "畅飞余 ${flight.liveSecCardLeft}" else "畅飞无票",
                            style = LoongType.caption.copy(fontFeatureSettings = "tnum"),
                            color = if (hasSeat) p.success else p.danger
                        )
                        if (flight.liveNormalLeft != null) {
                            Text(
                                "普通余 ${flight.liveNormalLeft}",
                                style = LoongType.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = p.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CityPickerDialog(
    title: String,
    cities: List<String>,
    cityMetaMap: Map<String, CityMeta>,
    allowAll: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val p = LocalLoongPalette.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, cities) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) cities else cities.filter { c ->
            val meta = cityMetaMap[c]
            c.lowercase().contains(q) ||
                (meta?.pinyin?.lowercase()?.contains(q) == true) ||
                (meta?.code?.lowercase()?.contains(q) == true) ||
                (meta?.province?.lowercase()?.contains(q) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.cardBg,
        title = { Text(title, style = LoongType.headline, color = p.textPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索城市 / 拼音 / 三字码", style = LoongType.footnote) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(310.dp)) {
                    if (allowAll) {
                        item(key = "__ALL__") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressHighlight(cornerRadius = 12.dp, onClick = { onSelect("") })
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("全部可达城市", style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold), color = p.primary)
                                Text("${cities.size} 座城市", style = LoongType.footnote, color = p.textTertiary)
                            }
                            HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                        }
                    }
                    items(filtered, key = { it }) { city ->
                        val meta = cityMetaMap[city]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressHighlight(cornerRadius = 12.dp, onClick = { onSelect(city) })
                                .padding(vertical = 11.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(city, style = LoongType.rowLabel, color = p.textPrimary)
                                meta?.let {
                                    Text("${it.province} · ${it.airport}", style = LoongType.footnote, color = p.textTertiary)
                                }
                            }
                            Text(
                                CityIataMapper.toApiIata(city),
                                style = LoongType.filter,
                                color = p.primary
                            )
                        }
                        HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = LoongType.filter) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeWindowPickerDialog(
    outStart: Int,
    outEnd: Int,
    retStart: Int,
    retEnd: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit
) {
    val p = LocalLoongPalette.current
    var os by remember { mutableStateOf(outStart) }
    var oe by remember { mutableStateOf(outEnd) }
    var rs by remember { mutableStateOf(retStart) }
    var re by remember { mutableStateOf(retEnd) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.cardBg,
        title = { Text("设置去程 / 返程起飞时段", style = LoongType.headline, color = p.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "去程起飞时段：${RoundTripMatcher.formatMinutes(os)} - ${RoundTripMatcher.formatMinutes(oe)}",
                    style = LoongType.filter.copy(fontFeatureSettings = "tnum"),
                    color = p.textPrimary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TIME_WINDOW_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = os == preset.startMinute && oe == preset.endMinute,
                            onClick = { os = preset.startMinute; oe = preset.endMinute },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = p.primarySoftBg,
                                selectedLabelColor = p.primary
                            ),
                            label = { Text(preset.label, style = LoongType.caption) }
                        )
                    }
                }
                HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                Text(
                    "返程起飞时段：${RoundTripMatcher.formatMinutes(rs)} - ${RoundTripMatcher.formatMinutes(re)}",
                    style = LoongType.filter.copy(fontFeatureSettings = "tnum"),
                    color = p.textPrimary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TIME_WINDOW_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = rs == preset.startMinute && re == preset.endMinute,
                            onClick = { rs = preset.startMinute; re = preset.endMinute },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = p.primarySoftBg,
                                selectedLabelColor = p.primary
                            ),
                            label = { Text(preset.label, style = LoongType.caption) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.bounceOnTouch(),
                shape = RoundedCornerShape(AppRadii.full),
                onClick = { onConfirm(os, oe, rs, re) }
            ) { Text("应用时段", style = LoongType.filter) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
