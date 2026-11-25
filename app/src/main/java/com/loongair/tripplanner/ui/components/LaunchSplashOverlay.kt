package com.loongair.tripplanner.ui.components

import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loongair.tripplanner.BuildConfig
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppThemeStyle
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2

/**
 * 全屏品牌启动加载页：
 * - 跟随「朱砂 / 薄暮」主题全屏边到边渐层铺底
 * - 航线圆弧动态描边 + 客机沿切线爬升入场 + 雷达脉冲波纹
 * - 底部流动胶囊加载条等待本地航网数据库就绪后平滑淡出
 */
@Composable
fun FullScreenLaunchSplash(
    isDataReady: Boolean,
    onDismissed: () -> Unit
) {
    val p = LocalLoongPalette.current
    val isMist = p.style == AppThemeStyle.MIST
    val latestReady by rememberUpdatedState(isDataReady)
    val latestDismiss by rememberUpdatedState(onDismissed)

    val arcProgress = remember { Animatable(0f) }
    val textReveal = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val arcJob = launch {
            arcProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 920, easing = FastOutSlowInEasing)
            )
        }
        val textJob = launch {
            textReveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
        // 最短展示 1350ms 让入场飞行动画完整走完，同时等待本地航网加载完毕（最长 2600ms 兜底）
        delay(1350L)
        if (!latestReady) {
            withTimeoutOrNull(1250L) {
                snapshotFlow { latestReady }.filter { it }.first()
            }
        }
        arcJob.join()
        textJob.join()
        exitProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
        )
        latestDismiss()
    }

    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPulse"
    )
    val loaderSlide by infinite.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loaderSlide"
    )

    val mainFg = if (isMist) p.textPrimary else Color(0xFFFFF8F5)
    val subFg = if (isMist) p.textSecondary else Color(0xFFF9E2DA)
    val emblemAccent = if (isMist) p.primary else Color(0xFFFFF8F5)
    val emblemSoft = if (isMist) p.primary.copy(alpha = 0.14f) else Color(0x22FFF8F5)

    val exitScale = 1f + 0.035f * exitProgress.value
    val exitAlpha = (1f - exitProgress.value).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha
                scaleX = exitScale
                scaleY = exitScale
            }
            .drawBehind {
                if (isMist) {
                    drawRect(color = p.pageBg)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(p.mistGlowLeft, p.mistGlowLeft.copy(alpha = 0f)),
                            center = Offset(size.width * 0.25f, size.height * 0.2f),
                            radius = size.width * 1.1f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(p.mistGlowRight, p.mistGlowRight.copy(alpha = 0f)),
                            center = Offset(size.width * 0.85f, size.height * 0.72f),
                            radius = size.width * 0.95f
                        )
                    )
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to p.heroTop,
                                0.52f to p.heroMid,
                                1.0f to Color(0xFF7D1017)
                            )
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x28FFF4EE), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.40f),
                            radius = size.width * 0.78f
                        )
                    )
                }
            }
    ) {
        // 背景淡水印（置于顶部留白处，不遮挡中心徽章）
        Text(
            text = "LOONG",
            fontSize = 112.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-5).sp,
            color = if (isMist) p.primary.copy(alpha = 0.04f) else Color(0x09FFF8F5),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        )

        // 中部：航线轨迹徽章 + 品牌标题
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // 1. 双层雷达扩散脉冲波纹
                    for (ringIdx in 0..1) {
                        val phase = (pulse + ringIdx * 0.5f) % 1f
                        val radius = size.minDimension * (0.28f + 0.21f * phase)
                        val ringAlpha = (1f - phase) * (if (isMist) 0.22f else 0.24f)
                        drawCircle(
                            color = emblemAccent.copy(alpha = ringAlpha),
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }

                    // 2. 中心圆角徽章底框
                    val boxSide = size.minDimension * 0.58f
                    val topLeft = Offset((size.width - boxSide) / 2f, (size.height - boxSide) / 2f)
                    drawRoundRect(
                        color = emblemSoft,
                        topLeft = topLeft,
                        size = Size(boxSide, boxSide),
                        cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx())
                    )
                    drawRoundRect(
                        color = emblemAccent.copy(alpha = 0.24f),
                        topLeft = topLeft,
                        size = Size(boxSide, boxSide),
                        cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // 3. 弧线航迹动态描边 + 客机沿切线爬升
                    val startX = size.width * 0.30f
                    val startY = size.height * 0.66f
                    val ctrlX = size.width * 0.42f
                    val ctrlY = size.height * 0.44f
                    val endX = size.width * 0.69f
                    val endY = size.height * 0.33f

                    val nativePath = AndroidPath().apply {
                        moveTo(startX, startY)
                        quadTo(ctrlX, ctrlY, endX, endY)
                    }
                    val pm = PathMeasure(nativePath, false)
                    val totalLen = pm.length
                    val curDist = totalLen * arcProgress.value.coerceIn(0.02f, 1f)

                    val segmentPath = AndroidPath()
                    pm.getSegment(0f, curDist, segmentPath, true)
                    drawPath(
                        path = segmentPath.asComposePath(),
                        color = emblemAccent.copy(alpha = 0.85f),
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 起点坐标圆点
                    drawCircle(
                        color = emblemAccent.copy(alpha = 0.4f),
                        radius = 5.dp.toPx(),
                        center = Offset(startX, startY)
                    )
                    drawCircle(
                        color = emblemAccent,
                        radius = 2.6.dp.toPx(),
                        center = Offset(startX, startY)
                    )

                    // 终点落点脉冲高光
                    if (arcProgress.value > 0.88f) {
                        val arriveAlpha = ((arcProgress.value - 0.88f) / 0.12f).coerceIn(0f, 1f)
                        drawCircle(
                            color = emblemAccent.copy(alpha = 0.35f * arriveAlpha),
                            radius = 6.dp.toPx() * arriveAlpha,
                            center = Offset(endX, endY)
                        )
                    }

                    // 客机坐标与切线角度（原向量指向正东 0°，旋转至弧线切线方向）
                    val pos = FloatArray(2)
                    val tan = FloatArray(2)
                    pm.getPosTan(curDist, pos, tan)
                    val angleDeg = Math.toDegrees(atan2(tan[1].toDouble(), tan[0].toDouble())).toFloat()

                    val planeScale = (size.minDimension / 165f) * (0.85f + 0.15f * arcProgress.value)
                    val jetPath = Path().apply {
                        // 机头朝右 (+X) 的简化均衡客机剪影
                        moveTo(16f, 0f)
                        lineTo(5f, -3.2f)
                        lineTo(-5f, -13.5f)
                        lineTo(-9.5f, -13.5f)
                        lineTo(-4.5f, -3.2f)
                        lineTo(-12.5f, -2.2f)
                        lineTo(-16.5f, -6.5f)
                        lineTo(-19f, -6.5f)
                        lineTo(-16.5f, 0f)
                        lineTo(-19f, 6.5f)
                        lineTo(-16.5f, 6.5f)
                        lineTo(-12.5f, 2.2f)
                        lineTo(-4.5f, 3.2f)
                        lineTo(-9.5f, 13.5f)
                        lineTo(-5f, 13.5f)
                        lineTo(5f, 3.2f)
                        close()
                    }
                    withTransform({
                        translate(left = pos[0], top = pos[1])
                        rotate(degrees = angleDeg, pivot = Offset.Zero)
                        scale(scaleX = planeScale, scaleY = planeScale, pivot = Offset.Zero)
                    }) {
                        drawPath(path = jetPath, color = emblemAccent)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.graphicsLayer {
                    alpha = textReveal.value
                    translationY = (1f - textReveal.value) * 28f
                }
            ) {
                Text(
                    text = "长龙畅飞规划",
                    style = LoongType.largeTitle.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = mainFg
                )
                Text(
                    text = "全国随心飞航网 · 往返配对 · 放票日历",
                    style = LoongType.footnote.copy(fontSize = 13.5.sp),
                    color = subFg
                )
            }
        }

        // 底部：胶囊加载进度条与状态字样
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .graphicsLayer { alpha = textReveal.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(AppRadii.full))
                    .background(emblemAccent.copy(alpha = 0.22f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val segW = size.width * 0.36f
                    val startX = size.width * loaderSlide
                    drawRoundRect(
                        color = emblemAccent,
                        topLeft = Offset(startX, 0f),
                        size = Size(segW, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
            }
            Text(
                text = if (isDataReady) "航网已就绪 · v${BuildConfig.VERSION_NAME}" else "正在载入本地航网与日历 · v${BuildConfig.VERSION_NAME}",
                style = LoongType.caption.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum"
                ),
                color = subFg.copy(alpha = 0.88f)
            )
        }
    }
}
