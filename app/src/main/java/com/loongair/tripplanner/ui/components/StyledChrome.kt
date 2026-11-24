package com.loongair.tripplanner.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loongair.tripplanner.data.local.OfficialAirportEntity
import com.loongair.tripplanner.data.local.UserSessionState
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.AppThemeStyle
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.bounceOnTouch
import com.loongair.tripplanner.ui.theme.pressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max

fun todayChineseHeaderDate(): String {
    val today = LocalDate.now()
    val week = when (today.dayOfWeek.value) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }
    return "${today.monthValue}月${today.dayOfMonth}日 $week"
}

/**
 * 计算与 chher-public CompactBarProgress 对应的滚动收起进度 (0f..1f)
 */
@Composable
fun rememberHeaderCollapseProgress(
    listState: LazyListState,
    collapseDistanceDp: Dp = 76.dp
): Float {
    val density = LocalDensity.current
    val collapsePx = remember(density, collapseDistanceDp) {
        with(density) { collapseDistanceDp.toPx() }.coerceAtLeast(1f)
    }
    val progress by remember(listState, collapsePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapsePx).coerceIn(0f, 1f)
        }
    }
    return progress
}

/**
 * 仿 chher-public LacquerTabScaffold 下拉阻尼拉伸 + 手绘字标刷新动画容器：
 * - 列表滚到顶部继续下拉时触发平滑橡皮筋阻尼（TabChromeTokens: maxPull=148dp, resistance=170dp, hold=68dp）
 * - 下拉过程中随距离逐笔描出「LOONG」圆头字标，过阈值轻触震动
 * - 松手后即使页面无需网络刷新，也会优雅停驻并循环演绎一段字标刷新动效，随后弹簧回弹归零
 * - 拉伸区自动延展朱砂漆面或薄暮双雾光背景，杜绝顶部露白断层
 */
