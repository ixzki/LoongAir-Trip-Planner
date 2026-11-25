package com.loongair.tripplanner.ui.search

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.loongair.tripplanner.ui.components.chhersShadow
import kotlinx.coroutines.launch
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
        status == -2 -> Triple("${prefix}不可用", p.dangerBg, p.danger)
        status == -3 -> Triple("${prefix}已截止", p.dangerBg, p.danger)
        status == -4 -> Triple("${prefix}无航班", p.subtleSurface, p.textSecondary)
        liveMaxSeats != null && liveMaxSeats > 0 -> Triple("${prefix}余${liveMaxSeats}张", p.successBg, p.success)
        liveMaxSeats != null && liveMaxSeats == 0 -> Triple("${prefix}畅飞无票", p.dangerBg, p.danger)
        status == 1 -> Triple("${prefix}可兑", p.successBg, p.success)
        status == 0 -> Triple("${prefix}已兑完", p.dangerBg, p.danger)
        status == -1 -> Triple("${prefix}未开放", p.warningBg, p.warning)
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

private data class CityLetterSection(
    val letter: Char,
    val cities: List<String>
)

data class CityPickerRequest(
    val key: Any,
    val title: String,
    val cities: List<String>,
    val cityMetaMap: Map<String, CityMeta>,
    val allowAll: Boolean,
    val subtitle: String = "",
    val trailingAction: (@Composable () -> Unit)? = null,
    val onDismiss: () -> Unit,
    val onSelect: (String) -> Unit
)

class CityPickerController {
    var activeRequest by mutableStateOf<CityPickerRequest?>(null)
        internal set

    fun show(request: CityPickerRequest) {
        activeRequest = request
    }

    fun dismiss(key: Any) {
        if (activeRequest?.key === key) {
            activeRequest = null
        }
    }
}

val LocalCityPickerController = compositionLocalOf<CityPickerController?> { null }

