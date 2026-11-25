package com.loongair.tripplanner.ui.routes

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loongair.tripplanner.data.remote.ChinaProvincePath
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.data.remote.LoongAirApi
import com.loongair.tripplanner.data.repository.NetworkRouteSummary
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
import com.loongair.tripplanner.ui.components.todayChineseHeaderDate
import com.loongair.tripplanner.ui.search.CityPickerDialog
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.pressScale
import kotlinx.coroutines.launch

private val HUB_COLOR_PALETTE = listOf(
    Color(0xFFC84B31),
    Color(0xFF2B6CB0),
    Color(0xFF7B52AB),
    Color(0xFFB58024),
    Color(0xFF2F855A),
    Color(0xFFA83E6B),
    Color(0xFF1F7A8C),
    Color(0xFF2B7A78),
    Color(0xFFC96A2B),
    Color(0xFF4C51BF)
)

@Composable
fun RoutesScreen(
    viewModel: MainViewModel,
    onJumpToRoundTrip: (fromCity: String, toCity: String) -> Unit,
    onGoToAccountSync: () -> Unit
) {
    val p = LocalLoongPalette.current
    val state by viewModel.networkState.collectAsStateWithLifecycle()
    val detailState by viewModel.routeDetailState.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val officialAirports by viewModel.officialAirports.collectAsStateWithLifecycle()
    val departureAirportsLoading by viewModel.departureAirportsLoading.collectAsStateWithLifecycle()

    BackHandler(enabled = detailState.route != null) {
        viewModel.closeRouteCalendarDetail()
    }

    // 首次登录并同步一个城市的所有航线之前，禁止进入空白航网页面
    if (!state.isLoading && !state.hasSyncedData) {
        FirstRunOnboardingGate(
            screenTitle = "航线",
            screenWatermark = "ROUTE",
            featureHint = "无预置数据，同步一座城市后解锁航网",
            session = session,
            officialAirports = officialAirports,
            departureAirportsLoading = departureAirportsLoading,
            officialSyncing = state.officialSyncing,
            syncDoneCount = state.syncDoneCount,
            syncTotalCount = state.syncTotalCount,
            officialSyncProgress = state.officialSyncProgress,
            onGoToLogin = onGoToAccountSync,
            onRefreshAirports = { viewModel.refreshOfficialDepartureAirports(silent = false) },
            onSyncCityAllRoutes = { city -> viewModel.syncSingleCityAllRoutes(city) },
            onStopSync = { viewModel.stopOfficialNetworkSync() }
        )
        return
    }

    val metaMap = remember(state.cityMetaList) { state.cityMetaList.associateBy { it.city } }
    val filteredRoutes = state.filteredRoutes
    val hubColorMap = remember(state.allOriginCities) {
        state.allOriginCities.mapIndexed { idx, city ->
            city to HUB_COLOR_PALETTE[idx % HUB_COLOR_PALETTE.size]
        }.toMap()
    }
    val quickHubs = remember(state.allOriginCities) {
        if (state.allOriginCities.size <= 1) {
            state.allOriginCities
        } else {
            listOf("全部") + state.allOriginCities.take(10)
        }
    }

    val listState = rememberLazyListState()
    val collapseProgress = rememberHeaderCollapseProgress(listState)

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var swapTurns by remember { mutableIntStateOf(0) }
    val swapRotation by animateFloatAsState(
        targetValue = swapTurns * 180f,
        animationSpec = AppMotion.select(),
        label = "swapCitySpin"
    )

    val totalCities = remember(state.allRoutes) {
        (state.allRoutes.map { it.fromCity } + state.allRoutes.map { it.toCity }).distinct().size
    }

    AnimatedContent(
        targetState = detailState.route != null,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(AppMotion.revealOffset()) { it } + fadeIn(AppMotion.fade(180))) togetherWith
                    (slideOutHorizontally(AppMotion.revealOffset()) { -it / 4 } + fadeOut(AppMotion.fade(220)))
            } else {
                (slideInHorizontally(AppMotion.revealOffset()) { -it / 4 } + fadeIn(AppMotion.fade(220))) togetherWith
                    (slideOutHorizontally(AppMotion.revealOffset()) { it } + fadeOut(AppMotion.fade(180)))
            }
        },
        label = "routeDetailSubPage"
    ) { showingDetail ->
        if (showingDetail && detailState.route != null) {
            RouteCalendarDetailPage(
                detailState = detailState,
                onBack = { viewModel.closeRouteCalendarDetail() },
                onRefreshCalendar = { viewModel.refreshOpenedRouteCalendar() },
                onCalibrateSchedule = { viewModel.calibrateOpenedRouteWeeklySchedule() },
                onSelectDate = { dateStr -> viewModel.selectRouteDetailDate(dateStr) },
                onRefreshDateSeats = { dateStr -> viewModel.queryRouteDetailSeats(dateStr) },
                onJumpToRoundTrip = onJumpToRoundTrip
            )
            return@AnimatedContent
        }

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
                // 1. 仿 chher-public 漆面/薄暮头图 + 跨缝悬浮全国航线地图卡
                item {
                    HeroHeaderOverlapContainer(
                        subtitle = todayChineseHeaderDate(),
                        title = "航线",
                        watermark = "ROUTE",
                        collapseProgress = collapseProgress,
                        trailingAction = {
                            HeroCircleActionButton(
                                icon = Icons.Default.Search,
                                contentDescription = "按城市筛选",
                                onClick = { showFromPicker = true }
                            )
                        }
                    ) {
                        ChinaRouteMapCard(
                            provinces = state.provinces,
                            routes = filteredRoutes,
                            metaMap = metaMap,
                            originCities = state.allOriginCities,
                            quickHubs = quickHubs,
                            hubColorMap = hubColorMap,
                            selectedOrigin = state.fromCityFilter,
                            totalCitiesCount = totalCities,
                            onSelectQuickHub = { hub ->
                                viewModel.setNetworkFilter(fromCity = if (hub == "全部") "" else hub, toCity = "")
                            }
                        )
                    }
                }

                // 后台逐条同步时刻与 60 天日历时的实时进度横幅
                item {
                    AnimatedVisibility(
                        visible = state.officialSyncing,
                        enter = expandVertically(AppMotion.reveal()) + fadeIn(AppMotion.fade()),
                        exit = shrinkVertically(AppMotion.reveal()) + fadeOut(AppMotion.fade(150))
                    ) {
                        Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                            LiveSyncProgressBanner(
                                done = state.syncDoneCount,
                                total = state.syncTotalCount,
                                progressText = state.officialSyncProgress,
                                onStop = { viewModel.stopOfficialNetworkSync() }
                            )
                        }
                    }
                }

                // 2. 城市筛选卡（精简移除顶部「城市筛选」与「航线详情」标题栏）
                item {
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        SoftCard(contentPadding = 12.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CityFilterBox(
                                    modifier = Modifier.weight(1f),
                                    label = "出发城市 / 机场",
                                    city = state.fromCityFilter,
                                    meta = metaMap[state.fromCityFilter],
                                    placeholder = "全部出发地",
                                    onClick = { showFromPicker = true }
                                )

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pressScale(
                                            scaleDown = AppPressScale.ICON,
                                            haptic = true,
                                            onClick = {
                                                swapTurns++
                                                viewModel.swapNetworkCities()
                                            }
                                        )
                                        .clip(CircleShape)
                                        .background(p.primarySoftBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = "互换城市",
                                        tint = p.primary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .graphicsLayer { rotationZ = swapRotation }
                                    )
                                }

                                CityFilterBox(
                                    modifier = Modifier.weight(1f),
                                    label = "到达城市 / 机场",
                                    city = state.toCityFilter,
                                    meta = metaMap[state.toCityFilter],
                                    placeholder = "全部到达地",
                                    onClick = { showToPicker = true }
                                )
                            }
                        }
                    }
                }

                // 3. 航线详情列表（点击任意航线卡片进入 40 天日历与余票详情页）
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
                } else if (filteredRoutes.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                            SoftCard(contentPadding = 22.dp) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("当前筛选条件下暂无航线", style = LoongType.headline, color = p.textPrimary)
                                    Text(
                                        "可清空出发/到达筛选，或前往「我的」拉取最新官方航线。",
                                        style = LoongType.footnote,
                                        color = p.textSecondary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredRoutes, key = { "${it.fromCity}|${it.toCity}" }) { route ->
                        Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                            NetworkRouteDetailCard(
                                route = route,
                                onClick = { viewModel.openRouteCalendarDetail(route) }
                            )
                        }
                    }
                }
            }

            // 顶部随滚动淡入的紧凑标题条
            CompactScrollTopBar(
                title = "航线",
                collapseProgress = collapseProgress,
                trailingAction = {
                    HeroCircleActionButton(
                        icon = Icons.Default.Search,
                        contentDescription = "按城市筛选",
                        onClick = { showFromPicker = true }
                    )
                }
            )
        }
    }

    if (showFromPicker) {
        CityPickerDialog(
            title = "选择出发城市",
            cities = state.allOriginCities,
            cityMetaMap = metaMap,
            allowAll = true,
            onDismiss = { showFromPicker = false },
            onSelect = { city ->
                viewModel.setNetworkFilter(fromCity = city)
                showFromPicker = false
            }
        )
    }

    if (showToPicker) {
        val destOptions = remember(state.fromCityFilter, state.allRoutes, state.allDestCities) {
            if (state.fromCityFilter.isBlank()) {
                state.allDestCities
            } else {
                state.allRoutes
                    .filter { it.fromCity == state.fromCityFilter }
                    .map { it.toCity }
                    .distinct()
                    .sorted()
            }
        }
        CityPickerDialog(
            title = "选择到达城市",
            cities = destOptions,
            cityMetaMap = metaMap,
            allowAll = true,
            onDismiss = { showToPicker = false },
            onSelect = { city ->
                viewModel.setNetworkFilter(toCity = city)
                showToPicker = false
            }
        )
    }
}

