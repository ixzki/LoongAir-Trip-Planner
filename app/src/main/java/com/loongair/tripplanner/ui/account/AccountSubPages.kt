package com.loongair.tripplanner.ui.account

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.loongair.tripplanner.BuildConfig
import com.loongair.tripplanner.data.local.InvalidatedRouteEntity
import com.loongair.tripplanner.data.local.UserSessionState
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.repository.NetworkRouteSummary
import com.loongair.tripplanner.ui.components.GroupedSectionLabel
import com.loongair.tripplanner.ui.components.SoftCard
import com.loongair.tripplanner.ui.components.TintedIconBadge
import com.loongair.tripplanner.ui.components.TonePillBadge
import com.loongair.tripplanner.ui.components.chhersShadow
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.AppThemeStyle
import com.loongair.tripplanner.ui.theme.CrimsonPalette
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.MistPalette
import com.loongair.tripplanner.ui.theme.MistyAccentPillar
import com.loongair.tripplanner.ui.theme.bounceOnTouch
import com.loongair.tripplanner.ui.theme.pressHighlight
import com.loongair.tripplanner.ui.theme.pressScale

/**
 * 独立子页顶部栏（复刻 chher-public LargeTitleScaffold：38dp 纯白柔影圆钮 + 32sp Bold 大标题）
 */
@Composable
internal fun SubPageHeader(title: String, onBack: () -> Unit) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = AppSpace.screen, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = p.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = title,
            style = LoongType.largeTitle,
            color = p.textPrimary,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