@Composable
fun CityPickerDialog(
    title: String,
    cities: List<String>,
    cityMetaMap: Map<String, CityMeta>,
    allowAll: Boolean,
    subtitle: String = "",
    trailingAction: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val controller = LocalCityPickerController.current
    val requestKey = remember { Any() }
    val request = CityPickerRequest(
        key = requestKey,
        title = title,
        cities = cities,
        cityMetaMap = cityMetaMap,
        allowAll = allowAll,
        subtitle = subtitle,
        trailingAction = trailingAction,
        onDismiss = onDismiss,
        onSelect = onSelect
    )
    if (controller != null) {
        SideEffect {
            controller.show(request)
        }
        DisposableEffect(controller, requestKey) {
            onDispose {
                controller.dismiss(requestKey)
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            CityPickerFullScreenContent(request = request)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CityPickerFullScreenContent(
    request: CityPickerRequest
) {
    val title = request.title
    val cities = request.cities
    val cityMetaMap = request.cityMetaMap
    val allowAll = request.allowAll
    val subtitle = request.subtitle
    val trailingAction = request.trailingAction
    val onDismiss = request.onDismiss
    val onSelect = request.onSelect

    val p = LocalLoongPalette.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var query by remember(request.key) { mutableStateOf("") }
    var activeScrubLetter by remember(request.key) { mutableStateOf<Char?>(null) }
    var indexBarHeightPx by remember { mutableStateOf(1) }

    // 与「卡片」「设置」子页完全一致：暖纸底直通状态栏 + 深色状态栏图标，退出后自动恢复主题状态栏
    val view = LocalView.current
    DisposableEffect(view, p.lightStatusBarIcons) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            controller?.isAppearanceLightStatusBars = p.lightStatusBarIcons
        }
    }

    val groupedSections = remember(cities, cityMetaMap) {
        cities.distinct()
            .groupBy { city ->
                CityIataMapper.initialLetterForCity(city, cityMetaMap[city]?.pinyin.orEmpty())
            }
            .entries
            .sortedWith(compareBy<Map.Entry<Char, List<String>>> { it.key == '#' }.thenBy { it.key })
            .map { (letter, list) ->
                CityLetterSection(
                    letter = letter,
                    cities = list.sortedBy { c ->
                        CityIataMapper.pinyinForCity(c, cityMetaMap[c]?.pinyin.orEmpty())
                    }
                )
            }
    }

    val filtered = remember(query, cities, cityMetaMap) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            emptyList()
        } else {
            cities.distinct().filter { c ->
                val meta = cityMetaMap[c]
                val py = CityIataMapper.pinyinForCity(c, meta?.pinyin.orEmpty())
                val code = (meta?.code?.ifBlank { null } ?: CityIataMapper.toApiIata(c)).lowercase()
                c.lowercase().contains(q) ||
                    py.contains(q) ||
                    (meta?.pinyin?.lowercase()?.contains(q) == true) ||
                    code.contains(q) ||
                    (meta?.airport?.lowercase()?.contains(q) == true) ||
                    (meta?.province?.lowercase()?.contains(q) == true)
            }.sortedBy { c ->
                CityIataMapper.pinyinForCity(c, cityMetaMap[c]?.pinyin.orEmpty())
            }
        }
    }

    val sectionLetters = remember(groupedSections) { groupedSections.map { it.letter } }
    val allItemOffset = if (allowAll) 1 else 0
    val currentVisibleLetter by remember(groupedSections, allItemOffset) {
        derivedStateOf {
            val idx = (listState.firstVisibleItemIndex - allItemOffset)
                .coerceIn(0, (groupedSections.size - 1).coerceAtLeast(0))
            groupedSections.getOrNull(idx)?.letter
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = p.pageBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 复用「卡片」页同款大标题头部：38dp 纯白柔影圆钮 + 32sp 大标题 + 暖纸搜索胶囊
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 14.dp, end = AppSpace.screen, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .chhersShadow(
                                    cornerRadius = 19.dp,
                                    floating = false,
                                    tint = p.shadowTint
                                )
                                .clip(CircleShape)
                                .background(p.cardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "返回",
                                tint = p.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    if (trailingAction != null) {
                        trailingAction()
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = LoongType.largeTitle,
                        color = p.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = LoongType.footnote,
                            color = p.textSecondary
                        )
                    }
                }

                // 同款纯白柔影胶囊搜索栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                        .chhersShadow(
                            cornerRadius = 22.dp,
                            floating = false,
                            tint = p.shadowTint
                        )
                        .clip(RoundedCornerShape(AppRadii.full))
                        .background(p.cardBg)
                        .border(0.6.dp, p.border, RoundedCornerShape(AppRadii.full))
                        .height(44.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = p.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索中文 / 拼音 / 首字母 / 三字码",
                                style = LoongType.footnote.copy(fontSize = 14.sp),
                                color = p.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = LoongType.rowLabel.copy(color = p.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(p.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(p.subtleSurface)
                                .pressScale(scaleDown = AppPressScale.CONTROL, haptic = false, onClick = { query = "" }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "清空",
                                tint = p.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 主体内容：A-Z 分组卡片 + 右侧首字母导航条（搜索时切为精准匹配列表）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (query.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = AppSpace.groupInset, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filtered.isEmpty()) {
                            item(key = "__EMPTY__") {
                                SoftCard(cornerRadius = AppRadii.card, contentPadding = 24.dp) {
                                    Text(
                                        text = "未找到包含「$query」的城市",
                                        style = LoongType.footnote,
                                        color = p.textTertiary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            item(key = "__RESULTS__") {
                                SoftCard(cornerRadius = AppRadii.card, contentPadding = 6.dp) {
                                    filtered.forEachIndexed { idx, city ->
                                        val meta = cityMetaMap[city]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pressHighlight(cornerRadius = 12.dp, onClick = { onSelect(city) })
                                                .padding(vertical = 12.dp, horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(city, style = LoongType.rowLabel, color = p.textPrimary)
                                                val sub = listOfNotNull(
                                                    meta?.province?.takeIf { it.isNotBlank() },
                                                    meta?.airport?.takeIf { it.isNotBlank() }
                                                ).joinToString(" · ")
                                                if (sub.isNotBlank()) {
                                                    Text(sub, style = LoongType.footnote, color = p.textTertiary)
                                                }
                                            }
                                            TonePillBadge(
                                                text = meta?.code?.ifBlank { null } ?: CityIataMapper.toApiIata(city),
                                                backgroundColor = p.primarySoftBg,
                                                contentColor = p.primary,
                                                cornerRadius = 8.dp
                                            )
                                        }
                                        if (idx < filtered.lastIndex) {
                                            HorizontalDivider(
                                                thickness = AppSpace.hairline,
                                                color = p.separator,
                                                modifier = Modifier.padding(horizontal = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = AppSpace.groupInset, end = 34.dp, top = 6.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.cardGap)
                    ) {
                        if (allowAll) {
                            item(key = "__ALL__") {
                                SoftCard(cornerRadius = AppRadii.card, contentPadding = 14.dp) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pressHighlight(cornerRadius = 12.dp, onClick = { onSelect("") })
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "全部可达城市",
                                            style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                                            color = p.primary
                                        )
                                        TonePillBadge(
                                            text = "${cities.distinct().size} 座城市",
                                            backgroundColor = p.primarySoftBg,
                                            contentColor = p.primary,
                                            cornerRadius = 8.dp
                                        )
                                    }
                                }
                            }
                        }

                        items(groupedSections, key = { it.letter.toString() }) { section ->
                            SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(p.primarySoftBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = section.letter.toString(),
                                            style = LoongType.filter.copy(fontWeight = FontWeight.Bold),
                                            color = p.primary
                                        )
                                    }
                                    Text(
                                        text = "${section.cities.size} 城",
                                        style = LoongType.caption,
                                        color = p.textTertiary
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    section.cities.forEach { city ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(p.subtleSurface)
                                                .border(0.6.dp, p.border, RoundedCornerShape(11.dp))
                                                .pressScale(
                                                    scaleDown = AppPressScale.CONTROL,
                                                    haptic = true,
                                                    onClick = { onSelect(city) }
                                                )
                                                .padding(horizontal = 15.dp, vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = city,
                                                style = LoongType.filter.copy(fontWeight = FontWeight.Medium),
                                                color = p.textPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 右侧悬浮暖白胶囊 A-Z 索引条（支持点击与上下滑动快速定位）
                    if (sectionLetters.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 5.dp)
                                .chhersShadow(
                                    cornerRadius = 14.dp,
                                    floating = false,
                                    tint = p.shadowTint
                                )
                                .clip(RoundedCornerShape(AppRadii.full))
                                .background(p.cardBg.copy(alpha = 0.94f))
                                .border(0.6.dp, p.border, RoundedCornerShape(AppRadii.full))
                                .padding(vertical = 6.dp, horizontal = 2.dp)
                                .width(22.dp)
                                .onSizeChanged { indexBarHeightPx = it.height.coerceAtLeast(1) }
                                .pointerInput(sectionLetters, allItemOffset) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        fun jumpToY(y: Float) {
                                            val idx = ((y / indexBarHeightPx.toFloat()) * sectionLetters.size)
                                                .toInt()
                                                .coerceIn(0, sectionLetters.lastIndex)
                                            val letter = sectionLetters[idx]
                                            if (activeScrubLetter != letter) {
                                                activeScrubLetter = letter
                                                scope.launch {
                                                    listState.scrollToItem(idx + allItemOffset)
                                                }
                                            }
                                        }
                                        jumpToY(down.position.y)
                                        do {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull() ?: break
                                            if (change.pressed) {
                                                jumpToY(change.position.y)
                                                change.consume()
                                            }
                                        } while (event.changes.any { it.pressed })
                                        activeScrubLetter = null
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            val highlightLetter = activeScrubLetter ?: currentVisibleLetter
                            sectionLetters.forEach { letter ->
                                val isCurrent = letter == highlightLetter
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) p.primary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) Color.White else p.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 滑动索引条时的居中放大字母提示气泡
                    activeScrubLetter?.let { letter ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(64.dp)
                                .chhersShadow(
                                    cornerRadius = 18.dp,
                                    floating = true,
                                    tint = p.shadowTint
                                )
                                .clip(RoundedCornerShape(18.dp))
                                .background(p.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter.toString(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
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
