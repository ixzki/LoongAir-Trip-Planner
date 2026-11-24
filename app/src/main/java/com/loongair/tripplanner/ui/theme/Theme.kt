package com.loongair.tripplanner.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppThemeStyle(val key: String, val displayName: String) {
    CRIMSON("crimson", "朱砂"),
    MIST("mist", "薄暮");

    companion object {
        fun fromKey(key: String?): AppThemeStyle =
            entries.firstOrNull { it.key == key } ?: CRIMSON
    }
}

/**
 * 参考 chher-public/lib/design/tokens.dart (cinnabarLight & mistyLight) 精确色阶与语意令牌
 */
@Immutable
data class LoongPalette(
    val style: AppThemeStyle,
    val primary: Color,
    val primaryDark: Color,
    val primarySoftBg: Color,
    val accentLine: Color,
    val heroTop: Color,
    val heroMid: Color,
    val heroBottom: Color,
    val heroText: Color,
    val heroSubText: Color,
    val heroChipBg: Color,
    val heroWatermark: Color,
    val mistGlowLeft: Color,
    val mistGlowRight: Color,
    val pageBg: Color,
    val cardBg: Color,
    val subtleSurface: Color,
    val pressed: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val separator: Color,
    val shadowTint: Color,
    val navInactive: Color,
    val lightStatusBarIcons: Boolean,
    val success: Color,
    val successBg: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color
)

/**
 * 朱砂（cinnabarLight）— 暖宣纸底 + 三段漆面渐层 + 三级温墨字阶 + 低饱和国色语意
 */
val CrimsonPalette = LoongPalette(
    style = AppThemeStyle.CRIMSON,
    primary = Color(0xFFAF2025),          // 朱 600 (6.78:1 on paper)
    primaryDark = Color(0xFF8A161B),      // chipText / 朱 700
    primarySoftBg = Color(0xFFFFE8E2),    // accentFill / 朱 100
    accentLine = Color(0xFFFDB6AB),       // accentLine / 朱 200
    heroTop = Color(0xFFC62F2F),          // lacTop
    heroMid = Color(0xFFAF2025),          // lacMid (55%)
    heroBottom = Color(0xFF94151E),       // lacBot
    heroText = Color(0xFFFFF8F5),         // onLac 奶白
    heroSubText = Color(0xFFF9E2DA),      // onLac2 淡朱
    heroChipBg = Color(0x26FFF8F5),       // MeTokens.glass 奶白 15%
    heroWatermark = Color(0x0EFFF8F5),    // 奶白 5.5% 柔水印（紧贴接缝上沿不抢文字）
    mistGlowLeft = Color(0xFFC62F2F),
    mistGlowRight = Color(0xFF94151E),
    pageBg = Color(0xFFF6F3F1),           // grouped 暖宣灰
    cardBg = Color(0xFFFFFFFE),           // surface 暖纸白
    subtleSurface = Color(0xFFF1EBE9),    // surface2 暖浅槽
    pressed = Color(0xFFEDE8E6),          // pressed 按压高亮
    textPrimary = Color(0xFF201A19),      // text 主墨
    textSecondary = Color(0xFF6E6563),    // text2 次墨
    textTertiary = Color(0xFF9E9694),     // text3 浅墨
    border = Color(0x1F462822),           // 12% 暖棕发丝边
    separator = Color(0x24462822),        // rgba(70,40,34,0.14)
    shadowTint = Color(0xFF3C1E19),       // shadowE1 暖棕环境阴影
    navInactive = Color(0xFF9E9694),      // text3
    lightStatusBarIcons = false,
    success = Color(0xFF337C56),          // 苍竹绿
    successBg = Color(0xFFDFF5E7),
    warning = Color(0xFF8E5D1E),          // 琥珀茶
    warningBg = Color(0xFFFDF2DD),
    danger = Color(0xFFAF2025),           // 绛朱砂
    dangerBg = Color(0xFFFFE8E2)
)

/**
 * 薄暮（mistyLight）— 雾白纸面 + 蓝紫双径向柔光 + 澄澈钴蓝强调 + 四色功能柱
 */