@Composable
fun LacquerPullRefreshBox(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onRefresh: (suspend () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalLoongPalette.current
    val isMist = p.style == AppThemeStyle.MIST
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val maxPullPx = with(density) { 148.dp.toPx() }
    val resistancePx = with(density) { 170.dp.toPx() }
    val holdPx = with(density) { 68.dp.toPx() }

    val pullAnim = remember { Animatable(0f) }
    var rawPull by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var hapticSent by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loaderFade by remember { mutableFloatStateOf(1f) }

    val loaderAlpha by animateFloatAsState(
        targetValue = loaderFade,
        animationSpec = AppMotion.fade(110),
        label = "pullLoaderAlpha"
    )

    val isAtTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    val nestedScrollConnection = remember(maxPullPx, resistancePx, holdPx, onRefresh) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 已经处于下拉拉伸状态时，向上推先平滑收回拉距，不抢占滚动
                if (source == NestedScrollSource.UserInput && available.y < 0f && rawPull > 0f && !isRefreshing) {
                    val nextRaw = max(0f, rawPull + available.y)
                    val nextPull = maxPullPx * nextRaw / (resistancePx + nextRaw)
                    val consumedY = nextRaw - rawPull
                    rawPull = nextRaw
                    armed = nextPull >= holdPx
                    scope.launch { pullAnim.snapTo(nextPull) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 到达顶部后继续下拉 -> 触发橡皮筋阻尼拉距
                if (source == NestedScrollSource.UserInput && available.y > 0f && isAtTop && !isRefreshing) {
                    val nextRaw = rawPull + available.y
                    val nextPull = maxPullPx * nextRaw / (resistancePx + nextRaw)
                    rawPull = nextRaw
                    val nowArmed = nextPull >= holdPx
                    if (nowArmed && !hapticSent) {
                        hapticSent = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    armed = nowArmed
                    scope.launch { pullAnim.snapTo(nextPull) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (rawPull > 0f || (pullAnim.value > 0f && !isRefreshing)) {
                    val shouldTrigger = armed && !isRefreshing
                    rawPull = 0f
                    armed = false
                    hapticSent = false
                    if (shouldTrigger) {
                        isRefreshing = true
                        loaderFade = 1f
                        scope.launch {
                            try {
                                val startMs = System.currentTimeMillis()
                                val settleJob = launch { pullAnim.animateTo(holdPx, AppMotion.tap()) }
                                runCatching { onRefresh?.invoke() }
                                settleJob.join()
                                // 干脆利落的停驻回弹：无网络刷新时仅短促停顿展示一笔写完即收起
                                val elapsed = System.currentTimeMillis() - startMs
                                val minHoldMs = 360L
                                if (elapsed < minHoldMs) delay(minHoldMs - elapsed)
                                loaderFade = 0f
                                delay(70L)
                            } finally {
                                isRefreshing = false
                                pullAnim.animateTo(0f, AppMotion.tap())
                                loaderFade = 1f
                            }
                        }
                    } else if (!isRefreshing) {
                        scope.launch {
                            pullAnim.animateTo(0f, AppMotion.tap())
                        }
                    }
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
        }
    }

    val pullPx = pullAnim.value
    val pullDp = with(density) { pullPx.toDp() }
    val pullProgress = (pullPx / holdPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.pageBg)
            .nestedScroll(nestedScrollConnection)
    ) {
        // 下拉时顶部延展的天空层（朱砂延续漆面顶色 / 薄暮延续蓝紫柔光），杜绝露底断层
        if (pullPx > 0.5f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(pullDp + 84.dp)
                    .drawBehind {
                        if (isMist) {
                            drawRect(color = p.pageBg)
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(p.mistGlowLeft, p.mistGlowLeft.copy(alpha = 0f)),
                                    center = Offset(0f, 0f),
                                    radius = size.width * 1.05f
                                )
                            )
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(p.mistGlowRight, p.mistGlowRight.copy(alpha = 0f)),
                                    center = Offset(size.width, 0f),
                                    radius = size.width * 0.85f
                                )
                            )
                        } else {
                            drawRect(color = p.heroTop)
                        }
                    }
            )
            // 顶部覆盖状态栏区域的漆面色
            if (!isMist) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(p.heroTop)
                )
            }
        }

        // 随下拉整体平滑下移的页面主体（保持原有 LazyColumn 滚动与几何不变）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pullPx },
            content = content
        )

        // 下拉露出的「LOONG」手绘字标刷新动效（参考 chher-public ChhersLoader）
        if (pullPx > 1f) {
            val loaderColor = when {
                !isMist -> p.heroText
                armed || isRefreshing -> p.primary
                else -> p.textTertiary
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(pullDp)
                    .clipToBounds()
                    .graphicsLayer { alpha = loaderAlpha },
                contentAlignment = Alignment.Center
            ) {
                LoongWordmarkLoader(
                    isRefreshing = isRefreshing,
                    pullProgress = pullProgress,
                    color = loaderColor
                )
            }
        }
    }
}

/**
 * 复刻 chher-public ChhersLoader 逐笔手绘字标动效（LOONG 极简圆头几何笔划）：
 * - 下拉阶段：随拉距 0..1 连续逐笔描出「LOONG」五字母
 * - 刷新阶段：流畅循环书写 + 呼吸淡出（1350ms 周期）
 */
