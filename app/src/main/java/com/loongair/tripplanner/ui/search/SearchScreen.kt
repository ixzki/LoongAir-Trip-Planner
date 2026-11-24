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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.local.TripTemplateEntity
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.data.repository.FlightCandidate
import com.loongair.tripplanner.data.repository.RoundTripCandidate
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.MainViewModel
import com.loongair.tripplanner.ui.components.CompactScrollTopBar
import com.loongair.tripplanner.ui.components.FirstRunOnboardingGate
import com.loongair.tripplanner.ui.components.HeroCircleActionButton
import com.loongair.tripplanner.ui.components.HeroHeaderOverlapContainer
import com.loongair.tripplanner.ui.components.LacquerPullRefreshBox
import com.loongair.tripplanner.ui.components.LiveSyncProgressBanner
import com.loongair.tripplanner.ui.components.SoftCard
import com.loongair.tripplanner.ui.components.TonePillBadge
import com.loongair.tripplanner.ui.components.rememberHeaderCollapseProgress
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.bounceOnTouch
import com.loongair.tripplanner.ui.theme.pressHighlight
import com.loongair.tripplanner.ui.theme.pressScale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onPlanCreatedNavigate: (TripPlanEntity) -> Unit,
    onGoToAccountSync: () -> Unit
) {
    val p = LocalLoongPalette.current
    val state by viewModel.searchState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val session by viewModel.sessionState.collectAsState()
    val officialAirports by viewModel.officialAirports.collectAsState()
    val departureAirportsLoading by viewModel.departureAirportsLoading.collectAsState()
    val templates by viewModel.templates.collectAsState()

    // 首次登录并同步一个城市的所有航线之前，锁定往返搜索功能
    if (!networkState.isLoading && !networkState.hasSyncedData) {
        FirstRunOnboardingGate(
            screenTitle = "往返搜索",
            screenWatermark = "TRIP",
            featureHint = "无预置数据，同步一座城市后解锁往返配对",
            session = session,
            officialAirports = officialAirports,
            departureAirportsLoading = departureAirportsLoading,
            officialSyncing = networkState.officialSyncing,
            syncDoneCount = networkState.syncDoneCount,
            syncTotalCount = networkState.syncTotalCount,
            officialSyncProgress = networkState.officialSyncProgress,
            onGoToLogin = onGoToAccountSync,
            onRefreshAirports = { viewModel.refreshOfficialDepartureAirports(silent = false) },
            onSyncCityAllRoutes = { city -> viewModel.syncSingleCityAllRoutes(city) },
            onStopSync = { viewModel.stopOfficialNetworkSync() }
        )
        return
    }

    val listState = rememberLazyListState()
    val collapseProgress = rememberHeaderCollapseProgress(listState)

    var showOriginPicker by remember { mutableStateOf(false) }
    var showDestPicker by remember { mutableStateOf(false) }
    var showOutDatePicker by remember { mutableStateOf(false) }
    var showRetDatePicker by remember { mutableStateOf(false) }
    var showTimeWindowDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TripTemplateEntity?>(null) }
    var showNewTemplateDialog by remember { mutableStateOf(false) }
    var showManageTemplatesDialog by remember { mutableStateOf(false) }
    var expandedDest by remember { mutableStateOf<String?>(null) }
    var savePlanDraft by remember { mutableStateOf<Triple<RoundTripCandidate, FlightCandidate, FlightCandidate>?>(null) }

    val daysUntilOut = ChronoUnit.DAYS.between(LocalDate.now(), state.outboundDate)

    LacquerPullRefreshBox(
        listState = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.cardGap)
        ) {
            // 1. 沉浸式头图 + 悬浮往返条件卡片
            item {
                HeroHeaderOverlapContainer(
                    subtitle = "${state.originCity}出发 · 匹配 ${state.results.size} 个可往返目的地",
                    title = "往返搜索",
                    watermark = "TRIP",
                    collapseProgress = collapseProgress,
                    trailingAction = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HeroCircleActionButton(
                                icon = if (state.batchScanning) Icons.Default.Stop else Icons.Default.Radar,
                                contentDescription = if (state.batchScanning) "停止核验" else "批量核验日历",
                                onClick = {
                                    if (state.batchScanning) viewModel.stopBatchCalendarVerify()
                                    else viewModel.startBatchCalendarVerify()
                                }
                            )
                            HeroCircleActionButton(
                                icon = Icons.Default.Tune,
                                contentDescription = "管理模板",
                                onClick = { showManageTemplatesDialog = true }
                            )
                        }
                    }
                ) {
                    SoftCard(cornerRadius = AppRadii.card, floating = true, contentPadding = 16.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressScale(scaleDown = AppPressScale.CARD, onClick = { showOriginPicker = true })
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(p.primarySoftBg)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("出发城市", style = LoongType.caption.copy(fontWeight = FontWeight.Medium), color = p.textSecondary)
                                Text(
                                    "${state.originCity} (${CityIataMapper.toApiIata(state.originCity)})",
                                    style = LoongType.headline.copy(fontSize = 16.5.sp),
                                    color = p.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressScale(scaleDown = AppPressScale.CARD, onClick = { showDestPicker = true })
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(p.subtleSurface)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("目标城市", style = LoongType.caption.copy(fontWeight = FontWeight.Medium), color = p.textTertiary)
                                Text(
                                    state.destinationFilter.ifBlank { "全部可达 (${state.reachableDestinations.size}城)" },
                                    style = LoongType.headline.copy(fontSize = 15.5.sp),
                                    color = p.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 往返快捷模板横滑胶囊（极简紧凑，已按用户要求移除上方标题栏）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            templates.forEach { tpl ->
                                val selected = state.activeTemplateId == tpl.id
                                val pillBg by animateColorAsState(
                                    targetValue = if (selected) p.primarySoftBg else p.subtleSurface,
                                    animationSpec = AppMotion.fade(),
                                    label = "tplBg_${tpl.id}"
                                )
                                val pillFg by animateColorAsState(
                                    targetValue = if (selected) p.primary else p.textPrimary,
                                    animationSpec = AppMotion.fade(),
                                    label = "tplFg_${tpl.id}"
                                )
                                Row(
                                    modifier = Modifier
                                        .pressScale(
                                            scaleDown = AppPressScale.CONTROL,
                                            haptic = true,
                                            onClick = { viewModel.applyTemplate(tpl) }
                                        )
                                        .clip(RoundedCornerShape(AppRadii.sm))
                                        .background(pillBg)
                                        .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = tpl.name,
                                        style = LoongType.footnoteStrong,
                                        color = pillFg
                                    )
                                    Box(
                                        modifier = Modifier
                                            .pressScale(
                                                scaleDown = AppPressScale.ICON,
                                                onClick = { editingTemplate = tpl }
                                            )
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "编辑模板",
                                            tint = if (selected) p.primary else p.textTertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .pressScale(
                                        scaleDown = AppPressScale.CONTROL,
                                        haptic = true,
                                        onClick = { showNewTemplateDialog = true }
                                    )
                                    .clip(RoundedCornerShape(AppRadii.sm))
                                    .background(p.primarySoftBg.copy(alpha = 0.65f))
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = p.primary)
                                Text("新模板", style = LoongType.footnoteStrong, color = p.primary)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = AppSpace.hairline,
                            color = p.separator
                        )

                        // 多去程日 / 任意模板匹配范围条
                        if (state.multiDatePairs.isNotEmpty()) {
                            val firstOutStr = state.multiDatePairs.first().first.toString().substring(5)
                            val lastOutStr = state.multiDatePairs.last().first.toString().substring(5)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.primarySoftBg.copy(alpha = 0.72f))
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${state.multiDateLabel} · $firstOutStr ~ $lastOutStr",
                                    style = LoongType.caption.copy(fontFeatureSettings = "tnum"),
                                    color = p.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "切单日",
                                    style = LoongType.caption,
                                    color = p.textSecondary,
                                    modifier = Modifier
                                        .pressScale(
                                            scaleDown = AppPressScale.CONTROL,
                                            haptic = true,
                                            onClick = { viewModel.clearMultiDateMode() }
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        // 去程 / 返程日期与起飞时段
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DateWindowBox(
                                modifier = Modifier.weight(1f),
                                label = "去程 (${RoundTripMatcher.dayOfWeekLabel(state.outboundDate.dayOfWeek.value)})",
                                dateStr = state.outboundDate.toString().substring(5),
                                windowStr = "${RoundTripMatcher.formatMinutes(state.outStartMinute)}-${RoundTripMatcher.formatMinutes(state.outEndMinute)}",
                                onClickDate = { showOutDatePicker = true },
                                onClickWindow = { showTimeWindowDialog = true }
                            )
                            DateWindowBox(
                                modifier = Modifier.weight(1f),
                                label = "返程 (${RoundTripMatcher.dayOfWeekLabel(state.returnDate.dayOfWeek.value)})",
                                dateStr = state.returnDate.toString().substring(5),
                                windowStr = "${RoundTripMatcher.formatMinutes(state.retStartMinute)}-${RoundTripMatcher.formatMinutes(state.retEndMinute)}",
                                onClickDate = { showRetDatePicker = true },
                                onClickWindow = { showTimeWindowDialog = true }
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 周切换 + 规则窗口提示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                WeekShiftPill(
                                    label = "上周",
                                    leading = true,
                                    onClick = { viewModel.shiftDatesByWeeks(-1) }
                                )
                                WeekShiftPill(
                                    label = "下周",
                                    leading = false,
                                    onClick = { viewModel.shiftDatesByWeeks(1) }
                                )
                            }

                            val (badgeText, badgeBg, badgeFg) = when {
                                daysUntilOut < 3 -> Triple("距出发${daysUntilOut}天·不足3天", p.dangerBg, p.danger)
                                daysUntilOut <= 30 -> Triple("距出发${daysUntilOut}天·可兑窗口", p.successBg, p.success)
                                else -> Triple("距开抢约${daysUntilOut - 30}天·适合定闹钟", p.warningBg, p.warning)
                            }
                            TonePillBadge(
                                text = badgeText,
                                backgroundColor = badgeBg,
                                contentColor = badgeFg,
                                cornerRadius = 9.dp
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchFilterPill(
                                label = "仅看直飞",
                                selected = state.directOnly,
                                onClick = { viewModel.updateDatesAndWindows(directOnly = !state.directOnly) }
                            )
                            SearchFilterPill(
                                label = "仅看往返均可兑",
                                selected = state.onlyCalendarAvailable,
                                onClick = { viewModel.updateDatesAndWindows(onlyCalendarAvailable = !state.onlyCalendarAvailable) }
                            )
                        }
                    }
                }
            }

            // 2. 城市航线时刻后台同步进度条
            item {
                AnimatedVisibility(
                    visible = networkState.officialSyncing,
                    enter = expandVertically(AppMotion.reveal()) + fadeIn(AppMotion.fade()),
                    exit = shrinkVertically(AppMotion.reveal()) + fadeOut(AppMotion.fade(150))
                ) {
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        LiveSyncProgressBanner(
                            done = networkState.syncDoneCount,
                            total = networkState.syncTotalCount,
                            progressText = networkState.officialSyncProgress,
                            onStop = { viewModel.stopOfficialNetworkSync() }
                        )
                    }
                }
            }

            // 3. 批量核验日历进行中进度条（平滑出现/收起）
            item {
                AnimatedVisibility(
                    visible = state.batchScanning,
                    enter = expandVertically(AppMotion.reveal()) + fadeIn(AppMotion.fade()),
                    exit = shrinkVertically(AppMotion.reveal()) + fadeOut(AppMotion.fade(150))
                ) {
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        SoftCard(cornerRadius = 18.dp, contentPadding = 14.dp) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppRadii.full)),
                                color = p.primary,
                                trackColor = p.primarySoftBg
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(state.batchProgressText, style = LoongType.footnoteStrong, color = p.primary)
                        }
                    }
                }
            }

            // 3. 候选往返列表
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = p.primary, strokeWidth = 2.5.dp)
                    }
                }
            } else if (state.results.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        SoftCard(contentPadding = 22.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("当前时段暂无匹配的往返班次", style = LoongType.headline, color = p.textPrimary)
                                Text(
                                    "可以尝试放宽去程/返程起飞时段、关闭「仅看直飞」或切换其他日期。",
                                    style = LoongType.footnote,
                                    color = p.textSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                val isMultiDateMode = state.multiDatePairs.size > 1
                items(
                    state.results,
                    key = { "${it.originCity}-${it.destinationCity}-${it.outboundDate}-${it.returnDate}" }
                ) { candidate ->
                    val candKey = "${candidate.destinationCity}|${candidate.outboundDate}|${candidate.returnDate}"
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        RoundTripCandidateCard(
                            candidate = candidate,
                            showDatesBadge = isMultiDateMode,
                            isExpanded = expandedDest == candKey,
                            isVerifying = state.verifyingDestination == candKey,
                            onToggleExpand = {
                                expandedDest = if (expandedDest == candKey) null else candKey
                            },
                            onVerifyLiveSeats = { viewModel.verifyCandidateLiveSeats(candidate) },
                            onSavePlan = { outF, retF ->
                                savePlanDraft = Triple(candidate, outF, retF)
                            }
                        )
                    }
                }
            }
        }

        // 顶部随滚动淡入的紧凑标题条
        CompactScrollTopBar(
            title = "往返搜索",
            collapseProgress = collapseProgress,
            trailingAction = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    HeroCircleActionButton(
                        icon = if (state.batchScanning) Icons.Default.Stop else Icons.Default.Radar,
                        contentDescription = if (state.batchScanning) "停止核验" else "批量核验日历",
                        onClick = {
                            if (state.batchScanning) viewModel.stopBatchCalendarVerify()
                            else viewModel.startBatchCalendarVerify()
                        }
                    )
                    HeroCircleActionButton(
                        icon = Icons.Default.Tune,
                        contentDescription = "管理模板",
                        onClick = { showManageTemplatesDialog = true }
                    )
                }
            }
        )
    }

    if (showOriginPicker) {
        CityPickerDialog(
            title = "选择出发城市",
            cities = state.allOriginCities,
            cityMetaMap = state.cityMetaList.associateBy { it.city },
            allowAll = false,
            onDismiss = { showOriginPicker = false },
            onSelect = { city ->
                viewModel.setOriginCity(city)
                showOriginPicker = false
            }
        )
    }

    if (showDestPicker) {
        CityPickerDialog(
            title = "筛选目标城市",
            cities = state.reachableDestinations,
            cityMetaMap = state.cityMetaList.associateBy { it.city },
            allowAll = true,
            onDismiss = { showDestPicker = false },
            onSelect = { city ->
                viewModel.setDestinationFilter(city)
                showDestPicker = false
            }
        )
    }

    if (showOutDatePicker) {
        val initialMillis = state.outboundDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showOutDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.updateDatesAndWindows(outboundDate = picked)
                    }
                    showOutDatePicker = false
                }) { Text("确定", style = LoongType.filter) }
            },
            dismissButton = {
                TextButton(onClick = { showOutDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("选择去程日期", style = LoongType.headline, modifier = Modifier.padding(16.dp)) })
        }
    }

    if (showRetDatePicker) {
        val initialMillis = state.returnDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showRetDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.updateDatesAndWindows(returnDate = picked)
                    }
                    showRetDatePicker = false
                }) { Text("确定", style = LoongType.filter) }
            },
            dismissButton = {
                TextButton(onClick = { showRetDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("选择返程日期", style = LoongType.headline, modifier = Modifier.padding(16.dp)) })
        }
    }

    if (showTimeWindowDialog) {
        TimeWindowPickerDialog(
            outStart = state.outStartMinute,
            outEnd = state.outEndMinute,
            retStart = state.retStartMinute,
            retEnd = state.retEndMinute,
            onDismiss = { showTimeWindowDialog = false },
            onConfirm = { os, oe, rs, re ->
                viewModel.updateDatesAndWindows(
                    outStartMinute = os,
                    outEndMinute = oe,
                    retStartMinute = rs,
                    retEndMinute = re
                )
                showTimeWindowDialog = false
            }
        )
    }

    if (showManageTemplatesDialog) {
        TemplateManagerDialog(
            templates = templates,
            onDismiss = { showManageTemplatesDialog = false },
            onAddNew = {
                showManageTemplatesDialog = false
                showNewTemplateDialog = true
            },
            onEdit = { tpl ->
                showManageTemplatesDialog = false
                editingTemplate = tpl
            },
            onDelete = { tpl ->
                viewModel.deleteTemplate(tpl)
            }
        )
    }

    if (showNewTemplateDialog) {
        TemplateEditorDialog(
            initial = null,
            onDismiss = { showNewTemplateDialog = false },
            onSave = { tpl ->
                viewModel.saveTemplate(tpl)
                showNewTemplateDialog = false
            }
        )
    }

    editingTemplate?.let { tpl ->
        TemplateEditorDialog(
            initial = tpl,
            onDismiss = { editingTemplate = null },
            onSave = { updated ->
                viewModel.saveTemplate(updated)
                editingTemplate = null
            },
            onDelete = { toDel ->
                viewModel.deleteTemplate(toDel)
                editingTemplate = null
            }
        )
    }

    savePlanDraft?.let { (cand, outF, retF) ->
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savePlanDraft = null },
            containerColor = Color.White,
            title = { Text("保存往返行程计划", style = LoongType.headline, color = p.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${cand.originCity} ⇄ ${cand.destinationCity}",
                        style = LoongType.headline,
                        color = p.primary
                    )
                    Text(
                        "去程：${cand.outboundDate} ${outF.flightNo} ${outF.depTime}-${outF.arrTime}",
                        style = LoongType.tabularMeta,
                        color = p.textPrimary
                    )
                    Text(
                        "返程：${cand.returnDate} ${retF.flightNo} ${retF.depTime}-${retF.arrTime}",
                        style = LoongType.tabularMeta,
                        color = p.textPrimary
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("行程备注（可选）") },
                        placeholder = { Text("如：周五下班出发、周末美食探店") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "保存后可立即在「行程」中一键设定放票开抢提醒闹钟。",
                        style = LoongType.footnote,
                        color = p.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.bounceOnTouch(),
                    shape = RoundedCornerShape(AppRadii.full),
                    onClick = {
                        viewModel.saveTripPlan(cand, outF, retF, note) { saved ->
                            onPlanCreatedNavigate(saved)
                        }
                        savePlanDraft = null
                    }
                ) {
                    Text("保存并去设提醒", style = LoongType.filter)
                }
            },
            dismissButton = {
                TextButton(onClick = { savePlanDraft = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekShiftPill(
    label: String,
    leading: Boolean,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    Row(
        modifier = Modifier
            .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(p.subtleSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (leading) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
        }
        Text(label, style = LoongType.footnoteStrong, color = p.textPrimary)
        if (!leading) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = p.textSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SearchFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    val bg by animateColorAsState(
        targetValue = if (selected) p.primarySoftBg else p.subtleSurface,
        animationSpec = AppMotion.fade(),
        label = "searchFilterBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) p.primary else p.textSecondary,
        animationSpec = AppMotion.fade(),
        label = "searchFilterFg"
    )
    Row(
        modifier = Modifier
            .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onClick)
            .clip(RoundedCornerShape(AppRadii.sm))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        }
        Text(label, style = LoongType.footnoteStrong, color = fg)
    }
}

@Composable
private fun DateWindowBox(
    modifier: Modifier = Modifier,
    label: String,
    dateStr: String,
    windowStr: String,
    onClickDate: () -> Unit,
    onClickWindow: () -> Unit
) {
    val p = LocalLoongPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.subtleSurface)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(scaleDown = AppPressScale.CONTROL, onClick = onClickDate),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, style = LoongType.caption.copy(fontWeight = FontWeight.Medium), color = p.textSecondary)
                Text(dateStr, style = LoongType.tabularTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = p.textPrimary)
            }
            Icon(Icons.Default.CalendarMonth, contentDescription = "改日期", modifier = Modifier.size(19.dp), tint = p.primary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(scaleDown = AppPressScale.CONTROL, onClick = onClickWindow)
                .clip(RoundedCornerShape(9.dp))
                .background(p.primarySoftBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "时段 $windowStr",
                style = LoongType.caption.copy(fontFeatureSettings = "tnum"),
                color = p.primary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RoundTripCandidateCard(
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
        containerColor = Color.White,
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
                        item {
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
                    items(filtered) { city ->
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
private fun TimeWindowPickerDialog(
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
        containerColor = Color.White,
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
