package com.loongair.tripplanner.ui.plans

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loongair.tripplanner.alarm.NotificationHelper
import com.loongair.tripplanner.data.local.ReminderEntity
import com.loongair.tripplanner.data.local.TripPlanEntity
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.MainViewModel
import com.loongair.tripplanner.ui.components.CompactScrollTopBar
import com.loongair.tripplanner.ui.components.FirstRunOnboardingGate
import com.loongair.tripplanner.ui.components.HeroCircleActionButton
import com.loongair.tripplanner.ui.components.HeroHeaderOverlapContainer
import com.loongair.tripplanner.ui.components.LacquerPullRefreshBox
import com.loongair.tripplanner.ui.components.SectionTitleRow
import com.loongair.tripplanner.ui.components.SoftCard
import com.loongair.tripplanner.ui.components.TintedIconBadge
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
import com.loongair.tripplanner.util.ChinaClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlansScreen(
    viewModel: MainViewModel,
    onGoToAccountSync: () -> Unit
) {
    val p = LocalLoongPalette.current
    val context = LocalContext.current
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val recheckingId by viewModel.recheckingPlanId.collectAsStateWithLifecycle()
    val highlightedPlanId by viewModel.highlightedPlanId.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val officialAirports by viewModel.officialAirports.collectAsStateWithLifecycle()
    val departureAirportsLoading by viewModel.departureAirportsLoading.collectAsStateWithLifecycle()

    // 首次登录并同步一个城市的所有航线之前（且本地无历史行程），锁定行程提醒功能
    if (!networkState.isLoading && !networkState.hasSyncedData && plans.isEmpty()) {
        FirstRunOnboardingGate(
            screenTitle = "行程提醒",
            screenWatermark = "PLAN",
            featureHint = "无预置数据，同步一座城市后解锁行程提醒",
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

    var hasNotifPerm by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    var canExactAlarm by remember { mutableStateOf(viewModel.scheduler.canScheduleExactAlarms()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = NotificationHelper.hasNotificationPermission(context)
                canExactAlarm = viewModel.scheduler.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        if (!granted) {
            viewModel.showMessage("未授予通知权限，抢票闹钟到点时可能无法弹出提醒")
        }
    }

    var reminderTargetPlan by remember { mutableStateOf<TripPlanEntity?>(null) }

    val remindersByPlan = remember(reminders) { reminders.groupBy { it.planId } }
    val pendingCount = remember(reminders) {
        val now = System.currentTimeMillis()
        reminders.count { it.enabled && !it.fired && it.triggerAtMillis > now }
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
            // 1. 沉浸式头图 + 悬浮权限状态卡
            item {
                HeroHeaderOverlapContainer(
                    subtitle = "${plans.size} 个行程计划 · $pendingCount 个待触发抢票闹钟",
                    title = "行程提醒",
                    watermark = "PLAN",
                    collapseProgress = collapseProgress,
                    trailingAction = {
                        HeroCircleActionButton(
                            icon = Icons.Default.NotificationsActive,
                            contentDescription = "发测试提醒",
                            onClick = {
                                if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.sendTestNotification()
                                }
                            }
                        )
                    }
                ) {
                    SoftCard(
                        cornerRadius = AppRadii.card,
                        floating = true,
                        borderColor = if (!hasNotifPerm || !canExactAlarm) p.warning.copy(alpha = 0.4f) else null,
                        contentPadding = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                TintedIconBadge(
                                    icon = if (hasNotifPerm && canExactAlarm) Icons.Default.NotificationsActive else Icons.Default.Warning,
                                    tint = if (hasNotifPerm && canExactAlarm) p.success else p.warning,
                                    background = if (hasNotifPerm && canExactAlarm) p.successBg else p.warningBg,
                                    size = 38.dp,
                                    iconSize = 20.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        if (hasNotifPerm && canExactAlarm) "抢票闹钟权限就绪" else "请开启通知与精确闹钟权限",
                                        style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                                        color = p.textPrimary
                                    )
                                    Text(
                                        if (hasNotifPerm && canExactAlarm) "支持息屏唤醒与重启自动恢复，可同步手机日历"
                                        else "开启后才能准点提醒提前 30 天开抢",
                                        style = LoongType.footnote.copy(fontSize = 12.5.sp),
                                        color = p.textSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .pressScale(
                                        scaleDown = AppPressScale.CONTROL,
                                        haptic = true,
                                        onClick = {
                                            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                viewModel.sendTestNotification()
                                            }
                                        }
                                    )
                                    .clip(RoundedCornerShape(AppRadii.full))
                                    .background(p.primarySoftBg)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("测试提醒", style = LoongType.caption, color = p.primary)
                            }
                        }

                        if (!hasNotifPerm || !canExactAlarm) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    OutlinedButton(
                                        onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, p.separator),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.bounceOnTouch()
                                    ) {
                                        Text("允许通知", style = LoongType.footnoteStrong)
                                    }
                                }
                                if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            runCatching { context.startActivity(intent) }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, p.separator),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.bounceOnTouch()
                                    ) {
                                        Text("开启精确闹钟", style = LoongType.footnoteStrong)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (plans.isNotEmpty()) {
                item {
                    SectionTitleRow(
                        title = "我的行程计划",
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .padding(horizontal = AppSpace.groupInset)
                    )
                }
            }

            // 2. 行程计划列表
            if (plans.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        SoftCard(contentPadding = 24.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TintedIconBadge(
                                    icon = Icons.Default.AlarmAdd,
                                    size = 52.dp,
                                    iconSize = 26.dp,
                                    cornerRadius = 16.dp
                                )
                                Text("还没有保存的往返行程计划", style = LoongType.headline, color = p.textPrimary)
                                Text(
                                    "在「往返」页挑选心仪的周末航班并点击「加入行程」，即可在这里设定放票开抢闹钟与系统日历提醒。",
                                    style = LoongType.footnote,
                                    color = p.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                items(plans, key = { it.id }) { plan ->
                    val planReminders = remindersByPlan[plan.id].orEmpty()
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        TripPlanCard(
                            plan = plan,
                            reminders = planReminders,
                            canExactAlarm = canExactAlarm,
                            isHighlighted = highlightedPlanId == plan.id,
                            isRechecking = recheckingId == plan.id,
                            onRecheckSeats = { viewModel.recheckPlanLiveSeats(plan) },
                            onAddReminderClick = { reminderTargetPlan = plan },
                            onExportCalendar = { exportPlanToSystemCalendar(context, plan) },
                            onDeletePlan = { viewModel.deleteTripPlan(plan) },
                            onToggleReminder = { viewModel.toggleReminder(it) },
                            onDeleteReminder = { viewModel.deleteReminder(it) }
                        )
                    }
                }
            }
        }

        CompactScrollTopBar(
            title = "行程提醒",
            collapseProgress = collapseProgress,
            trailingAction = {
                HeroCircleActionButton(
                    icon = Icons.Default.NotificationsActive,
                    contentDescription = "发测试提醒",
                    onClick = {
                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.sendTestNotification()
                        }
                    }
                )
            }
        )
    }

    reminderTargetPlan?.let { plan ->
        AddReminderDialog(
            plan = plan,
            onDismiss = { reminderTargetPlan = null },
            onAddReminder = { triggerMillis, label, title, msg ->
                viewModel.addReminder(
                    planId = plan.id,
                    triggerAtMillis = triggerMillis,
                    label = label,
                    title = title,
                    message = msg
                )
                reminderTargetPlan = null
            }
        )
    }
}