val MistPalette = LoongPalette(
    style = AppThemeStyle.MIST,
    primary = Color(0xFF0D74CE),          // Radix Blue 11
    primaryDark = Color(0xFF0A5CA6),
    primarySoftBg = Color(0xFFE6F1FC),    // 柔蓝浅底
    accentLine = Color(0xFFAED4F5),
    heroTop = Color(0xFFE3EBF8),          // 雾蓝高光
    heroMid = Color(0xFFECEBF8),
    heroBottom = Color(0xFFF5F6F8),       // 平滑融进纸面
    heroText = Color(0xFF1C2024),         // text 冷静炭黑
    heroSubText = Color(0xFF6B6E76),      // text2 雾灰
    heroChipBg = Color(0xB8FFFFFF),       // 72% 玻璃白
    heroWatermark = Color(0x0A0D74CE),
    mistGlowLeft = Color(0xFFE3EBF8),     // TabChromeTokens.mistGradients 蓝光
    mistGlowRight = Color(0xFFEEE7F6),    // TabChromeTokens.mistGradients 紫光
    pageBg = Color(0xFFF5F6F8),           // grouped 雾纸灰
    cardBg = Color(0xFFFFFFFF),           // surface 纯纸白
    subtleSurface = Color(0xFFEEF0F3),    // surface2 浅槽
    pressed = Color(0xFFE6E9EE),          // pressed 按压高亮
    textPrimary = Color(0xFF1C2024),      // text
    textSecondary = Color(0xFF6B6E76),    // text2
    textTertiary = Color(0xFFA0A3AB),     // text3
    border = Color(0x1A1C2024),           // 10% 雾灰发丝边
    separator = Color(0x1F1C2024),        // 12% 分隔线
    shadowTint = Color(0xFF1C2024),       // 冷雾环境阴影
    navInactive = Color(0xFFA0A3AB),      // text3
    lightStatusBarIcons = true,
    success = Color(0xFF2B8C5E),          // 雾翠绿
    successBg = Color(0xFFE6F6EB),
    warning = Color(0xFF9C5E12),          // 暮琥珀
    warningBg = Color(0xFFFCF3DF),
    danger = Color(0xFFD6454A),           // 雾茜红
    dangerBg = Color(0xFFFCECED)
)

/** 薄暮主题四功能入口专属柔雾四色（参考 chher-public IconTints.misty*） */
object MistyAccentPillar {
    val blue = Color(0xFF1A7AD4)
    val blueBg = Color(0xFFE7F2FD)
    val orange = Color(0xFFD66A26)
    val orangeBg = Color(0xFFFCEFE6)
    val green = Color(0xFF2B8C5E)
    val greenBg = Color(0xFFE6F5EE)
    val violet = Color(0xFF7E52AD)
    val violetBg = Color(0xFFF2ECF8)
}

val LocalLoongPalette = staticCompositionLocalOf { CrimsonPalette }

/**
 * 参考 chher-public/lib/design/typography.dart 的完整字号/字重/行高/字距体系
 *
 * 原则：克制使用字重（标题 w700、小标题/操作 w600、行标签 w500、正文 w400），
 * 杜绝满屏 w800 粗重感与 10sp 微字；时刻/日期/余票数字统一启用等宽数字 (tnum)。
 */
object LoongType {
    /** 32sp Bold (-0.6sp tracking) — 一级页与二级页大标题 */
    val largeTitle = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 37.sp,
        letterSpacing = (-0.6).sp
    )

    /** 25sp Bold (-0.4sp tracking) — 会员漆面大昵称 / 强调主标题 */
    val title1 = TextStyle(
        fontSize = 25.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    )

    /** 20sp Bold (-0.2sp tracking) — 页面分区标题 (SectionHeader) */
    val title2 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    )

    /** 17sp SemiBold — 卡片主标题、航线对标题、折叠顶栏小标题 */
    val headline = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    )

    /** 16sp Medium — 设置项与分组菜单行标题 (SettingsTokens.rowLabel) */
    val rowLabel = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp
    )

    /** 16sp Regular — 舒展正文段落（关于页、说明文字） */
    val body = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp
    )

    /** 15sp Regular — 次级正文、副标题 */
    val subhead = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    )

    /** 14sp SemiBold — 按钮文案、筛选胶囊、班次核心信息 */
    val filter = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    )

    /** 14sp SemiBold 等宽数字 — 航班时刻、航班号、日期 */
    val tabularTitle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
        fontFeatureSettings = "tnum"
    )

    /** 13.5sp Medium 等宽数字 — 列表中的起降时刻与次要数字 */
    val tabularMeta = TextStyle(
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 19.sp,
        fontFeatureSettings = "tnum"
    )

    /** 13sp Regular — 辅助元信息、机场说明、时间备注 */
    val footnote = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp
    )

    /** 13sp SemiBold — 眉题（日期）、分组标签（外观/通用）、卡片次级标签 */
    val footnoteStrong = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp
    )

    /** 12sp SemiBold — 执飞星期胶囊、余票徽章、状态标签 */
    val caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp
    )

    /** 12.5sp Medium — 快捷入口瓦片文字与底栏标签 */
    val navLabel = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 17.sp
    )
}

private val AppTypography = Typography(
    displaySmall = LoongType.largeTitle,
    headlineMedium = LoongType.title1,
    headlineSmall = LoongType.title2,
    titleLarge = LoongType.title2,
    titleMedium = LoongType.headline,
    titleSmall = LoongType.filter,
    bodyLarge = LoongType.body,
    bodyMedium = LoongType.subhead,
    bodySmall = LoongType.footnote,
    labelLarge = LoongType.filter,
    labelMedium = LoongType.footnoteStrong,
    labelSmall = LoongType.caption
)

// 兼容旧引用常量
val LoongBluePrimary = CrimsonPalette.primary
val LoongCyanAccent = MistPalette.primary
val LoongSurfaceBg = CrimsonPalette.pageBg
val LoongEmerald = CrimsonPalette.success
val LoongEmeraldBg = CrimsonPalette.successBg
val LoongAmber = CrimsonPalette.warning
val LoongAmberBg = CrimsonPalette.warningBg
val LoongRed = CrimsonPalette.danger
val LoongRedBg = CrimsonPalette.dangerBg
val LoongTextSecondary = CrimsonPalette.textSecondary
val LoongBorder = CrimsonPalette.border