@Composable
private fun LoongWordmarkLoader(
    isRefreshing: Boolean,
    pullProgress: Float,
    color: Color
) {
    val strokes = remember { buildLoongWordmarkStrokes() }
    val totalLength = remember(strokes) { strokes.sumOf { it.length.toDouble() }.toFloat().coerceAtLeast(1f) }

    val transition = rememberInfiniteTransition(label = "loongLoaderLoop")
    val loopPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loongLoopPhase"
    )

    // 刷新循环中：0..0.72 轻快写完，0.72..0.86 短暂停留，0.86..1.0 淡出
    val cycleAlpha = if (!isRefreshing) {
        (0.28f + 0.72f * pullProgress).coerceIn(0f, 1f)
    } else {
        when {
            loopPhase <= 0.82f -> 1f
            loopPhase <= 0.96f -> (1f - (loopPhase - 0.82f) / 0.14f).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    val drawProgress = if (!isRefreshing) {
        pullProgress
    } else {
        FastOutSlowInEasing.transform((loopPhase / 0.72f).coerceIn(0f, 1f))
    }

    val scratchPath = remember { Path() }

    Canvas(
        modifier = Modifier
            .width(104.dp)
            .height(22.dp)
            .graphicsLayer { alpha = cycleAlpha }
    ) {
        val viewW = 114f
        val viewH = 26f
        val scale = minOf(size.width / viewW, size.height / viewH)
        val dx = (size.width - viewW * scale) / 2f
        val dy = (size.height - viewH * scale) / 2f
        val strokeW = 2.35.dp.toPx() / scale

        val targetLen = drawProgress * totalLength
        var drawn = 0f

        withTransform({
            translate(left = dx, top = dy)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            val style = Stroke(
                width = strokeW,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            for (seg in strokes) {
                val remain = targetLen - drawn
                if (remain <= 0f) break
                if (remain >= seg.length) {
                    drawPath(path = seg.path, color = color, style = style)
                } else {
                    scratchPath.reset()
                    seg.measure.getSegment(0f, remain, scratchPath, true)
                    drawPath(path = scratchPath, color = color, style = style)
                }
                drawn += seg.length
            }
        }
    }
}

private data class WordmarkStroke(
    val path: Path,
    val measure: PathMeasure,
    val length: Float
)

private fun buildLoongWordmarkStrokes(): List<WordmarkStroke> {
    // 114 × 26 坐标空间内的精致圆头几何「LOONG」五笔划
    val rawPaths = listOf(
        // L
        Path().apply {
            moveTo(4f, 3f)
            lineTo(4f, 22f)
            lineTo(17f, 22f)
        },
        // O (1)
        Path().apply {
            addOval(Rect(left = 23f, top = 5f, right = 40f, bottom = 22f))
        },
        // O (2)
        Path().apply {
            addOval(Rect(left = 47f, top = 5f, right = 64f, bottom = 22f))
        },
        // N
        Path().apply {
            moveTo(71f, 22f)
            lineTo(71f, 5f)
            lineTo(86f, 22f)
            lineTo(86f, 5f)
        },
        // G（逆时针描完外圆至 3 点钟方向后水平向内收笔）
        Path().apply {
            arcTo(
                rect = Rect(left = 93f, top = 5f, right = 110f, bottom = 22f),
                startAngleDegrees = -42f,
                sweepAngleDegrees = -318f,
                forceMoveTo = true
            )
            lineTo(101f, 13.5f)
        }
    )
    return rawPaths.map { path ->
        val m = PathMeasure().apply { setPath(path, false) }
        WordmarkStroke(path = path, measure = m, length = m.length)
    }
}

/**
 * 仿 chher-public LacquerTabScaffold 沉浸式顶栏 + 跨缝浮卡容器：
 * - 朱砂主题：三段朱漆渐变 (#C62F2F -> #AF2025 55% -> #94151E) + 5.5% 奶白大水印 + 26dp 纸面圆角接缝
 * - 薄暮主题：柔和蓝紫双径向雾光 (#E3EBF8 + #EEE7F6) 自然融入雾白纸面
 */
@Composable
fun HeroHeaderOverlapContainer(
    subtitle: String,
    title: String,
    watermark: String = "LOONG",
    collapseProgress: Float = 0f,
    trailingAction: (@Composable BoxScope.() -> Unit)? = null,
    heroExtraContent: (@Composable ColumnScope.() -> Unit)? = null,
    overlappingCard: @Composable () -> Unit
) {
    val p = LocalLoongPalette.current
    val isMist = p.style == AppThemeStyle.MIST
    val largeTitleAlpha = (1f - (collapseProgress / 0.55f)).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isMist) {
                        drawRect(color = p.pageBg)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(p.mistGlowLeft, p.mistGlowLeft.copy(alpha = 0f)),
                                center = Offset(0f, 0f),
                                radius = size.width * 1.05f
                            )
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(p.mistGlowRight, p.mistGlowRight.copy(alpha = 0f)),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.85f
                            )
                        )
                    } else {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to p.heroTop,
                                    0.55f to p.heroMid,
                                    1.0f to p.heroBottom
                                )
                            )
                        )
                    }
                }
        ) {
            if (!isMist) {
                Text(
                    text = watermark,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-5).sp,
                    lineHeight = 112.sp,
                    color = p.heroWatermark,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 14.dp, y = (-4).dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = AppSpace.screen,
                        end = AppSpace.screen,
                        top = 14.dp,
                        bottom = if (isMist) 64.dp else 72.dp
                    )
                    .alpha(largeTitleAlpha)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = LoongType.footnoteStrong,
                                color = p.heroSubText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            text = title,
                            style = LoongType.largeTitle,
                            color = p.heroText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (trailingAction != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(contentAlignment = Alignment.Center, content = trailingAction)
                    }
                }
                if (heroExtraContent != null) {
                    Spacer(Modifier.height(14.dp))
                    heroExtraContent()
                }
            }

            // 朱砂漆面底部 26dp 纸面上浮圆角接缝
            if (!isMist) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = AppRadii.paper, topEnd = AppRadii.paper))
                        .background(p.pageBg)
                )
            }
        }

        val overlapTop = when {
            heroExtraContent != null -> 168.dp
            subtitle.isNotBlank() -> 106.dp
            else -> 92.dp
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = overlapTop)
                .padding(horizontal = AppSpace.groupInset)
        ) {
            overlappingCard()
        }
    }
}

