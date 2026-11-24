package com.loongair.tripplanner.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 参考 chher-public/lib/design/motion.dart 的五档具名弹簧与按压缩放规范：
 * 所有位移动效优先由物理弹簧驱动，不使用生硬的 Material Ripple。
 *
 * 弹簧换算公式 (CupertinoMotion duration + bounce -> Compose SpringSpec)：
 *   stiffness ≈ (2π / duration)^2,  dampingRatio ≈ 1 - bounce
 */
object AppMotion {
    /** 250ms, bounce 0 — 按钮/控件按压回弹、角标清零消失 */
    fun <T> tap(): SpringSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 630f
    )

    /** 230ms, bounce 0.14 — 快捷入口瓦片、轻快胶囊回弹 */
    fun <T> entryTap(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 740f
    )

    /** 340ms, bounce 0.18 — 底栏滑块、图标强调缩放、箭头旋转、选项切换 */
    fun <T> select(): SpringSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 340f
    )

    /** 380ms, bounce 0 — 二级页推入/弹出、卡片展开收起、顶栏小标题显隐 */
    fun <T> reveal(): SpringSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 275f
    )

    /** 380ms IntOffset 版本 — 供 AnimatedContent 页面滑入/滑出使用 */
    fun revealOffset(): SpringSpec<IntOffset> = spring(
        dampingRatio = 1.0f,
        stiffness = 275f
    )

    /** 420ms, bounce 0.14 — 弹窗、底部弹层、浮层揭示 */
    fun <T> sheet(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 225f
    )

    /** 300ms, bounce 0.30 — 选中勾号弹入、Toast 弹入、徽章出现 */
    fun <T> pop(): SpringSpec<T> = spring(
        dampingRatio = 0.70f,
        stiffness = 440f
    )

    /** 颜色与透明度淡变例外（对应 AppMotion.fade = 180ms / PressHighlight.fadeBack = 200ms） */
    fun <T> fade(durationMillis: Int = 180): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    )

    /** 主题切换颜色平滑插值（避免单帧闪烁） */
    fun themeColor(): TweenSpec<Color> = tween(
        durationMillis = 320,
        easing = FastOutSlowInEasing
    )
}

/**
 * 参考 chher-public/lib/design/tokens.dart -> AppPressScale
 */
object AppPressScale {
    /** 按钮、圆钮、导航项、快捷入口 */
    const val CONTROL = 0.96f

    /** 独立卡片、外观预览卡 */
    const val CARD = 0.97f

    /** 较大内容块 */
    const val CONTENT = 0.985f

    /** 紧凑小图标（如缩放键、交换键） */
    const val ICON = 0.92f
}

/**
 * 参考 chher-public/lib/design/tokens.dart -> AppRadii / AppSpace
 */
object AppRadii {
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 18.dp
    val card: Dp = 22.dp
    val lg: Dp = 24.dp
    val paper: Dp = 26.dp
    val full: Dp = 999.dp
}

object AppSpace {
    val screen: Dp = 20.dp
    val groupInset: Dp = 16.dp
    val cardInset: Dp = 4.dp
    val rowInset: Dp = 16.dp
    val sectionGap: Dp = 24.dp
    val cardGap: Dp = 12.dp
    val hairline: Dp = 0.5.dp
    val navBarHeight: Dp = 52.dp
    val actionRowHeight: Dp = 52.dp
    val singleLineHeight: Dp = 44.dp
}

/**
 * 块状控件统一按压缩放反馈（对应 chher-public PressScale）：
 * 无 Material 水波纹，按下整体弹性微缩，松手按弹簧回弹；可选叠加紧凑底色与轻触震动。
 */
fun Modifier.pressScale(
    scaleDown: Float = AppPressScale.CONTROL,
    enabled: Boolean = true,
    haptic: Boolean = false,
    role: Role? = Role.Button,
    pressedColor: Color? = null,
    pressedShape: Shape = RoundedCornerShape(AppRadii.sm),
    springSpec: SpringSpec<Float> = AppMotion.tap(),
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) scaleDown else 1f,
        animationSpec = springSpec,
        label = "pressScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (enabled && isPressed && pressedColor != null) pressedColor else Color.Transparent,
        animationSpec = if (isPressed) tween(0) else AppMotion.fade(180),
        label = "pressBg"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (pressedColor != null) {
                Modifier
                    .clip(pressedShape)
                    .background(bgColor)
            } else {
                Modifier
            }
        )
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = role
        ) {
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onClick()
        }
}

/**
 * 列表整行按压高亮反馈（对应 chher-public PressHighlight）：
 * 不缩放整行，按下立即铺上主题 pressed 底色，松手 200ms 柔和平滑淡回。
 */
fun Modifier.pressHighlight(
    cornerRadius: Dp = AppRadii.md,
    pressedColor: Color? = null,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val palette = LocalLoongPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tint = pressedColor ?: palette.pressed

    val bg by animateColorAsState(
        targetValue = if (enabled && isPressed) tint else Color.Transparent,
        animationSpec = if (isPressed) tween(0) else AppMotion.fade(200),
        label = "pressHighlight"
    )

    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(bg)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = role,
            onClick = onClick
        )
}

/**
 * 仅提供视觉按压缩放（用于 Material Button / FilterChip 等自带 clickable 的组件外层包裹）
 */
fun Modifier.bounceOnTouch(
    scaleDown: Float = AppPressScale.CONTROL,
    enabled: Boolean = true
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) scaleDown else 1f,
        animationSpec = AppMotion.tap(),
        label = "bounceOnTouch"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (!enabled) {
                pressed = false
                return@pointerInput
            }
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}
