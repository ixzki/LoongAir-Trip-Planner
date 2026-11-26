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
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.loongair.tripplanner.util.rememberChinaDateTime
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
    val state by viewModel.searchState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val officialAirports by viewModel.officialAirports.collectAsStateWithLifecycle()
    val departureAirportsLoading by viewModel.departureAirportsLoading.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()

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

    val now = rememberChinaDateTime()
    val today = now.toLocalDate()
    val cutoffPassed = RoundTripMatcher.isRedemptionClosed(today.plusDays(3), now)
    LaunchedEffect(today, cutoffPassed) { viewModel.runSearch() }
    val daysUntilOut = ChronoUnit.DAYS.between(today, state.outboundDate)

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

                            val isGlobalBlocked =
                                !RoundTripMatcher.isWithinOfficialChangfeiDates(state.outboundDate) ||
                                    !RoundTripMatcher.isWithinOfficialChangfeiDates(state.returnDate) ||
                                    state.outboundDate.toString() in networkState.globalUnavailableDates ||
                                    state.returnDate.toString() in networkState.globalUnavailableDates
                            val (badgeText, badgeBg, badgeFg) = when {
                                isGlobalBlocked -> Triple("含官方屏蔽不可用日", p.dangerBg, p.danger)
                                daysUntilOut < 0 -> Triple("出发日已过期·不可兑", p.dangerBg, p.danger)
                                RoundTripMatcher.isRedemptionClosed(state.outboundDate, now) ->
                                    Triple("距出发${daysUntilOut}天·兑换已截止", p.dangerBg, p.danger)
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
                    val soldOutPairMatch = remember(state.originCity, state.destinationFilter, networkState.allRoutes) {
                        if (state.destinationFilter.isBlank()) null
                        else {
                            val outRoute = networkState.allRoutes.firstOrNull {
                                it.fromCity == state.originCity && it.toCity == state.destinationFilter
                            }
                            val retRoute = networkState.allRoutes.firstOrNull {
                                it.fromCity == state.destinationFilter && it.toCity == state.originCity
                            }
                            val outSoldOut = outRoute != null && outRoute.flights.isEmpty() &&
                                outRoute.availableDaysCount == 0 && outRoute.soldOutDaysCount > 0
                            val retSoldOut = retRoute != null && retRoute.flights.isEmpty() &&
                                retRoute.availableDaysCount == 0 && retRoute.soldOutDaysCount > 0
                            if (outSoldOut || retSoldOut) (outRoute to retRoute) else null
                        }
                    }
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        SoftCard(contentPadding = 22.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    if (soldOutPairMatch != null) {
                                        "${state.originCity} ⇄ ${state.destinationFilter} 近期名额已约满"
                                    } else {
                                        "当前时段暂无匹配的往返班次"
                                    },
                                    style = LoongType.headline,
                                    color = p.textPrimary
                                )
                                Text(
                                    if (soldOutPairMatch != null) {
                                        "该航线仍在畅飞卡适用范围内，但近期已放票日期均已约满（官方暂未返回约满日班次时刻）；可在「航线」页点开该航线日历蹲守每日 10:00 新放票或退票回流。"
                                    } else {
                                        "可以尝试放宽去程/返程起飞时段、关闭「仅看直飞」或切换其他日期。"
                                    },
                                    style = LoongType.footnote,
                                    color = if (soldOutPairMatch != null) p.warning else p.textSecondary
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

    val pickerSelectableDates = remember(today, networkState.globalUnavailableDates) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                if (d.isBefore(today) || d.isAfter(RoundTripMatcher.OFFICIAL_CARD_END_DATE)) return false
                if (!RoundTripMatcher.isWithinOfficialChangfeiDates(d)) return false
                return d.toString() !in networkState.globalUnavailableDates
            }
        }
    }

    if (showOutDatePicker) {
        val initialMillis = state.outboundDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = pickerSelectableDates
        )
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
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        "选择去程日期",
                        style = LoongType.headline,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }
    }

    if (showRetDatePicker) {
        val initialMillis = state.returnDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = pickerSelectableDates
        )
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
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        "选择返程日期",
                        style = LoongType.headline,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
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
            containerColor = p.cardBg,
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