/**
 * 滚动折叠后的顶部紧凑导航条（复刻 chher-public _CompactBackground 反向凹圆角）：
 * - 在顶栏底边左右向下延伸 20dp 反向弧角，使下方滚动的宣纸页始终保持圆角上滑观感，绝不平直截断！
 */
@Composable
fun CompactScrollTopBar(
    title: String,
    collapseProgress: Float,
    modifier: Modifier = Modifier,
    reverseRadius: Dp = 20.dp,
    trailingAction: (@Composable BoxScope.() -> Unit)? = null
) {
    val p = LocalLoongPalette.current
    val isMist = p.style == AppThemeStyle.MIST
    val bgAlpha = ((collapseProgress - 0.28f) / 0.40f).coerceIn(0f, 1f)
    val titleAlpha by animateFloatAsState(
        targetValue = if (collapseProgress >= 0.58f) 1f else 0f,
        animationSpec = AppMotion.reveal(),
        label = "compactTitleAlpha"
    )

    if (bgAlpha <= 0.01f && titleAlpha <= 0.01f) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = bgAlpha }
            .drawBehind {
                val r = reverseRadius.toPx()
                val bottom = (size.height - r).coerceAtLeast(0f)
                // 精确复刻 chher-public _CompactBackground：底边左右向下带二次贝塞尔反圆角
                val archPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, bottom + r)
                    quadraticTo(size.width, bottom, size.width - r, bottom)
                    lineTo(r, bottom)
                    quadraticTo(0f, bottom, 0f, bottom + r)
                    close()
                }
                if (isMist) {
                    drawPath(
                        path = archPath,
                        color = p.pageBg.copy(alpha = 0.96f)
                    )
                    // 薄暮顶栏底部柔和径向光带，与双雾光保持一体
                    drawPath(
                        path = archPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                p.mistGlowLeft.copy(alpha = 0.45f),
                                p.mistGlowRight.copy(alpha = 0.35f)
                            )
                        )
                    )
                } else {
                    drawPath(
                        path = archPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(p.heroTop, p.heroMid),
                            startY = 0f,
                            endY = bottom + r
                        )
                    )
                }
            }
            .statusBarsPadding()
            .padding(bottom = reverseRadius)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppSpace.navBarHeight)
                .padding(horizontal = AppSpace.screen)
        ) {
            Text(
                text = title,
                style = LoongType.headline,
                color = if (isMist) p.textPrimary else p.heroText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = (1f - titleAlpha) * 12f
                    }
            )
            if (trailingAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { alpha = titleAlpha },
                    contentAlignment = Alignment.Center,
                    content = trailingAction
                )
            }
        }
    }
}

/**
 * 精确复刻 chher-public 的两档高斯柔影 (shadowE1 / shadowE2)：
 * 直接以 BlurMaskFilter 绘制贴合圆角路径的双层扩散投影，摆脱 Android 原生 elevation 偏淡问题，
 * 让纯白卡片在暖宣纸 / 雾纸底色上呈现鲜明而柔和的立体悬浮层次。
 */