@Composable
private fun CityFilterBox(
    modifier: Modifier,
    label: String,
    city: String,
    meta: CityMeta?,
    placeholder: String,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    val hasCity = city.isNotBlank()
    val bg by animateColorAsState(
        targetValue = if (hasCity) p.primarySoftBg.copy(alpha = 0.72f) else p.subtleSurface,
        animationSpec = AppMotion.fade(),
        label = "cityBoxBg"
    )
    Column(
        modifier = modifier
            .pressScale(scaleDown = AppPressScale.CARD, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = LoongType.caption.copy(fontWeight = FontWeight.Medium), color = p.textTertiary)
        Text(
            text = if (!hasCity) placeholder else "$city (${CityIataMapper.toApiIata(city)})",
            style = LoongType.headline.copy(fontSize = 15.5.sp),
            color = if (!hasCity) p.textSecondary else p.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (hasCity && meta != null) {
            Text(
                meta.airport,
                style = LoongType.caption.copy(fontWeight = FontWeight.Normal),
                color = p.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChinaRouteMapCard(
    provinces: List<ChinaProvincePath>,
    routes: List<NetworkRouteSummary>,
    metaMap: Map<String, CityMeta>,
    originCities: List<String>,
    quickHubs: List<String>,
    hubColorMap: Map<String, Color>,
    selectedOrigin: String,
    totalCitiesCount: Int,
    onSelectQuickHub: (String) -> Unit
) {
    val p = LocalLoongPalette.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scale = scaleAnim.value
    val isZoomedOrPanned = scale > 1.03f || kotlin.math.abs(offsetX) > 2f || kotlin.math.abs(offsetY) > 2f

    val parsedPaths = remember(provinces) {
        provinces.map { parseSvgPathToComposePath(it.path) }
    }

    val activeCities = remember(routes) {
        (routes.map { it.fromCity } + routes.map { it.toCity }).toSet()
    }
    val originRankMap = remember(originCities) {
        originCities.withIndex().associate { (i, c) -> c to i }
    }
    val topHubsSet = remember(originCities) { originCities.take(8).toSet() }
    val topLabelHubsSet = remember(originCities) { originCities.take(14).toSet() }

    // 概览模式下将往返双向重叠航线去重，用轻盈大圆弧线呈现；聚焦枢纽时完整高亮
    val displayRoutes = remember(routes, selectedOrigin) {
        if (selectedOrigin.isNotBlank()) {
            routes
        } else {
            val seen = HashSet<String>(routes.size)
            routes.filter { r ->
                val key = if (r.fromCity < r.toCity) "${r.fromCity}|${r.toCity}" else "${r.toCity}|${r.fromCity}"
                seen.add(key)
            }
        }
    }
    val sortedLabelCities = remember(activeCities, selectedOrigin, originRankMap) {
        activeCities.sortedWith(
            compareBy<String>(
                { it != selectedOrigin },
                { originRankMap[it] ?: Int.MAX_VALUE },
                { it }
            )
        )
    }

    val arcPath = remember { Path() }
    val placedRects = remember { ArrayList<android.graphics.RectF>(32) }

    val haloPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
        }
    }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
        }
    }

    SoftCard(cornerRadius = AppRadii.card, floating = true, contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "全国随心飞航网",
                style = LoongType.headline,
                color = p.textPrimary
            )

            TonePillBadge(
                text = if (selectedOrigin.isBlank()) {
                    "$totalCitiesCount 城 · ${routes.size} 条"
                } else {
                    "${selectedOrigin}出发 · ${routes.size} 条"
                },
                backgroundColor = if (selectedOrigin.isBlank()) p.subtleSurface else p.primarySoftBg,
                contentColor = if (selectedOrigin.isBlank()) p.textSecondary else p.primary,
                cornerRadius = 9.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.26f)
                .clip(RoundedCornerShape(16.dp))
                .background(p.subtleSurface.copy(alpha = 0.72f))
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scaleAnim.value * zoom).coerceIn(1f, 4.5f)
                        scope.launch { scaleAnim.snapTo(nextScale) }
                        if (nextScale <= 1.01f) {
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            val maxX = size.width * (nextScale - 1f) / 2f
                            val maxY = size.height * (nextScale - 1f) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseScale = minOf(size.width / 1000f, size.height / 780f)
                val dx = (size.width - 1000f * baseScale) / 2f
                val dy = (size.height - 780f * baseScale) / 2f
                val isFocused = selectedOrigin.isNotBlank()

                withTransform({
                    translate(left = size.width / 2f + offsetX, top = size.height / 2f + offsetY)
                    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    translate(left = -size.width / 2f + dx, top = -size.height / 2f + dy)
                    scale(scaleX = baseScale, scaleY = baseScale, pivot = Offset.Zero)
                }) {
                    val provinceFill = Color(0xFFFFFFFF)
                    val provinceStroke = Color(0xFFE3DDD9)
                    for (path in parsedPaths) {
                        drawPath(path = path, color = provinceFill)
                        drawPath(
                            path = path,
                            color = provinceStroke,
                            style = Stroke(width = 0.95f / scale)
                        )
                    }

                    // 1. 轻盈贝塞尔航迹弧线（概览柔化降噪，聚焦时流光高亮）
                    for (route in displayRoutes) {
                        val fromMeta = metaMap[route.fromCity] ?: continue
                        val toMeta = metaMap[route.toCity] ?: continue
                        val x1 = projectX(fromMeta.lng)
                        val y1 = projectY(fromMeta.lat)
                        val x2 = projectX(toMeta.lng)
                        val y2 = projectY(toMeta.lat)

                        val mx = (x1 + x2) / 2f
                        val my = (y1 + y2) / 2f
                        val vx = x2 - x1
                        val vy = y2 - y1
                        val dist = kotlin.math.hypot(vx, vy)
                        val bend = (dist * 0.14f).coerceAtMost(44f)
                        val nx = if (dist > 1f) -vy / dist else 0f
                        val ny = if (dist > 1f) vx / dist else 0f
                        val sign = if (ny <= 0f) 1f else -1f

                        arcPath.reset()
                        arcPath.moveTo(x1, y1)
                        arcPath.quadraticTo(mx + nx * bend * sign, my + ny * bend * sign, x2, y2)

                        val lineColor = when {
                            isFocused -> hubColorMap[selectedOrigin] ?: p.primary
                            route.fromCity in topHubsSet -> hubColorMap[route.fromCity] ?: p.primary
                            else -> hubColorMap[route.toCity] ?: p.primary
                        }
                        if (isFocused) {
                            drawPath(
                                path = arcPath,
                                color = lineColor.copy(alpha = 0.16f),
                                style = Stroke(width = 4.2f / scale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                            drawPath(
                                path = arcPath,
                                color = lineColor.copy(alpha = 0.86f),
                                style = Stroke(width = 1.75f / scale, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        } else {
                            val isMajor = route.fromCity in topHubsSet || route.toCity in topHubsSet
                            drawPath(
                                path = arcPath,
                                color = lineColor.copy(alpha = if (isMajor) 0.30f else 0.16f),
                                style = Stroke(width = (if (isMajor) 1.15f else 0.85f) / scale)
                            )
                        }
                    }

                    // 2. 城市节点圆点（区分主枢纽环点与次级航点）
                    for (city in activeCities) {
                        val meta = metaMap[city] ?: continue
                        val cx = projectX(meta.lng)
                        val cy = projectY(meta.lat)
                        val isSelected = city == selectedOrigin
                        val isMajorHub = city in topHubsSet
                        val dotColor = when {
                            isSelected -> hubColorMap[city] ?: p.primary
                            isMajorHub -> hubColorMap[city] ?: p.primary
                            isFocused -> (hubColorMap[selectedOrigin] ?: p.primary).copy(alpha = 0.85f)
                            else -> Color(0xFF64748B)
                        }
                        val radius = when {
                            isSelected -> 6.4f / scale
                            isMajorHub -> 4.3f / scale
                            isFocused -> 3.4f / scale
                            else -> 2.1f / scale
                        }

                        if (isSelected) {
                            drawCircle(
                                color = dotColor.copy(alpha = 0.22f),
                                radius = radius * 2.1f,
                                center = Offset(cx, cy)
                            )
                        }
                        if (isSelected || isMajorHub || isFocused) {
                            drawCircle(color = Color.White, radius = radius + 1.5f / scale, center = Offset(cx, cy))
                        }
                        drawCircle(color = dotColor, radius = radius, center = Offset(cx, cy))
                    }

                    // 3. 碰撞避让地名标注（缩放/聚焦自动揭露更多城市，概览零重叠）
                    placedRects.clear()
                    for (city in sortedLabelCities) {
                        val meta = metaMap[city] ?: continue
                        val isSelected = city == selectedOrigin
                        val isMajor = city in topLabelHubsSet
                        val eligible = isSelected || isMajor || scale >= 1.45f || (isFocused && activeCities.size <= 24)
                        if (!eligible) continue

                        val cx = projectX(meta.lng)
                        val cy = projectY(meta.lat)
                        val labelSize = when {
                            isSelected -> 20f / scale
                            isMajor -> 15.5f / scale
                            else -> 12f / scale
                        }
                        val yOffset = if (isSelected || isMajor) -9.5f / scale else -6.5f / scale

                        haloPaint.textSize = labelSize
                        haloPaint.strokeWidth = 3.4f / scale
                        haloPaint.typeface = if (isSelected || isMajor) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

                        textPaint.textSize = labelSize
                        textPaint.typeface = if (isSelected || isMajor) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        textPaint.color = when {
                            isSelected || isMajor -> (hubColorMap[city] ?: p.primary).toArgb()
                            else -> Color(0xFF334155).toArgb()
                        }

                        val w = textPaint.measureText(city)
                        val pad = 5f / scale
                        val rect = android.graphics.RectF(
                            cx - w / 2f - pad,
                            cy + yOffset - labelSize,
                            cx + w / 2f + pad,
                            cy + yOffset + 2f / scale
                        )
                        if (!isSelected && placedRects.any { android.graphics.RectF.intersects(it, rect) }) {
                            continue
                        }
                        placedRects.add(rect)
                        drawContext.canvas.nativeCanvas.drawText(city, cx, cy + yOffset, haloPaint)
                        drawContext.canvas.nativeCanvas.drawText(city, cx, cy + yOffset, textPaint)
                    }
                }
            }

            // 悬浮于地图右上角的紧凑缩放胶囊条
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(0.6.dp, p.border),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    SmallMapCtrlBtn(Icons.Default.Add, "放大") {
                        scope.launch {
                            scaleAnim.animateTo((scaleAnim.value * 1.4f).coerceAtMost(4.5f), AppMotion.reveal())
                        }
                    }
                    SmallMapCtrlBtn(Icons.Default.Remove, "缩小") {
                        scope.launch {
                            val next = (scaleAnim.value / 1.4f).coerceAtLeast(1f)
                            if (next <= 1.02f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            scaleAnim.animateTo(next, AppMotion.reveal())
                        }
                    }
                    if (isZoomedOrPanned) {
                        SmallMapCtrlBtn(Icons.Default.CenterFocusStrong, "重置") {
                            offsetX = 0f
                            offsetY = 0f
                            scope.launch {
                                scaleAnim.animateTo(1f, AppMotion.reveal())
                            }
                        }
                    }
                }
            }
        }

        if (quickHubs.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                quickHubs.forEach { hub ->
                    val isSel = (hub == "全部" && selectedOrigin.isBlank()) || hub == selectedOrigin
                    val pillBg by animateColorAsState(
                        targetValue = if (isSel) p.primarySoftBg else p.subtleSurface,
                        animationSpec = AppMotion.fade(),
                        label = "hubBg_$hub"
                    )
                    val pillFg by animateColorAsState(
                        targetValue = if (isSel) p.primary else p.textSecondary,
                        animationSpec = AppMotion.fade(),
                        label = "hubFg_$hub"
                    )
                    Row(
                        modifier = Modifier
                            .pressScale(
                                scaleDown = AppPressScale.CONTROL,
                                haptic = true,
                                onClick = { onSelectQuickHub(hub) }
                            )
                            .clip(RoundedCornerShape(AppRadii.sm))
                            .background(pillBg)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        hubColorMap[hub]?.let { c ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(c)
                            )
                        }
                        Text(
                            text = hub,
                            style = LoongType.footnoteStrong,
                            color = pillFg
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallMapCtrlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .pressScale(scaleDown = AppPressScale.ICON, haptic = true, onClick = onClick)
            .clip(RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(16.dp), tint = p.textPrimary)
    }
}

@Composable
private fun NetworkRouteDetailCard(
    route: NetworkRouteSummary,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    SoftCard(
        modifier = Modifier.pressScale(scaleDown = AppPressScale.CARD, haptic = true, onClick = onClick),
        cornerRadius = AppRadii.card,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "${route.fromCity} → ${route.toCity}",
                    style = LoongType.headline,
                    color = p.textPrimary
                )
                Text(
                    "${route.fromAirport} (${route.fromIata}) → ${route.toAirport} (${route.toIata})",
                    style = LoongType.footnote,
                    color = p.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (route.isCalendarSynced && route.availableDaysCount == 0 && route.soldOutDaysCount > 0) {
                TonePillBadge(
                    text = "近期全约满",
                    backgroundColor = p.warningBg,
                    contentColor = p.warning,
                    cornerRadius = 8.dp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "查看近40天余票",
                tint = p.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (route.flights.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.subtleSurface)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    route.flights.forEachIndexed { idx, f ->
                        if (idx > 0) HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                        val freqLabel = RoundTripMatcher.formatChineseFrequency(f.frequency)
                        val cleanFlightNo = LoongAirApi.decodeFlightNo(f.flightNo)
                            ?: f.flightNo.takeUnless { it.contains("&") }
                            ?: "GJ班次"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${f.depTime}-${f.arrTime}",
                                style = LoongType.tabularMeta.copy(fontWeight = FontWeight.Bold),
                                color = p.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = cleanFlightNo,
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
                                text = "${f.stopover} · ${f.aircraft.ifBlank { "A320" }}",
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
        }
    }
}

private fun projectX(lng: Double): Float =
    (40.0 + ((lng - 73.0) / 62.0) * 920.0).toFloat()

private fun projectY(lat: Double): Float =
    (40.0 + ((53.5 - lat) / 35.5) * 700.0).toFloat()

private fun parseSvgPathToComposePath(svgPath: String): Path {
    val path = Path()
    val tokens = svgPath.trim().split(Regex("\\s+"))
    var i = 0
    while (i < tokens.size) {
        when (tokens[i]) {
            "M" -> {
                val xy = tokens.getOrNull(i + 1)?.split(",")
                if (xy != null && xy.size == 2) {
                    val x = xy[0].toFloatOrNull()
                    val y = xy[1].toFloatOrNull()
                    if (x != null && y != null) path.moveTo(x, y)
                }
                i += 2
            }
            "L" -> {
                val xy = tokens.getOrNull(i + 1)?.split(",")
                if (xy != null && xy.size == 2) {
                    val x = xy[0].toFloatOrNull()
                    val y = xy[1].toFloatOrNull()
                    if (x != null && y != null) path.lineTo(x, y)
                }
                i += 2
            }
            "Z", "z" -> {
                path.close()
                i += 1
            }
            else -> i += 1
        }
    }
    return path
}