@Composable
fun LoongAirTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.CRIMSON,
    content: @Composable () -> Unit
) {
    val target = when (themeStyle) {
        AppThemeStyle.CRIMSON -> CrimsonPalette
        AppThemeStyle.MIST -> MistPalette
    }

    // 平滑颜色过渡，切换「薄暮 ↔ 朱砂」时所有色阶柔顺演进
    val animSpec = AppMotion.themeColor()
    val primary by animateColorAsState(target.primary, animSpec, label = "primary")
    val primaryDark by animateColorAsState(target.primaryDark, animSpec, label = "primaryDark")
    val primarySoftBg by animateColorAsState(target.primarySoftBg, animSpec, label = "primarySoftBg")
    val accentLine by animateColorAsState(target.accentLine, animSpec, label = "accentLine")
    val heroTop by animateColorAsState(target.heroTop, animSpec, label = "heroTop")
    val heroMid by animateColorAsState(target.heroMid, animSpec, label = "heroMid")
    val heroBottom by animateColorAsState(target.heroBottom, animSpec, label = "heroBottom")
    val heroText by animateColorAsState(target.heroText, animSpec, label = "heroText")
    val heroSubText by animateColorAsState(target.heroSubText, animSpec, label = "heroSubText")
    val heroChipBg by animateColorAsState(target.heroChipBg, animSpec, label = "heroChipBg")
    val heroWatermark by animateColorAsState(target.heroWatermark, animSpec, label = "heroWatermark")
    val pageBg by animateColorAsState(target.pageBg, animSpec, label = "pageBg")
    val cardBg by animateColorAsState(target.cardBg, animSpec, label = "cardBg")
    val subtleSurface by animateColorAsState(target.subtleSurface, animSpec, label = "subtleSurface")
    val pressed by animateColorAsState(target.pressed, animSpec, label = "pressed")
    val textPrimary by animateColorAsState(target.textPrimary, animSpec, label = "textPrimary")
    val textSecondary by animateColorAsState(target.textSecondary, animSpec, label = "textSecondary")
    val textTertiary by animateColorAsState(target.textTertiary, animSpec, label = "textTertiary")
    val border by animateColorAsState(target.border, animSpec, label = "border")
    val separator by animateColorAsState(target.separator, animSpec, label = "separator")
    val shadowTint by animateColorAsState(target.shadowTint, animSpec, label = "shadowTint")
    val navInactive by animateColorAsState(target.navInactive, animSpec, label = "navInactive")
    val success by animateColorAsState(target.success, animSpec, label = "success")
    val successBg by animateColorAsState(target.successBg, animSpec, label = "successBg")
    val warning by animateColorAsState(target.warning, animSpec, label = "warning")
    val warningBg by animateColorAsState(target.warningBg, animSpec, label = "warningBg")
    val danger by animateColorAsState(target.danger, animSpec, label = "danger")
    val dangerBg by animateColorAsState(target.dangerBg, animSpec, label = "dangerBg")

    val animatedPalette = LoongPalette(
        style = target.style,
        primary = primary,
        primaryDark = primaryDark,
        primarySoftBg = primarySoftBg,
        accentLine = accentLine,
        heroTop = heroTop,
        heroMid = heroMid,
        heroBottom = heroBottom,
        heroText = heroText,
        heroSubText = heroSubText,
        heroChipBg = heroChipBg,
        heroWatermark = heroWatermark,
        mistGlowLeft = target.mistGlowLeft,
        mistGlowRight = target.mistGlowRight,
        pageBg = pageBg,
        cardBg = cardBg,
        subtleSurface = subtleSurface,
        pressed = pressed,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        border = border,
        separator = separator,
        shadowTint = shadowTint,
        navInactive = navInactive,
        lightStatusBarIcons = target.lightStatusBarIcons,
        success = success,
        successBg = successBg,
        warning = warning,
        warningBg = warningBg,
        danger = danger,
        dangerBg = dangerBg
    )

    val colorScheme = lightColorScheme(
        primary = animatedPalette.primary,
        onPrimary = Color.White,
        primaryContainer = animatedPalette.primarySoftBg,
        onPrimaryContainer = animatedPalette.primaryDark,
        secondary = animatedPalette.primary,
        onSecondary = Color.White,
        secondaryContainer = animatedPalette.primarySoftBg,
        onSecondaryContainer = animatedPalette.primaryDark,
        background = animatedPalette.pageBg,
        onBackground = animatedPalette.textPrimary,
        surface = animatedPalette.cardBg,
        onSurface = animatedPalette.textPrimary,
        surfaceVariant = animatedPalette.subtleSurface,
        onSurfaceVariant = animatedPalette.textSecondary,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = animatedPalette.subtleSurface,
        outline = animatedPalette.border,
        outlineVariant = animatedPalette.separator,
        error = animatedPalette.danger,
        onError = Color.White
    )

    CompositionLocalProvider(LocalLoongPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