fun Modifier.chhersShadow(
    cornerRadius: Dp,
    floating: Boolean = false,
    tint: Color
): Modifier = this.drawBehind {
    val maxRadius = minOf(size.width, size.height) / 2f
    val radiusPx = cornerRadius.toPx().coerceAtMost(maxRadius)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    if (floating) {
        // shadowE2：跨缝主浮卡 / 地图卡 / 胶囊提示 — 贴边轻影 + 深层大扩散悬浮光晕
        drawSoftShadowLayer(
            paint = paint,
            radiusPx = radiusPx,
            offsetY = 2.dp.toPx(),
            blurPx = 5.dp.toPx(),
            spreadPx = (-0.5).dp.toPx(),
            color = tint.copy(alpha = 0.09f)
        )
        drawSoftShadowLayer(
            paint = paint,
            radiusPx = radiusPx,
            offsetY = 10.dp.toPx(),
            blurPx = 22.dp.toPx(),
            spreadPx = (-1.5).dp.toPx(),
            color = tint.copy(alpha = 0.16f)
        )
    } else {
        // shadowE1：普通分组卡 / 航线卡 / 预览卡 / 圆形返回键
        drawSoftShadowLayer(
            paint = paint,
            radiusPx = radiusPx,
            offsetY = 1.5.dp.toPx(),
            blurPx = 3.5.dp.toPx(),
            spreadPx = 0f,
            color = tint.copy(alpha = 0.07f)
        )
        drawSoftShadowLayer(
            paint = paint,
            radiusPx = radiusPx,
            offsetY = 5.dp.toPx(),
            blurPx = 14.dp.toPx(),
            spreadPx = (-1).dp.toPx(),
            color = tint.copy(alpha = 0.12f)
        )
    }
}

private fun DrawScope.drawSoftShadowLayer(
    paint: android.graphics.Paint,
    radiusPx: Float,
    offsetY: Float,
    blurPx: Float,
    spreadPx: Float,
    color: Color
) {
    if (blurPx <= 0.5f) return
    paint.color = color.toArgb()
    paint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            -spreadPx,
            offsetY - spreadPx,
            size.width + spreadPx,
            size.height + offsetY + spreadPx,
            radiusPx,
            radiusPx,
            paint
        )
    }
}

/**
 * 顶栏玻璃圆钮（参考 chher-public ChromeButton：44dp 命中区、38dp 视觉圆、20dp 图标、按压弹性缩放）
 */