internal fun CardsSubPage(
    session: UserSessionState,
    cardSyncLoading: Boolean,
    onBack: () -> Unit,
    onResyncCards: () -> Unit,
    onSelectCard: (String, String) -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenManual: () -> Unit
) {
    val p = LocalLoongPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBg),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SubPageHeader(title = "卡片", onBack = onBack) }

        item {
            Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        TintedIconBadge(
                            icon = Icons.Default.CreditCard,
                            size = 44.dp,
                            iconSize = 22.dp,
                            cornerRadius = 13.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = session.cardName,
                                style = LoongType.headline,
                                color = p.textPrimary
                            )
                            Text(
                                text = "当前生效授权 ID：${UserSessionState.maskGrantId(session.secCardGrantTid).ifBlank { "未识别，请同步卡包" }}",
                                style = LoongType.footnote.copy(fontFeatureSettings = "tnum"),
                                color = p.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        enabled = session.isLoggedIn && !cardSyncLoading,
                        onClick = onResyncCards,
                        shape = RoundedCornerShape(AppRadii.full),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .bounceOnTouch(enabled = session.isLoggedIn && !cardSyncLoading)
                    ) {
                        if (cardSyncLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("正在同步卡包...", style = LoongType.filter)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重新同步卡包", style = LoongType.filter)
                        }
                    }

                    if (session.detectedCards.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "切换名下畅飞卡 / 权益卡（共 ${session.detectedCards.size} 张）",
                            style = LoongType.footnoteStrong,
                            color = p.textTertiary
                        )
                        Spacer(Modifier.height(9.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            session.detectedCards.forEach { card ->
                                val isSel = card.grantTid == session.secCardGrantTid
                                val cardBg by animateColorAsState(
                                    targetValue = if (isSel) p.primarySoftBg.copy(alpha = 0.65f) else p.subtleSurface,
                                    animationSpec = AppMotion.fade(),
                                    label = "cardItemBg_${card.grantTid}"
                                )
                                val cardBorder by animateColorAsState(
                                    targetValue = if (isSel) p.primary else Color.Transparent,
                                    animationSpec = AppMotion.fade(),
                                    label = "cardItemBorder_${card.grantTid}"
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressScale(
                                            scaleDown = AppPressScale.CONTENT,
                                            haptic = true,
                                            onClick = { onSelectCard(card.grantTid, card.cardName) }
                                        ),
                                    color = cardBg,
                                    border = BorderStroke(1.5.dp, cardBorder),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSel,
                                            onClick = { onSelectCard(card.grantTid, card.cardName) }
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(card.cardName, style = LoongType.rowLabel, color = p.textPrimary)
                                                TonePillBadge(
                                                    text = if (card.isSecCard) "次卡/畅飞" else "Plus/其他",
                                                    backgroundColor = if (card.isSecCard) p.successBg else p.warningBg,
                                                    contentColor = if (card.isSecCard) p.success else p.warning,
                                                    cornerRadius = 7.dp
                                                )
                                            }
                                            Text(
                                                "ID: ${UserSessionState.maskGrantId(card.grantTid)}",
                                                style = LoongType.footnote.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                                                color = p.textTertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!session.isLoggedIn) {
                        Spacer(Modifier.height(10.dp))
                        Text("请先返回「我的」登录账号后再同步名下畅飞卡。", style = LoongType.footnote, color = p.textSecondary)
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpace.groupInset),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupedSectionLabel("高级")
                SoftCard(cornerRadius = AppRadii.card, contentPadding = AppSpace.cardInset) {
                    SettingsRowItem(
                        icon = Icons.Default.BugReport,
                        title = "卡包诊断（已脱敏）",
                        trailingText = "查看",
                        onClick = onOpenDiagnostic
                    )
                    SettingsRowItem(
                        icon = Icons.Default.Key,
                        title = "手动设置 scgTid / 导入 SESSION",
                        onClick = onOpenManual
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSubPage(
    currentTheme: AppThemeStyle,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectTheme: (AppThemeStyle) -> Unit,
    onResetTemplates: () -> Unit,
    onClearCache: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit
) {
    val p = LocalLoongPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBg),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { SubPageHeader(title = "设置", onBack = onBack) }

        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpace.groupInset),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GroupedSectionLabel("外观")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StylePreviewCard(
                        modifier = Modifier.weight(1f),
                        style = AppThemeStyle.MIST,
                        isSelected = currentTheme == AppThemeStyle.MIST,
                        onClick = { onSelectTheme(AppThemeStyle.MIST) }
                    )
                    StylePreviewCard(
                        modifier = Modifier.weight(1f),
                        style = AppThemeStyle.CRIMSON,
                        isSelected = currentTheme == AppThemeStyle.CRIMSON,
                        onClick = { onSelectTheme(AppThemeStyle.CRIMSON) }
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpace.groupInset),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GroupedSectionLabel("通用")
                SoftCard(cornerRadius = AppRadii.card, contentPadding = AppSpace.cardInset) {
                    SettingsRowItem(
                        icon = Icons.Default.Restore,
                        title = "恢复默认往返模板",
                        onClick = onResetTemplates
                    )
                    SettingsRowItem(
                        icon = Icons.Default.DeleteOutline,
                        title = "清空日历缓存并恢复内置航线库",
                        onClick = onClearCache
                    )
                    SettingsRowItem(
                        icon = Icons.Default.Info,
                        title = "关于 畅飞行",
                        trailingPill = "v${BuildConfig.VERSION_NAME}",
                        onClick = onOpenAbout
                    )
                }
            }
        }

        if (isLoggedIn) {
            item {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .padding(horizontal = AppSpace.groupInset)
                ) {
                    val shape = RoundedCornerShape(AppRadii.full)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .chhersShadow(
                                cornerRadius = 24.dp,
                                floating = false,
                                tint = p.shadowTint
                            ),
                        color = p.cardBg,
                        shape = shape
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .pressHighlight(cornerRadius = AppRadii.full, onClick = onLogout),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "退出登录",
                                style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                                color = p.danger
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AboutSubPage(onBack: () -> Unit) {
    val p = LocalLoongPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBg),
        contentPadding = PaddingValues(bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { SubPageHeader(title = "关于 畅飞行", onBack = onBack) }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .chhersShadow(
                            cornerRadius = AppRadii.lg,
                            floating = true,
                            tint = p.shadowTint
                        )
                        .clip(RoundedCornerShape(AppRadii.lg))
                        .background(
                            Brush.linearGradient(listOf(p.primary, p.primaryDark))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Text(
                    text = "畅飞行",
                    style = LoongType.headline,
                    color = p.textPrimary
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = LoongType.subhead.copy(fontFeatureSettings = "tnum"),
                    color = p.textSecondary
                )
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpace.groupInset),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupedSectionLabel("应用说明")
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                    AboutInfoBlock(
                        title = "主要功能",
                        content = "随心飞全网航线浏览、往返智能配对、近 40 天放票日历与抢票闹钟提醒。"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = AppSpace.hairline,
                        color = p.separator
                    )
                    AboutInfoBlock(
                        title = "数据隐私",
                        content = "凭据、缓存与行程计划仅保存在本机并加密存储，不上传第三方服务器。"
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = AppSpace.hairline,
                        color = p.separator
                    )
                    AboutInfoBlock(
                        title = "免责声明",
                        content = "本工具与浙江长龙航空无官方关联，可兑名额、适用日期及航班时刻请以官方渠道为准。"
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpace.groupInset),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupedSectionLabel("开源参考")
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                    Text(
                        text = "接口协议参考 eureka-wu/clairline（MIT），视觉交互致敬 zzNaLogiczz/chher-public。",
                        style = LoongType.footnote,
                        color = p.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutInfoBlock(title: String, content: String) {
    val p = LocalLoongPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = LoongType.footnoteStrong,
            color = p.textPrimary
        )
        Text(
            text = content,
            style = LoongType.subhead,
            color = p.textSecondary
        )
    }
}

@Composable
internal fun QuickActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    bg: Color,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    Column(
        modifier = modifier
            .pressScale(
                scaleDown = AppPressScale.CONTROL,
                haptic = true,
                pressedColor = p.pressed,
                pressedShape = RoundedCornerShape(14.dp),
                springSpec = AppMotion.entryTap(),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        TintedIconBadge(
            icon = icon,
            tint = tint,
            background = bg,
            size = 36.dp,
            iconSize = 19.dp,
            cornerRadius = 11.dp
        )
        Text(
            text = label,
            style = LoongType.navLabel,
            color = p.textPrimary.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun StylePreviewCard(
    modifier: Modifier,
    style: AppThemeStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    val isMistCard = style == AppThemeStyle.MIST
    val previewPalette = if (isMistCard) MistPalette else CrimsonPalette

    val outlineAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = AppMotion.fade(220),
        label = "styleOutline_${style.key}"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (isSelected) AppMotion.pop() else AppMotion.tap(),
        label = "styleCheck_${style.key}"
    )

    val cardShape = RoundedCornerShape(AppRadii.card)
    Box(
        modifier = modifier
            .pressScale(
                scaleDown = AppPressScale.CARD,
                haptic = true,
                onClick = { if (!isSelected) onClick() }
            )
            .chhersShadow(
                cornerRadius = AppRadii.card,
                floating = false,
                tint = p.shadowTint
            )
            .clip(cardShape)
            .background(p.cardBg)
            .border(
                width = 2.dp,
                color = p.primary.copy(alpha = outlineAlpha),
                shape = cardShape
            )
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(previewPalette.pageBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(
                            if (isMistCard) {
                                Brush.horizontalGradient(
                                    listOf(
                                        MistyAccentPillar.blue.copy(alpha = 0.16f),
                                        MistyAccentPillar.violet.copy(alpha = 0.13f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to CrimsonPalette.heroTop,
                                        0.55f to CrimsonPalette.heroMid,
                                        1f to CrimsonPalette.heroBottom
                                    )
                                )
                            }
                        )
                )
                if (!isMistCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 44.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(previewPalette.pageBg)
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 15.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(previewPalette.warningBg)
                )
                Box(
                    modifier = Modifier
                        .padding(start = 33.dp, top = 20.dp)
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(AppRadii.full))
                        .background(if (isMistCard) previewPalette.textPrimary else previewPalette.heroText)
                )
                val dots = if (isMistCard) {
                    listOf(MistyAccentPillar.blue, MistyAccentPillar.orange, MistyAccentPillar.green, MistyAccentPillar.violet)
                } else {
                    List(4) { CrimsonPalette.primary }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 38.dp)
                        .height(24.dp),
                    color = previewPalette.cardBg,
                    shape = RoundedCornerShape(AppRadii.xs),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dots.forEach { dotColor ->
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 68.dp)
                        .height(24.dp),
                    color = previewPalette.cardBg,
                    shape = RoundedCornerShape(AppRadii.xs),
                    shadowElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(AppRadii.full))
                                .background(previewPalette.textPrimary.copy(alpha = 0.25f))
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 11.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = style.displayName,
                    style = LoongType.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = p.textPrimary
                )
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.5.dp, p.textTertiary, CircleShape)
                        )
                    }
                    if (checkScale > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val s = checkScale.coerceAtLeast(0f)
                                    scaleX = s
                                    scaleY = s
                                }
                                .clip(CircleShape)
                                .background(p.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    trailingText: String = "",
    trailingPill: String = "",
    onClick: () -> Unit
) {
    val p = LocalLoongPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppSpace.actionRowHeight)
            .pressHighlight(cornerRadius = AppRadii.md, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            TintedIconBadge(icon = icon, size = 32.dp, iconSize = 18.dp, cornerRadius = 10.dp)
            Text(
                text = title,
                style = LoongType.rowLabel,
                color = p.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (trailingPill.isNotBlank()) {
                TonePillBadge(
                    text = trailingPill,
                    backgroundColor = p.primarySoftBg,
                    contentColor = p.primary,
                    cornerRadius = AppRadii.full
                )
            } else if (trailingText.isNotBlank()) {
                Text(
                    text = trailingText,
                    style = LoongType.subhead.copy(fontSize = 14.5.sp),
                    color = p.textTertiary
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = p.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkManageSubPage(
    activeHomeCity: String,
    isLoggedIn: Boolean,
    officialSyncing: Boolean,
    officialSyncedCount: Int,
    bundledRouteCount: Int,
    homeDestinations: List<String>,
    soldOutRoutes: List<NetworkRouteSummary>,
    invalidatedRoutes: List<InvalidatedRouteEntity>,
    onBack: () -> Unit,
    onRefreshHomeCity: () -> Unit,
    onSelectDestination: (String) -> Unit,
    onRefreshAllSoldOut: () -> Unit,
    onRestoreInvalidated: (String, String) -> Unit
) {
    val p = LocalLoongPalette.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val liveVerifiedCount = (officialSyncedCount - bundledRouteCount).coerceAtLeast(0)
    val sortedHomeDestinations = remember(homeDestinations) {
        homeDestinations.distinct().sortedBy { CityIataMapper.pinyinForCity(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBg)
    ) {
        SubPageHeader(title = "畅飞航网管理", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSpace.groupInset,
                end = AppSpace.groupInset,
                top = 8.dp,
                bottom = 36.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.cardGap)
        ) {
            // 1. 航网统计总览卡（与「卡片」页顶部主卡一致）
            item(key = "overview") {
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkMetricColumn(
                            value = "$officialSyncedCount",
                            label = "有效航线",
                            valueColor = p.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(AppSpace.hairline)
                                .height(28.dp)
                                .background(p.separator)
                        )
                        NetworkMetricColumn(
                            value = "${soldOutRoutes.size}",
                            label = "近期全约满",
                            valueColor = if (soldOutRoutes.isNotEmpty()) p.warning else p.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(AppSpace.hairline)
                                .height(28.dp)
                                .background(p.separator)
                        )
                        NetworkMetricColumn(
                            value = "${invalidatedRoutes.size}",
                            label = "已移出/停航",
                            valueColor = if (invalidatedRoutes.isNotEmpty()) p.danger else p.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = p.subtleSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "实时校验 $liveVerifiedCount 条 · 内置基准 $bundledRouteCount 条",
                            style = LoongType.caption.copy(fontFeatureSettings = "tnum"),
                            color = p.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = !officialSyncing,
                        shape = RoundedCornerShape(AppRadii.full),
                        onClick = onRefreshHomeCity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .bounceOnTouch(enabled = !officialSyncing)
                    ) {
                        if (officialSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (officialSyncing) "正在同步航线..." else "刷新常驻城（$activeHomeCity）航线",
                            style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // 2. 分段标签切换卡
            item(key = "tabs") {
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 6.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tabs = listOf(
                            "$activeHomeCity (${sortedHomeDestinations.size})",
                            "全约满 (${soldOutRoutes.size})",
                            "已移出 (${invalidatedRoutes.size})"
                        )
                        tabs.forEachIndexed { idx, label ->
                            val selected = selectedTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) p.primary else Color.Transparent)
                                    .pressScale(
                                        scaleDown = AppPressScale.CONTROL,
                                        haptic = true,
                                        onClick = { selectedTab = idx }
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                                    color = if (selected) Color.White else p.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. 分页内容区
            when (selectedTab) {
                0 -> {
                    item(key = "home_dests") {
                        SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                            Text(
                                text = "从 $activeHomeCity 可达目的地（点击设为往返搜索目标）",
                                style = LoongType.footnote,
                                color = p.textSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            if (sortedHomeDestinations.isEmpty()) {
                                Text(
                                    text = "尚未同步「$activeHomeCity」航线，请点击上方按钮刷新。",
                                    style = LoongType.footnote,
                                    color = p.textTertiary
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(9.dp)
                                ) {
                                    sortedHomeDestinations.forEach { city ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(p.subtleSurface)
                                                .border(0.6.dp, p.border, RoundedCornerShape(11.dp))
                                                .pressScale(
                                                    scaleDown = AppPressScale.CONTROL,
                                                    haptic = true,
                                                    onClick = { onSelectDestination(city) }
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
                }

                1 -> {
                    item(key = "sold_out_card") {
                        SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                            Text(
                                text = "以下航线仍在畅飞卡适用范围内，仅近期已放票日期名额全部约满（非移出卡外）；可在每日 10:00 开抢新窗或临近出行前巡检退票回流。",
                                style = LoongType.footnote,
                                color = p.textSecondary
                            )
                            if (soldOutRoutes.isNotEmpty() && isLoggedIn) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    enabled = !officialSyncing,
                                    shape = RoundedCornerShape(AppRadii.full),
                                    border = BorderStroke(1.dp, p.primary.copy(alpha = 0.45f)),
                                    onClick = onRefreshAllSoldOut,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .bounceOnTouch(enabled = !officialSyncing)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = p.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "一键巡检全部约满航线余票",
                                        style = LoongType.footnoteStrong,
                                        color = p.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (soldOutRoutes.isEmpty()) {
                                Text(
                                    text = "当前全网暂无近期全部约满的航线。",
                                    style = LoongType.footnote,
                                    color = p.textTertiary
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    soldOutRoutes.forEach { r ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(p.warningBg)
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${r.fromCity} → ${r.toCity}",
                                                    style = LoongType.rowLabel,
                                                    color = p.textPrimary
                                                )
                                                Text(
                                                    text = if (r.flights.isNotEmpty()) {
                                                        "已有时刻 ${r.flights.size} 班 · 近 ${r.soldOutDaysCount} 天约满"
                                                    } else {
                                                        "近 ${r.soldOutDaysCount} 个放票日约满（有余票即自动抓取时刻）"
                                                    },
                                                    style = LoongType.caption,
                                                    color = p.warning
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    item(key = "invalidated_card") {
                        SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                            Text(
                                text = "以下航线经官方接口确认已下线、停航无班或超出当前畅飞卡适用范围，已从本地与内置基准库自动剔除。",
                                style = LoongType.footnote,
                                color = p.textSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            if (invalidatedRoutes.isEmpty()) {
                                Text(
                                    text = "当前暂未检测到被移出或停航失效的航线。",
                                    style = LoongType.footnote,
                                    color = p.textTertiary
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    invalidatedRoutes.forEach { inv ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(p.dangerBg)
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${inv.fromCity} → ${inv.toCity}",
                                                    style = LoongType.rowLabel,
                                                    color = p.textPrimary
                                                )
                                                Text(
                                                    text = inv.reason,
                                                    style = LoongType.caption,
                                                    color = p.danger
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(AppRadii.full))
                                                    .background(p.cardBg)
                                                    .pressScale(
                                                        scaleDown = AppPressScale.CONTROL,
                                                        haptic = true,
                                                        onClick = { onRestoreInvalidated(inv.fromCity, inv.toCity) }
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "重检",
                                                    style = LoongType.caption.copy(fontWeight = FontWeight.SemiBold),
                                                    color = p.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkMetricColumn(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val p = LocalLoongPalette.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = LoongType.title2.copy(fontFeatureSettings = "tnum"),
            color = valueColor
        )
        Text(
            text = label,
            style = LoongType.caption,
            color = p.textTertiary
        )
    }
}
