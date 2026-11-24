package com.loongair.tripplanner

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.loongair.tripplanner.alarm.NotificationHelper
import com.loongair.tripplanner.ui.MainViewModel
import com.loongair.tripplanner.ui.account.AccountScreen
import com.loongair.tripplanner.ui.components.chhersShadow
import com.loongair.tripplanner.ui.plans.PlansScreen
import com.loongair.tripplanner.ui.routes.RoutesScreen
import com.loongair.tripplanner.ui.search.SearchScreen
import com.loongair.tripplanner.ui.theme.AppMotion
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.AppThemeStyle
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongAirTheme
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.pressScale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var selectedTabState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.WHITE, AndroidColor.WHITE)
        )
        handleIncomingIntent(intent)
        setContent {
            val session by viewModel.sessionState.collectAsState()
            val themeStyle = AppThemeStyle.fromKey(session.themeStyle)

            LoongAirTheme(themeStyle = themeStyle) {
                val palette = LocalLoongPalette.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = palette.lightStatusBarIcons
                    controller.isAppearanceLightNavigationBars = true
                }
                MainScaffold(
                    viewModel = viewModel,
                    selectedTab = selectedTabState.intValue,
                    onSelectTab = { selectedTabState.intValue = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val planId = intent?.getLongExtra(NotificationHelper.EXTRA_PLAN_ID, -1L) ?: -1L
        if (planId > 0L) {
            viewModel.highlightPlan(planId)
            selectedTabState.intValue = 2
        }
    }
}

private data class BottomTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
    val showDot: Boolean = false
)

@Composable
private fun MainScaffold(
    viewModel: MainViewModel,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val palette = LocalLoongPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val session by viewModel.sessionState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()

    val activeReminderCount = remember(reminders) {
        val now = System.currentTimeMillis()
        reminders.count { it.enabled && !it.fired && it.triggerAtMillis > now }
    }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    val tabs = listOf(
        BottomTabItem(0, "航线", Icons.Default.Map),
        BottomTabItem(1, "往返", Icons.Default.FlightTakeoff),
        BottomTabItem(2, "行程", Icons.Default.Alarm, badgeCount = activeReminderCount),
        BottomTabItem(3, "我的", Icons.Default.Person, showDot = !session.isLoggedIn || !networkState.hasSyncedData)
    )

    Scaffold(
        containerColor = palette.pageBg,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) { data ->
                // 白底黑字胶囊 Toast + chher-public AppMotion.pop 弹簧弹入
                val pop = remember(data) { Animatable(0f) }
                LaunchedEffect(data) {
                    pop.snapTo(0f)
                    pop.animateTo(1f, AppMotion.pop())
                }
                val shape = RoundedCornerShape(AppRadii.full)
                Surface(
                    shape = shape,
                    color = Color.White,
                    border = BorderStroke(0.8.dp, palette.border),
                    modifier = Modifier
                        .graphicsLayer {
                            val v = pop.value
                            alpha = v.coerceIn(0f, 1f)
                            scaleX = 0.92f + 0.08f * v
                            scaleY = 0.92f + 0.08f * v
                            translationY = (1f - v) * 28f
                        }
                        .chhersShadow(
                            cornerRadius = 24.dp,
                            floating = true,
                            tint = palette.shadowTint
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                        Text(
                            text = data.visuals.message,
                            color = palette.textPrimary,
                            style = LoongType.filter.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        },
        bottomBar = {
            ChhersBottomBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 150),
                label = "mainTabCrossfade"
            ) { tab ->
                when (tab) {
                    0 -> RoutesScreen(
                        viewModel = viewModel,
                        onJumpToRoundTrip = { fromCity, toCity ->
                            viewModel.jumpToRoundTripSearch(fromCity, toCity)
                            onSelectTab(1)
                        },
                        onGoToAccountSync = { onSelectTab(3) }
                    )
                    1 -> SearchScreen(
                        viewModel = viewModel,
                        onPlanCreatedNavigate = { savedPlan ->
                            viewModel.highlightPlan(savedPlan.id)
                            onSelectTab(2)
                        },
                        onGoToAccountSync = { onSelectTab(3) }
                    )
                    2 -> PlansScreen(
                        viewModel = viewModel,
                        onGoToAccountSync = { onSelectTab(3) }
                    )
                    else -> AccountScreen(viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * 复刻 chher-public/lib/widgets/app_shell.dart -> AppBottomNavigation：
 * - 68dp 高度 + 发丝顶分隔线
 * - 单个弹簧滑动底胶囊 (46dp × 34dp, 12dp 圆角, AppMotion.select) 跨标签丝滑漂移
 * - 选中图标弹性放大 (1.0 -> 1.08) + 色彩平滑插值 + 12.5sp w600/w500 标签
 * - 提醒数字与未登录红点使用 AppMotion.pop 弹簧缩放显隐
 */
@Composable
private fun ChhersBottomBar(
    tabs: List<BottomTabItem>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val p = LocalLoongPalette.current
    val pillPosition by animateFloatAsState(
        targetValue = selectedTab.toFloat(),
        animationSpec = AppMotion.select(),
        label = "bottomBarPillPos"
    )

    Surface(
        color = p.cardBg,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                val slotWidth = maxWidth / tabs.size.coerceAtLeast(1)
                val pillWidth = 46.dp
                val pillHeight = 34.dp
                val pillOffsetX = slotWidth * (pillPosition + 0.5f) - pillWidth / 2f

                // 物理弹簧滑动底胶囊
                Box(
                    modifier = Modifier
                        .offset(x = pillOffsetX, y = 7.dp)
                        .width(pillWidth)
                        .height(pillHeight)
                        .clip(RoundedCornerShape(AppRadii.sm))
                        .background(p.primarySoftBg)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEach { tab ->
                        val selected = tab.index == selectedTab
                        val emphasis by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = AppMotion.select(),
                            label = "tabEmphasis_${tab.index}"
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) p.primary else p.textTertiary,
                            animationSpec = AppMotion.fade(180),
                            label = "tabIconTint_${tab.index}"
                        )
                        val labelColor by animateColorAsState(
                            targetValue = if (selected) p.primary else p.textSecondary,
                            animationSpec = AppMotion.fade(180),
                            label = "tabLabelColor_${tab.index}"
                        )
                        val badgeScale by animateFloatAsState(
                            targetValue = if (tab.badgeCount > 0 || tab.showDot) 1f else 0f,
                            animationSpec = if (tab.badgeCount > 0 || tab.showDot) AppMotion.pop() else AppMotion.tap(),
                            label = "tabBadgeScale_${tab.index}"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pressScale(
                                    scaleDown = AppPressScale.CONTROL,
                                    haptic = true,
                                    onClick = { onSelectTab(tab.index) }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.height(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        val s = 1f + 0.08f * emphasis
                                        scaleX = s
                                        scaleY = s
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = iconTint,
                                        modifier = Modifier.size(23.dp)
                                    )
                                    if (tab.badgeCount > 0 && badgeScale > 0.01f) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 8.dp, y = (-5).dp)
                                                .graphicsLayer {
                                                    val s = badgeScale.coerceAtLeast(0f)
                                                    scaleX = s
                                                    scaleY = s
                                                }
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(p.danger),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (tab.badgeCount > 9) "9+" else "${tab.badgeCount}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    } else if (tab.showDot && badgeScale > 0.01f) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 3.dp, y = (-1).dp)
                                                .graphicsLayer {
                                                    val s = badgeScale.coerceAtLeast(0f)
                                                    scaleX = s
                                                    scaleY = s
                                                }
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(p.danger)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = LoongType.navLabel.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = labelColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