@Composable
fun HeroCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    val isMist = p.style == AppThemeStyle.MIST
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .then(
                    if (isMist) {
                        Modifier.chhersShadow(
                            cornerRadius = 19.dp,
                            floating = false,
                            tint = p.shadowTint
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(p.heroChipBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = p.heroText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 纸面柔影卡片（参考 chher-public HomeTokens / MeTokens / SettingsTokens）
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AppRadii.card,
    borderColor: Color? = null,
    floating: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalLoongPalette.current
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .chhersShadow(
                cornerRadius = cornerRadius,
                floating = floating,
                tint = p.shadowTint
            ),
        shape = shape,
        color = p.cardBg,
        shadowElevation = 0.dp,
        border = borderColor?.let { BorderStroke(1.2.dp, it) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * 功能图标底块（参考 chher-public EntryIconTokens：32dp 方块、10dp 圆角、18dp 图标）
 */
@Composable
fun TintedIconBadge(
    icon: ImageVector,
    tint: Color? = null,
    background: Color? = null,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    cornerRadius: Dp = if (size <= 34.dp) 10.dp else if (size <= 46.dp) 12.dp else 18.dp
) {
    val p = LocalLoongPalette.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background ?: p.primarySoftBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: p.primary,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * 页面分区标题（参考 chher-public SectionHeader：20sp Bold -0.2sp + 右侧轻盈操作项）
 */
@Composable
fun SectionTitleRow(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = LocalLoongPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = LoongType.title2,
            color = p.textPrimary
        )
        if (actionText != null && onActionClick != null) {
            Row(
                modifier = Modifier
                    .pressScale(scaleDown = AppPressScale.CONTROL, onClick = onActionClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionText,
                    style = LoongType.filter,
                    color = p.primary
                )
                Spacer(Modifier.width(1.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = p.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 设置页/分组块眉标（参考 chher-public _SettingsSection：13sp SemiBold text3）
 */
@Composable
fun GroupedSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val p = LocalLoongPalette.current
    Text(
        text = text,
        style = LoongType.footnoteStrong,
        color = p.textTertiary,
        modifier = modifier.padding(start = 10.dp, bottom = 2.dp)
    )
}

/**
 * 胶囊标签（参考 chher-public Badge / Chip：统一 12sp SemiBold 与舒适内边距）
 */
@Composable
fun TonePillBadge(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val animBg by animateColorAsState(backgroundColor, AppMotion.fade(), label = "pillBg")
    val animFg by animateColorAsState(contentColor, AppMotion.fade(), label = "pillFg")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(animBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = LoongType.caption,
            color = animFg,
            maxLines = 1
        )
    }
}

/**
 * 首次使用引导锁屏卡（未登录或尚未同步任何城市航线时，取代航线/往返/行程业务内容）：
 * - 清晰展示两步初始化：1. 登录长龙会员识别畅飞卡 -> 2. 优先同步一个城市的所有航线
 * - 绝不附带任何本地预置城市或航线数据
 */
@Composable
fun FirstRunOnboardingGate(
    screenTitle: String,
    screenWatermark: String,
    featureHint: String,
    session: UserSessionState,
    officialAirports: List<OfficialAirportEntity>,
    departureAirportsLoading: Boolean,
    officialSyncing: Boolean,
    syncDoneCount: Int,
    syncTotalCount: Int,
    officialSyncProgress: String,
    onGoToLogin: () -> Unit,
    onRefreshAirports: () -> Unit,
    onSyncCityAllRoutes: (String) -> Unit,
    onStopSync: () -> Unit
) {
    val p = LocalLoongPalette.current
    val listState = rememberLazyListState()
    val collapseProgress = rememberHeaderCollapseProgress(listState)
    val isLoggedIn = session.isLoggedIn

    LacquerPullRefreshBox(
        listState = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpace.cardGap)
        ) {
            item {
                HeroHeaderOverlapContainer(
                    subtitle = if (isLoggedIn) "还差一步 · 同步首个城市" else todayChineseHeaderDate(),
                    title = screenTitle,
                    watermark = screenWatermark,
                    collapseProgress = collapseProgress
                ) {
                    SoftCard(
                        cornerRadius = AppRadii.card,
                        floating = true,
                        contentPadding = 18.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TintedIconBadge(
                                icon = if (isLoggedIn) Icons.Default.TravelExplore else Icons.Default.LockOpen,
                                size = 42.dp,
                                iconSize = 21.dp,
                                cornerRadius = 13.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isLoggedIn) "先同步一座城市" else "登录并同步后使用",
                                    style = LoongType.headline.copy(fontSize = 17.5.sp),
                                    color = p.textPrimary
                                )
                                Text(
                                    text = featureHint,
                                    style = LoongType.footnote,
                                    color = p.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // 精简两步清单
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(p.subtleSurface)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OnboardingStepRow(
                                stepNumber = 1,
                                title = "登录长龙会员",
                                subtitle = if (isLoggedIn) "${session.userName} · ${session.cardName}" else "",
                                completed = isLoggedIn,
                                active = !isLoggedIn
                            )
                            HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                            OnboardingStepRow(
                                stepNumber = 2,
                                title = "优先同步一个城市的全部航线",
                                subtitle = "",
                                completed = false,
                                active = isLoggedIn
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (!isLoggedIn) {
                            Button(
                                onClick = onGoToLogin,
                                shape = RoundedCornerShape(AppRadii.full),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .bounceOnTouch()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "前往「我的」登录",
                                    style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        } else {
                            FirstCitySyncPanel(
                                officialAirports = officialAirports,
                                departureAirportsLoading = departureAirportsLoading,
                                officialSyncing = officialSyncing,
                                syncDoneCount = syncDoneCount,
                                syncTotalCount = syncTotalCount,
                                officialSyncProgress = officialSyncProgress,
                                onRefreshAirports = onRefreshAirports,
                                onSyncCityAllRoutes = onSyncCityAllRoutes,
                                onStopSync = onStopSync
                            )
                        }
                    }
                }
            }
        }

        CompactScrollTopBar(
            title = screenTitle,
            collapseProgress = collapseProgress
        )
    }
}

@Composable
private fun OnboardingStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    completed: Boolean,
    active: Boolean
) {
    val p = LocalLoongPalette.current
    val badgeBg = when {
        completed -> p.successBg
        active -> p.primarySoftBg
        else -> p.cardBg
    }
    val badgeFg = when {
        completed -> p.success
        active -> p.primary
        else -> p.textTertiary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已完成",
                    tint = badgeFg,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "$stepNumber",
                    style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                    color = badgeFg
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = LoongType.filter.copy(fontWeight = FontWeight.SemiBold),
                color = if (active || completed) p.textPrimary else p.textSecondary
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = LoongType.footnote.copy(fontSize = 12.sp),
                    color = if (completed) p.success else p.textTertiary
                )
            }
        }
    }
}

/**
 * 优先同步「单个城市全部航线」的交互面板（可复用于引导锁屏、「我的」页首启浮卡及扩展同步弹窗）：
 * - 展示从长龙官方实时拉取的可选出发城市（若有），同时支持直接输入城市名或机场三字码
 * - 实时展示逐条同步进度与随时停止按钮
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstCitySyncPanel(
    officialAirports: List<OfficialAirportEntity>,
    departureAirportsLoading: Boolean,
    officialSyncing: Boolean,
    syncDoneCount: Int,
    syncTotalCount: Int,
    officialSyncProgress: String,
    onRefreshAirports: () -> Unit,
    onSyncCityAllRoutes: (String) -> Unit,
    onStopSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalLoongPalette.current
    var cityInput by remember { mutableStateOf("") }
    var showFullDirectoryDialog by remember { mutableStateOf(false) }

    val resolvedCity = remember(cityInput, officialAirports) {
        val q = cityInput.trim()
        if (q.isBlank()) ""
        else {
            val fromOfficial = officialAirports.firstOrNull {
                it.cityName.equals(q, ignoreCase = true) ||
                    it.airportCode.equals(q, ignoreCase = true) ||
                    it.pinyin.equals(q, ignoreCase = true)
            }?.cityName
            fromOfficial ?: CityIataMapper.toCityName(q)
        }
    }

    val filteredAirports = remember(officialAirports, cityInput) {
        val q = cityInput.trim()
        val source = if (q.isBlank()) {
            officialAirports
        } else {
            officialAirports.filter {
                it.cityName.contains(q, ignoreCase = true) ||
                    it.airportCode.contains(q, ignoreCase = true) ||
                    it.pinyin.contains(q, ignoreCase = true)
            }
        }
        source.take(18)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = cityInput,
            onValueChange = { cityInput = it },
            label = { Text("出发城市（中文/拼音/三字码）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text("输入中文、拼音或三字码") },
            singleLine = true,
            enabled = !officialSyncing,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (resolvedCity.isNotBlank() && !officialSyncing) {
                        onSyncCityAllRoutes(resolvedCity)
                    }
                }
            ),
            trailingIcon = {
                if (officialAirports.isNotEmpty() && !officialSyncing) {
                    IconButton(onClick = { showFullDirectoryDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "全部城市目录",
                            tint = p.primary
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (departureAirportsLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = p.primary)
                Text("正在获取长龙官方国内城市目录...", style = LoongType.footnote, color = p.textSecondary)
            }
        } else if (filteredAirports.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "官方城市目录 (${officialAirports.size}城)",
                    style = LoongType.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                    color = p.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (officialAirports.size > 18) {
                        Text(
                            "全部",
                            style = LoongType.caption,
                            color = p.primary,
                            modifier = Modifier
                                .pressScale(scaleDown = AppPressScale.CONTROL, onClick = { showFullDirectoryDialog = true })
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        "刷新",
                        style = LoongType.caption,
                        color = p.primary,
                        modifier = Modifier
                            .pressScale(scaleDown = AppPressScale.CONTROL, onClick = onRefreshAirports)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                filteredAirports.forEach { ap ->
                    val selected = resolvedCity.equals(ap.cityName, ignoreCase = true) ||
                        cityInput.trim().equals(ap.airportCode, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .pressScale(
                                scaleDown = AppPressScale.CONTROL,
                                haptic = true,
                                enabled = !officialSyncing,
                                onClick = { cityInput = ap.cityName }
                            )
                            .clip(RoundedCornerShape(AppRadii.sm))
                            .background(if (selected) p.primarySoftBg else p.subtleSurface)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${ap.cityName} ${ap.airportCode}",
                            style = LoongType.caption.copy(fontFeatureSettings = "tnum"),
                            color = if (selected) p.primary else p.textPrimary
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "支持输入中文、拼音或三字码，或点右侧拉取目录",
                    style = LoongType.footnote.copy(fontSize = 12.sp),
                    color = p.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onRefreshAirports,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("拉取城市目录", style = LoongType.caption)
                }
            }
        }

        if (officialSyncing) {
            LiveSyncProgressContent(
                done = syncDoneCount,
                total = syncTotalCount,
                progressText = officialSyncProgress,
                onStop = onStopSync
            )
        } else {
            Button(
                enabled = resolvedCity.isNotBlank(),
                onClick = { onSyncCityAllRoutes(resolvedCity) },
                shape = RoundedCornerShape(AppRadii.full),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bounceOnTouch(enabled = resolvedCity.isNotBlank())
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (resolvedCity.isBlank()) "请先输入或选择出发城市" else "优先同步「$resolvedCity」全部航线",
                    style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showFullDirectoryDialog) {
        var dirQuery by remember { mutableStateOf("") }
        val dirList = remember(officialAirports, dirQuery) {
            val q = dirQuery.trim()
            if (q.isBlank()) officialAirports
            else officialAirports.filter {
                it.cityName.contains(q, ignoreCase = true) ||
                    it.airportCode.contains(q, ignoreCase = true) ||
                    it.pinyin.contains(q, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showFullDirectoryDialog = false },
            containerColor = Color.White,
            title = { Text("选择出发城市 (${officialAirports.size})", style = LoongType.headline, color = p.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dirQuery,
                        onValueChange = { dirQuery = it },
                        placeholder = { Text("按中文、拼音或三字码筛选") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(dirList.size) { idx ->
                            val ap = dirList[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(p.subtleSurface)
                                    .pressScale(
                                        scaleDown = AppPressScale.CONTENT,
                                        haptic = true,
                                        onClick = {
                                            cityInput = ap.cityName
                                            showFullDirectoryDialog = false
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(ap.cityName, style = LoongType.filter, color = p.textPrimary)
                                    if (ap.airportName.isNotBlank()) {
                                        Text(ap.airportName, style = LoongType.caption.copy(fontWeight = FontWeight.Normal), color = p.textTertiary)
                                    }
                                }
                                Text(
                                    ap.airportCode,
                                    style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                                    color = p.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFullDirectoryDialog = false }) { Text("关闭") }
            }
        )
    }
}

/**
 * 紧凑型后台同步进度卡（用于阶段 2 逐条补全班次时刻与 60 天日历时，在各标签顶部平滑展示）
 */
@Composable
fun LiveSyncProgressBanner(
    done: Int,
    total: Int,
    progressText: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoftCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = 14.dp
    ) {
        LiveSyncProgressContent(
            done = done,
            total = total,
            progressText = progressText,
            onStop = onStop
        )
    }
}

@Composable
private fun LiveSyncProgressContent(
    done: Int,
    total: Int,
    progressText: String,
    onStop: () -> Unit
) {
    val p = LocalLoongPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(AppRadii.full)),
                color = p.primary,
                trackColor = p.primarySoftBg
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(AppRadii.full)),
                color = p.primary,
                trackColor = p.primarySoftBg
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = progressText.ifBlank { "正在同步官方航线..." },
                style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                color = p.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(AppRadii.full),
                border = BorderStroke(1.dp, p.danger.copy(alpha = 0.45f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .bounceOnTouch()
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = p.danger, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("停止", style = LoongType.caption, color = p.danger)
            }
        }
    }
}