@Composable
private fun TripPlanCard(
    plan: TripPlanEntity,
    reminders: List<ReminderEntity>,
    canExactAlarm: Boolean,
    isHighlighted: Boolean,
    isRechecking: Boolean,
    onRecheckSeats: () -> Unit,
    onAddReminderClick: () -> Unit,
    onExportCalendar: () -> Unit,
    onDeletePlan: () -> Unit,
    onToggleReminder: (ReminderEntity) -> Unit,
    onDeleteReminder: (ReminderEntity) -> Unit
) {
    val p = LocalLoongPalette.current
    val timeFmt = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    SoftCard(
        cornerRadius = AppRadii.card,
        borderColor = if (isHighlighted) p.primary else null,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "${plan.fromCity} ⇄ ${plan.toCity}",
                    style = LoongType.title2.copy(fontSize = 19.sp),
                    color = p.textPrimary
                )
                if (plan.note.isNotBlank()) {
                    Text(plan.note, style = LoongType.footnote, color = p.textSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressScale(scaleDown = AppPressScale.ICON, onClick = onDeletePlan),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除计划", tint = p.textTertiary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.subtleSurface)
        ) {
            Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("去程 ${plan.outboundDate}", style = LoongType.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), color = p.textTertiary)
                        Text(
                            "${plan.outboundDepTime}→${plan.outboundArrTime} · ${plan.outboundFlightNo} (${plan.outboundStopover})",
                            style = LoongType.tabularMeta.copy(fontWeight = FontWeight.SemiBold),
                            color = p.textPrimary
                        )
                    }
                    SeatCountTag(plan.outboundLeftCount, plan.outboundDate)
                }
                HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("返程 ${plan.returnDate}", style = LoongType.caption.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), color = p.textTertiary)
                        Text(
                            "${plan.returnDepTime}→${plan.returnArrTime} · ${plan.returnFlightNo} (${plan.returnStopover})",
                            style = LoongType.tabularMeta.copy(fontWeight = FontWeight.SemiBold),
                            color = p.textPrimary
                        )
                    }
                    SeatCountTag(plan.returnLeftCount, plan.returnDate)
                }
                plan.lastVerifiedAt?.let { ts ->
                    val verifiedStr = Instant.ofEpochMilli(ts)
                        .atZone(ZoneId.systemDefault())
                        .format(timeFmt)
                    Text(
                        "最近实时核验：$verifiedStr",
                        style = LoongType.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                        color = p.textTertiary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRecheckSeats,
                enabled = !isRechecking,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, p.separator),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .bounceOnTouch(enabled = !isRechecking)
            ) {
                if (isRechecking) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = p.primary)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(5.dp))
                Text("查余票", style = LoongType.footnoteStrong)
            }

            Button(
                onClick = onAddReminderClick,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(42.dp)
                    .bounceOnTouch()
            ) {
                Icon(Icons.Default.AlarmAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("设抢票提醒", style = LoongType.footnoteStrong)
            }

            FilledTonalButton(
                onClick = onExportCalendar,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(42.dp)
                    .bounceOnTouch()
            ) {
                Icon(Icons.Default.Event, contentDescription = "写日历", modifier = Modifier.size(17.dp))
            }
        }

        AnimatedVisibility(
            visible = reminders.isNotEmpty(),
            enter = expandVertically(AppMotion.reveal()) + fadeIn(AppMotion.fade()),
            exit = shrinkVertically(AppMotion.reveal()) + fadeOut(AppMotion.fade(140))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = AppSpace.hairline,
                    color = p.separator
                )
                Text(
                    "已设定抢票提醒 (${reminders.size})",
                    style = LoongType.footnoteStrong,
                    color = p.textTertiary
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    reminders.forEach { r ->
                        val triggerDt = Instant.ofEpochMilli(r.triggerAtMillis)
                            .atZone(ChinaClock.ZONE)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val isExpired = r.fired || r.triggerAtMillis <= System.currentTimeMillis()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isExpired) p.subtleSurface else p.primarySoftBg.copy(alpha = 0.65f))
                                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.label, style = LoongType.filter, color = p.textPrimary)
                                    if (isExpired) {
                                        Text(
                                            if (r.fired) "已触发" else "已过期",
                                            style = LoongType.caption.copy(fontWeight = FontWeight.Medium),
                                            color = p.textTertiary
                                        )
                                    } else if (r.enabled && !canExactAlarm) {
                                        TonePillBadge(
                                            text = "非精确",
                                            backgroundColor = p.warningBg,
                                            contentColor = p.warning,
                                            cornerRadius = 6.dp
                                        )
                                    }
                                }
                                Text(
                                    "⏰ $triggerDt",
                                    style = LoongType.tabularMeta.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (isExpired) p.textSecondary else p.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = r.enabled && !isExpired,
                                    enabled = !isExpired,
                                    onCheckedChange = { onToggleReminder(r) }
                                )
                                IconButton(onClick = { onDeleteReminder(r) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除提醒", modifier = Modifier.size(18.dp), tint = p.textTertiary)
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
private fun SeatCountTag(count: Int?, flightDateStr: String) {
    val p = LocalLoongPalette.current
    val today = ChinaClock.today()
    val parsedDate = remember(flightDateStr) { runCatching { LocalDate.parse(flightDateStr) }.getOrNull() }
    val normalized = parsedDate?.let {
        RoundTripMatcher.normalizeCalendarStatus(
            rawStatus = null,
            date = it,
            today = today
        )
    }
    val (txt, bg, fg) = when {
        normalized == -2 -> Triple("不可用", p.dangerBg, p.danger)
        normalized == -3 -> Triple("已截止", p.dangerBg, p.danger)
        count == null -> Triple("未核验", p.cardBg, p.textTertiary)
        count > 0 -> Triple("畅飞余 $count", p.successBg, p.success)
        else -> Triple("暂无余票", p.dangerBg, p.danger)
    }
    TonePillBadge(
        text = txt,
        backgroundColor = bg,
        contentColor = fg,
        cornerRadius = 8.dp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    plan: TripPlanEntity,
    onDismiss: () -> Unit,
    onAddReminder: (triggerAtMillis: Long, label: String, title: String, message: String) -> Unit
) {
    val p = LocalLoongPalette.current
    val suggestions = remember(plan.id) { RoundTripMatcher.buildReminderSuggestions(plan) }
    var customDate by remember { mutableStateOf(ChinaClock.today().plusDays(1)) }
    var customHour by remember { mutableStateOf(9) }
    var customMinute by remember { mutableStateOf(55) }
    var customLabel by remember { mutableStateOf("自定义抢票提醒") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.cardBg,
        title = { Text("设定抢票提醒", style = LoongType.headline, color = p.textPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("智能预设（一键添加）", style = LoongType.footnoteStrong, color = p.primary)
                suggestions.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (s.isInPast) p.subtleSurface else p.primarySoftBg.copy(alpha = 0.65f))
                            .pressHighlight(
                                cornerRadius = 14.dp,
                                enabled = !s.isInPast,
                                onClick = {
                                    onAddReminder(s.triggerAtMillis, s.label, s.title, s.message)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(s.label, style = LoongType.filter, color = if (s.isInPast) p.textTertiary else p.textPrimary)
                            Text(
                                s.description,
                                style = LoongType.footnote.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                                color = p.textSecondary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (s.isInPast) "已过" else "添加",
                            style = LoongType.filter,
                            color = if (s.isInPast) p.textTertiary else p.primary
                        )
                    }
                }

                HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                Text("或自定义精确提醒时间", style = LoongType.footnoteStrong, color = p.primary)

                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text("提醒标签") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, p.separator),
                        modifier = Modifier
                            .weight(1f)
                            .bounceOnTouch()
                    ) {
                        Text(customDate.toString(), style = LoongType.tabularMeta)
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, p.separator),
                        modifier = Modifier
                            .weight(1f)
                            .bounceOnTouch()
                    ) {
                        Text("%02d:%02d".format(customHour, customMinute), style = LoongType.tabularMeta)
                    }
                }

                Button(
                    onClick = {
                        val dt = LocalDateTime.of(customDate, LocalTime.of(customHour, customMinute))
                        val millis = dt.atZone(ChinaClock.ZONE).toInstant().toEpochMilli()
                        val title = "抢票提醒：${plan.fromCity} ⇄ ${plan.toCity}"
                        val msg = "去程 ${plan.outboundDate} ${plan.outboundFlightNo}(${plan.outboundDepTime}) / 返程 ${plan.returnDate} ${plan.returnFlightNo}(${plan.returnDepTime})，请及时查票兑换！"
                        onAddReminder(millis, customLabel.ifBlank { "自定义提醒" }, title, msg)
                    },
                    shape = RoundedCornerShape(AppRadii.full),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .bounceOnTouch()
                ) {
                    Text("保存自定义提醒", style = LoongType.filter)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = LoongType.filter) }
        }
    )

    if (showDatePicker) {
        val initMs = customDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        customDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("确定", style = LoongType.filter) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = dpState)
        }
    }

    if (showTimePicker) {
        val tpState = rememberTimePickerState(initialHour = customHour, initialMinute = customMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = p.cardBg,
            title = { Text("选择提醒时间", style = LoongType.headline, color = p.textPrimary) },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    customHour = tpState.hour
                    customMinute = tpState.minute
                    showTimePicker = false
                }) { Text("确定", style = LoongType.filter) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } }
        )
    }
}

private fun exportPlanToSystemCalendar(context: Context, plan: TripPlanEntity) {
    val today = ChinaClock.today()
    val outDate = runCatching { LocalDate.parse(plan.outboundDate) }.getOrNull() ?: today
    val openDate = outDate.minusDays(30)
    val startDate = if (openDate.isAfter(today)) openDate else today.plusDays(1)
    val startMillis = LocalDateTime.of(startDate, LocalTime.of(9, 55))
        .atZone(ChinaClock.ZONE)
        .toInstant()
        .toEpochMilli()

    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 15 * 60 * 1000L)
        putExtra(CalendarContract.Events.TITLE, "抢长龙畅飞卡：${plan.fromCity} ⇄ ${plan.toCity}")
        putExtra(
            CalendarContract.Events.DESCRIPTION,
            "去程：${plan.outboundDate} ${plan.outboundFlightNo} (${plan.outboundDepTime}-${plan.outboundArrTime})\n" +
                "返程：${plan.returnDate} ${plan.returnFlightNo} (${plan.returnDepTime}-${plan.returnArrTime})\n" +
                if (plan.note.isNotBlank()) "备注：${plan.note}" else ""
        )
    }
    runCatching { context.startActivity(intent) }
}
