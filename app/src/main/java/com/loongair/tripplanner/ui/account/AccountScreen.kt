package com.loongair.tripplanner.ui.account

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.loongair.tripplanner.data.local.OfficialAirportEntity
import com.loongair.tripplanner.data.local.UserSessionState
import com.loongair.tripplanner.ui.MainViewModel
import com.loongair.tripplanner.ui.components.CompactScrollTopBar
import com.loongair.tripplanner.ui.components.FirstCitySyncPanel
import com.loongair.tripplanner.ui.components.GroupedSectionLabel
import com.loongair.tripplanner.ui.components.HeroHeaderOverlapContainer
import com.loongair.tripplanner.ui.components.LacquerPullRefreshBox
import com.loongair.tripplanner.ui.components.SoftCard
import com.loongair.tripplanner.ui.components.TintedIconBadge
import com.loongair.tripplanner.ui.components.TonePillBadge
import com.loongair.tripplanner.ui.components.chhersShadow
import com.loongair.tripplanner.ui.components.rememberHeaderCollapseProgress
import com.loongair.tripplanner.ui.search.CityPickerDialog
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ProfileSubPage(val depth: Int) {
    MAIN(0),
    CARDS(1),
    SETTINGS(1),
    ABOUT(2)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(viewModel: MainViewModel) {
    val p = LocalLoongPalette.current
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val officialAirports by viewModel.officialAirports.collectAsStateWithLifecycle()
    val departureAirportsLoading by viewModel.departureAirportsLoading.collectAsStateWithLifecycle()
    val officialLoading by viewModel.officialDestLoading.collectAsStateWithLifecycle()
    val cardSyncLoading by viewModel.cardSyncLoading.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val currentTheme = AppThemeStyle.fromKey(session.themeStyle)
    var subPage by remember { mutableStateOf(ProfileSubPage.MAIN) }

    var mobile by remember(session.mobile) { mutableStateOf(session.mobile) }
    var smsCode by remember { mutableStateOf("") }
    var verifyId by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var submittingLogin by remember { mutableStateOf(false) }
    var showYidunDialog by remember { mutableStateOf(false) }

    var showManualDialog by remember { mutableStateOf(false) }
    var showOfficialDestsDialog by remember { mutableStateOf(false) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    var showSyncOtherCityDialog by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = subPage != ProfileSubPage.MAIN) {
        subPage = when (subPage) {
            ProfileSubPage.ABOUT -> ProfileSubPage.SETTINGS
            else -> ProfileSubPage.MAIN
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    // 仿 chher-public / Cupertino 二级页方向性推入与弹出转场 (AppMotion.reveal)
    AnimatedContent(
        targetState = subPage,
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                (slideInHorizontally(AppMotion.revealOffset()) { it } + fadeIn(AppMotion.fade(180))) togetherWith
                    (slideOutHorizontally(AppMotion.revealOffset()) { -it / 4 } + fadeOut(AppMotion.fade(220)))
            } else {
                (slideInHorizontally(AppMotion.revealOffset()) { -it / 4 } + fadeIn(AppMotion.fade(220))) togetherWith
                    (slideOutHorizontally(AppMotion.revealOffset()) { it } + fadeOut(AppMotion.fade(180)))
            }
        },
        label = "accountSubPageNav"
    ) { page ->
        when (page) {
            ProfileSubPage.CARDS -> {
                CardsSubPage(
                    session = session,
                    cardSyncLoading = cardSyncLoading,
                    onBack = { subPage = ProfileSubPage.MAIN },
                    onResyncCards = { viewModel.resyncUserCards() },
                    onSelectCard = { tid, name -> viewModel.selectActiveCard(tid, name) },
                    onOpenDiagnostic = { showDiagnosticDialog = true },
                    onOpenManual = { showManualDialog = true }
                )
            }

            ProfileSubPage.SETTINGS -> {
                SettingsSubPage(
                    currentTheme = currentTheme,
                    isLoggedIn = session.isLoggedIn,
                    onBack = { subPage = ProfileSubPage.MAIN },
                    onSelectTheme = { viewModel.setThemeStyle(it.key) },
                    onResetTemplates = { viewModel.resetDefaultTemplates() },
                    onClearCache = { viewModel.clearLocalCaches() },
                    onOpenDiagnostic = { showDiagnosticDialog = true },
                    onOpenManual = { showManualDialog = true },
                    onOpenAbout = { subPage = ProfileSubPage.ABOUT },
                    onLogout = {
                        viewModel.logout()
                        subPage = ProfileSubPage.MAIN
                    }
                )
            }

            ProfileSubPage.ABOUT -> {
                AboutSubPage(onBack = { subPage = ProfileSubPage.SETTINGS })
            }

            ProfileSubPage.MAIN -> {
                ProfileMainPage(
                    session = session,
                    currentTheme = currentTheme,
                    hasSyncedData = networkState.hasSyncedData,
                    officialAirports = officialAirports,
                    departureAirportsLoading = departureAirportsLoading,
                    officialLoading = officialLoading,
                    officialSyncing = networkState.officialSyncing,
                    syncDoneCount = networkState.syncDoneCount,
                    syncTotalCount = networkState.syncTotalCount,
                    officialSyncProgress = networkState.officialSyncProgress,
                    officialSyncedCount = networkState.officialSyncedCount,
                    mobile = mobile,
                    onMobileChange = { mobile = it },
                    smsCode = smsCode,
                    onSmsCodeChange = { smsCode = it },
                    countdown = countdown,
                    submittingLogin = submittingLogin,
                    onOpenYidun = { showYidunDialog = true },
                    onSubmitLogin = {
                        scope.launch {
                            submittingLogin = true
                            val res = viewModel.loginBySmsCode(mobile, smsCode, verifyId)
                            submittingLogin = false
                            viewModel.showMessage(res.message)
                            if (res.ok) smsCode = ""
                        }
                    },
                    onRefreshAirports = { viewModel.refreshOfficialDepartureAirports(silent = false) },
                    onSyncCityAllRoutes = { city -> viewModel.syncSingleCityAllRoutes(city) },
                    onStopSync = { viewModel.stopOfficialNetworkSync() },
                    onOpenHomeCityPicker = { showHomeCityPicker = true },
                    onOpenCards = { subPage = ProfileSubPage.CARDS },
                    onSyncHomeRoutes = { viewModel.checkOfficialDestinationsForHome() },
                    onToggleNetworkSync = {
                        if (networkState.officialSyncing) viewModel.stopOfficialNetworkSync()
                        else viewModel.syncOfficialNetworkHubs()
                    },
                    onOpenSyncOtherCity = { showSyncOtherCityDialog = true },
                    onOpenOfficialDests = { showOfficialDestsDialog = true },
                    onOpenSettings = { subPage = ProfileSubPage.SETTINGS }
                )
            }
        }
    }

    // ---- 公共弹窗（统一纯白底色）----

    if (showYidunDialog) {
        YidunCaptchaDialog(
            onDismiss = { showYidunDialog = false },
            onValidated = { validateToken ->
                showYidunDialog = false
                scope.launch {
                    val res = viewModel.sendSmsCode(mobile, validateToken)
                    viewModel.showMessage(res.message)
                    if (res.ok) {
                        verifyId = res.verifyId
                        countdown = 60
                    }
                }
            },
            onError = { errMsg -> viewModel.showMessage(errMsg) }
        )
    }

    if (showHomeCityPicker) {
        CityPickerDialog(
            title = "选择常驻出发城市",
            cities = searchState.allOriginCities,
            cityMetaMap = searchState.cityMetaList.associateBy { it.city },
            allowAll = false,
            onDismiss = { showHomeCityPicker = false },
            onSelect = { city ->
                viewModel.setHomeCity(city)
                showHomeCityPicker = false
            }
        )
    }

    if (showSyncOtherCityDialog) {
        AlertDialog(
            onDismissRequest = { showSyncOtherCityDialog = false },
            containerColor = p.cardBg,
            title = { Text("同步其他城市全部航线", style = LoongType.headline, color = p.textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "输入或选择任意出发城市，将自动同步该城市全部官方可达目的地、去返班次时刻与放票日历。",
                        style = LoongType.footnote,
                        color = p.textSecondary
                    )
                    FirstCitySyncPanel(
                        officialAirports = officialAirports,
                        departureAirportsLoading = departureAirportsLoading,
                        officialSyncing = networkState.officialSyncing,
                        syncDoneCount = networkState.syncDoneCount,
                        syncTotalCount = networkState.syncTotalCount,
                        officialSyncProgress = networkState.officialSyncProgress,
                        onRefreshAirports = { viewModel.refreshOfficialDepartureAirports(silent = false) },
                        onSyncCityAllRoutes = { city ->
                            viewModel.syncSingleCityAllRoutes(city)
                            showSyncOtherCityDialog = false
                        },
                        onStopSync = { viewModel.stopOfficialNetworkSync() }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSyncOtherCityDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showOfficialDestsDialog) {
        val dests = searchState.officialAllowedDestinations.orEmpty()
        AlertDialog(
            onDismissRequest = { showOfficialDestsDialog = false },
            containerColor = p.cardBg,
            title = { Text("${session.homeCity}出发 · 已同步航线 (${dests.size})", style = LoongType.headline, color = p.textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (dests.isEmpty()) {
                        Text(
                            "尚未同步该城市航线，请点击下方按钮重新拉取。",
                            style = LoongType.footnote,
                            color = p.textSecondary
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dests.forEach { city ->
                                AssistChip(
                                    onClick = {
                                        viewModel.setDestinationFilter(city)
                                        viewModel.showMessage("已将往返搜索目标设为「$city」")
                                        showOfficialDestsDialog = false
                                    },
                                    label = { Text(city, style = LoongType.caption) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.checkOfficialDestinationsForHome() }) { Text("重新拉取", style = LoongType.filter) }
            },
            dismissButton = {
                TextButton(onClick = { showOfficialDestsDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showManualDialog) {
        var manualSession by remember { mutableStateOf("") }
        var manualTid by remember(session.secCardGrantTid) { mutableStateOf(session.secCardGrantTid) }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            containerColor = p.cardBg,
            title = { Text("手动凭证 / scgTid 设置", style = LoongType.headline, color = p.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = manualTid,
                        onValueChange = { manualTid = it },
                        label = { Text("畅飞卡授权 ID (scgTid)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (session.isLoggedIn) {
                        Button(
                            enabled = manualTid.isNotBlank(),
                            shape = RoundedCornerShape(AppRadii.full),
                            onClick = {
                                viewModel.updateScgTidManually(manualTid)
                                showManualDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .bounceOnTouch(enabled = manualTid.isNotBlank())
                        ) {
                            Text("仅更新 scgTid", style = LoongType.filter)
                        }
                        HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                    }
                    OutlinedTextField(
                        value = manualSession,
                        onValueChange = { manualSession = it },
                        label = { Text("SESSION Cookie 值（可选）") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        enabled = !submittingLogin && manualSession.isNotBlank(),
                        shape = RoundedCornerShape(AppRadii.full),
                        onClick = {
                            scope.launch {
                                submittingLogin = true
                                val res = viewModel.importManualCredentials(manualSession, manualTid)
                                submittingLogin = false
                                viewModel.showMessage(res.message)
                                if (res.ok) showManualDialog = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .bounceOnTouch(enabled = !submittingLogin && manualSession.isNotBlank())
                    ) {
                        Text("导入 SESSION 凭证", style = LoongType.filter)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showDiagnosticDialog) {
        val diagText = session.cardDiagnostic.ifBlank { "暂无诊断记录，请先点击「重新同步卡包」。" }
        AlertDialog(
            onDismissRequest = { showDiagnosticDialog = false },
            containerColor = p.cardBg,
            title = { Text("卡包同步诊断（已脱敏）", style = LoongType.headline, color = p.textPrimary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "当前选用 scgTid：${UserSessionState.maskGrantId(session.secCardGrantTid)}\n已识别卡数：${session.detectedCards.size}",
                        style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                        color = p.primary
                    )
                    Surface(color = p.subtleSurface, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = diagText,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = p.textPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(diagText))
                    viewModel.showMessage("已复制脱敏诊断信息")
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制诊断", style = LoongType.filter)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ProfileMainPage(
    session: UserSessionState,
    currentTheme: AppThemeStyle,
    hasSyncedData: Boolean,
    officialAirports: List<OfficialAirportEntity>,
    departureAirportsLoading: Boolean,
    officialLoading: Boolean,
    officialSyncing: Boolean,
    syncDoneCount: Int,
    syncTotalCount: Int,
    officialSyncProgress: String,
    officialSyncedCount: Int,
    mobile: String,
    onMobileChange: (String) -> Unit,
    smsCode: String,
    onSmsCodeChange: (String) -> Unit,
    countdown: Int,
    submittingLogin: Boolean,
    onOpenYidun: () -> Unit,
    onSubmitLogin: () -> Unit,
    onRefreshAirports: () -> Unit,
    onSyncCityAllRoutes: (String) -> Unit,
    onStopSync: () -> Unit,
    onOpenHomeCityPicker: () -> Unit,
    onOpenCards: () -> Unit,
    onSyncHomeRoutes: () -> Unit,
    onToggleNetworkSync: () -> Unit,
    onOpenSyncOtherCity: () -> Unit,
    onOpenOfficialDests: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = LocalLoongPalette.current
    val isMist = currentTheme == AppThemeStyle.MIST
    val listState = rememberLazyListState()
    val collapseProgress = rememberHeaderCollapseProgress(
        listState = listState,
        collapseDistanceDp = if (isMist) 76.dp else 128.dp
    )

    val topFloatingCard: @Composable () -> Unit = {
        when {
            !session.isLoggedIn && !hasSyncedData -> {
                LoginFloatingCard(
                    mobile = mobile,
                    onMobileChange = onMobileChange,
                    smsCode = smsCode,
                    onSmsCodeChange = onSmsCodeChange,
                    countdown = countdown,
                    submittingLogin = submittingLogin,
                    onOpenYidun = onOpenYidun,
                    onSubmitLogin = onSubmitLogin,
                    floating = true,
                    firstRunHint = "未预置航线数据，登录后优先同步一座城市即可使用"
                )
            }
            session.isLoggedIn && !hasSyncedData -> {
                FirstCitySyncFloatingCard(
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
            else -> {
                QuickEntryStripCard(
                    session = session,
                    isMist = isMist,
                    floating = true,
                    officialLoading = officialLoading,
                    officialSyncing = officialSyncing,
                    syncDoneCount = syncDoneCount,
                    syncTotalCount = syncTotalCount,
                    officialSyncProgress = officialSyncProgress,
                    onOpenHomeCityPicker = onOpenHomeCityPicker,
                    onSyncHomeRoutes = onSyncHomeRoutes,
                    onToggleNetworkSync = onToggleNetworkSync
                )
            }
        }
    }

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
            // 1. 仿 chher-public 我的页顶栏：朱砂漆面跨缝身份 vs 薄暮双雾光纸面身份卡
            if (isMist) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
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
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(top = 14.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "我的",
                                style = LoongType.largeTitle,
                                color = p.textPrimary,
                                modifier = Modifier.padding(horizontal = AppSpace.screen)
                            )
                            // 薄暮独立白纸身份卡（复刻 me_page.dart _MistyIdentity）
                            Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(p.primarySoftBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (session.isLoggedIn && session.userName.isNotBlank()) {
                                                    Text(
                                                        text = session.userName.take(1),
                                                        style = LoongType.title2.copy(fontSize = 22.sp),
                                                        color = p.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.Flight,
                                                        contentDescription = null,
                                                        tint = p.primary,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = if (session.isLoggedIn) session.userName else "未登录长龙会员",
                                                    style = LoongType.title2.copy(fontSize = 21.sp),
                                                    color = p.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (session.isLoggedIn) session.maskedMobile.ifBlank { "已绑定账号" } else "登录后同步专属航网",
                                                        style = LoongType.footnote.copy(fontFeatureSettings = "tnum"),
                                                        color = p.textTertiary
                                                    )
                                                    if (session.isLoggedIn) {
                                                        TonePillBadge(
                                                            text = session.memberLevel,
                                                            backgroundColor = p.primarySoftBg,
                                                            contentColor = p.primary,
                                                            cornerRadius = 8.dp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (session.isLoggedIn) {
                                            Row(
                                                modifier = Modifier
                                                    .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onOpenCards)
                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("卡片", style = LoongType.footnoteStrong, color = p.textSecondary)
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = p.textTertiary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 顶部主悬浮卡（未登录=直登卡；已登录未同步=优先同步单城卡；已就绪=三入口条）
                            Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                                topFloatingCard()
                            }
                        }
                    }
                }
            } else {
                item {
                    HeroHeaderOverlapContainer(
                        subtitle = "",
                        title = "我的",
                        watermark = "LOONG",
                        collapseProgress = collapseProgress,
                        heroExtraContent = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(p.heroChipBg)
                                            .border(1.5.dp, p.heroText.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (session.isLoggedIn && session.userName.isNotBlank()) {
                                            Text(
                                                text = session.userName.take(1),
                                                style = LoongType.title1.copy(fontSize = 24.sp),
                                                color = p.heroText
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Flight,
                                                contentDescription = null,
                                                tint = p.heroText,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            text = if (session.isLoggedIn) session.userName else "未登录长龙会员",
                                            style = LoongType.title1,
                                            color = p.heroText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (session.isLoggedIn) session.maskedMobile.ifBlank { "已绑定账号" } else "登录后同步专属航网",
                                                style = LoongType.footnote.copy(fontFeatureSettings = "tnum"),
                                                color = p.heroSubText
                                            )
                                            if (session.isLoggedIn) {
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.28f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = session.memberLevel,
                                                        style = LoongType.caption.copy(fontSize = 11.sp),
                                                        color = p.heroText,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (session.isLoggedIn) {
                                    Row(
                                        modifier = Modifier
                                            .pressScale(scaleDown = AppPressScale.CONTROL, haptic = true, onClick = onOpenCards)
                                            .clip(RoundedCornerShape(AppRadii.full))
                                            .background(p.heroChipBg)
                                            .padding(start = 13.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "卡片",
                                            style = LoongType.footnoteStrong,
                                            color = p.heroText
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = p.heroSubText,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        topFloatingCard()
                    }
                }
            }

            // 2. 若已保留本地同步数据但退出登录，仍提供快捷登录卡片
            if (!session.isLoggedIn && hasSyncedData) {
                item {
                    Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                        LoginFloatingCard(
                            mobile = mobile,
                            onMobileChange = onMobileChange,
                            smsCode = smsCode,
                            onSmsCodeChange = onSmsCodeChange,
                            countdown = countdown,
                            submittingLogin = submittingLogin,
                            onOpenYidun = onOpenYidun,
                            onSubmitLogin = onSubmitLogin,
                            floating = false,
                            firstRunHint = "凭据仅保存在本机，直连长龙官方网关，重新登录后可继续刷新实时余票与航线。"
                        )
                    }
                }
            }

            // 3. 复刻 chher-public _Tools：同心圆角白卡 (22dp 外 / 4dp 垫 / 18dp 行，行间只用留白无生硬灰线)
            item {
                Box(modifier = Modifier.padding(horizontal = AppSpace.groupInset)) {
                    SoftCard(cornerRadius = AppRadii.card, contentPadding = AppSpace.cardInset) {
                        if (hasSyncedData) {
                            SettingsRowItem(
                                icon = Icons.Default.CloudSync,
                                title = "同步其他城市航线",
                                trailingText = "扩展城市",
                                onClick = onOpenSyncOtherCity
                            )
                            SettingsRowItem(
                                icon = Icons.Default.TravelExplore,
                                title = "已同步航线",
                                trailingText = "已同步 $officialSyncedCount 条",
                                onClick = onOpenOfficialDests
                            )
                        }
                        SettingsRowItem(
                            icon = Icons.Default.Settings,
                            title = "设置",
                            trailingText = "${currentTheme.displayName}主题",
                            onClick = onOpenSettings
                        )
                    }
                }
            }

            // 底部预留落款安全区（对应 MeTokens.colophonClearance）
            item {
                Spacer(Modifier.height(52.dp))
            }
        }

        // 底部固定极简落款（复刻 chher-public me_page.dart Positioned bottom: 28）
        Text(
            text = "LOONG TRIP",
            style = LoongType.caption.copy(letterSpacing = 3.2.sp),
            color = p.textTertiary.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
        )

        CompactScrollTopBar(
            title = if (session.isLoggedIn) session.userName else "我的",
            collapseProgress = collapseProgress
        )
    }
}

@Composable
private fun LoginFloatingCard(
    mobile: String,
    onMobileChange: (String) -> Unit,
    smsCode: String,
    onSmsCodeChange: (String) -> Unit,
    countdown: Int,
    submittingLogin: Boolean,
    onOpenYidun: () -> Unit,
    onSubmitLogin: () -> Unit,
    floating: Boolean,
    firstRunHint: String
) {
    val p = LocalLoongPalette.current
    SoftCard(cornerRadius = AppRadii.card, floating = floating, contentPadding = 16.dp) {
        Text(
            "长龙会员 · 手机验证码直登",
            style = LoongType.headline,
            color = p.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            firstRunHint,
            style = LoongType.footnote,
            color = p.textSecondary
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = mobile,
            onValueChange = { if (it.length <= 11) onMobileChange(it.filter(Char::isDigit)) },
            label = { Text("11 位长龙会员手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = smsCode,
                onValueChange = { if (it.length <= 6) onSmsCodeChange(it.filter(Char::isDigit)) },
                label = { Text("短信验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                enabled = countdown == 0 && mobile.length == 11,
                onClick = onOpenYidun,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, p.separator),
                modifier = Modifier
                    .height(54.dp)
                    .bounceOnTouch(enabled = countdown == 0 && mobile.length == 11)
            ) {
                Text(if (countdown > 0) "${countdown}s" else "获取验证码", style = LoongType.filter)
            }
        }
        Spacer(Modifier.height(14.dp))

        Button(
            enabled = !submittingLogin && mobile.length == 11 && smsCode.length >= 4,
            shape = RoundedCornerShape(AppRadii.full),
            onClick = onSubmitLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .bounceOnTouch(enabled = !submittingLogin && mobile.length == 11 && smsCode.length >= 4)
        ) {
            if (submittingLogin) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (submittingLogin) "正在登录并同步畅飞卡..." else "立即登录",
                style = LoongType.rowLabel.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun FirstCitySyncFloatingCard(
    officialAirports: List<OfficialAirportEntity>,
    departureAirportsLoading: Boolean,
    officialSyncing: Boolean,
    syncDoneCount: Int,
    syncTotalCount: Int,
    officialSyncProgress: String,
    onRefreshAirports: () -> Unit,
    onSyncCityAllRoutes: (String) -> Unit,
    onStopSync: () -> Unit
) {
    val p = LocalLoongPalette.current
    SoftCard(cornerRadius = AppRadii.card, floating = true, contentPadding = 16.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TintedIconBadge(
                icon = Icons.Default.TravelExplore,
                size = 40.dp,
                iconSize = 20.dp,
                cornerRadius = 12.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "优先同步一座城市的所有航线",
                    style = LoongType.headline,
                    color = p.textPrimary
                )
                Text(
                    text = "同步完成后立即解锁「航线」「往返」「行程」全部功能",
                    style = LoongType.footnote,
                    color = p.textSecondary
                )
            }
        }
        Spacer(Modifier.height(14.dp))
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

@Composable
private fun QuickEntryStripCard(
    session: UserSessionState,
    isMist: Boolean,
    floating: Boolean,
    officialLoading: Boolean,
    officialSyncing: Boolean,
    syncDoneCount: Int,
    syncTotalCount: Int,
    officialSyncProgress: String,
    onOpenHomeCityPicker: () -> Unit,
    onSyncHomeRoutes: () -> Unit,
    onToggleNetworkSync: () -> Unit
) {
    val p = LocalLoongPalette.current
    SoftCard(cornerRadius = AppRadii.card, floating = floating, contentPadding = 10.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocationCity,
                label = if (session.homeCity.isNotBlank()) "常驻${session.homeCity}" else "选常驻城",
                tint = if (isMist) MistyAccentPillar.blue else p.primary,
                bg = if (isMist) MistyAccentPillar.blueBg else p.primarySoftBg,
                onClick = onOpenHomeCityPicker
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.TravelExplore,
                label = if (officialLoading) "同步中" else "同步航线",
                tint = if (isMist) MistyAccentPillar.green else p.primary,
                bg = if (isMist) MistyAccentPillar.greenBg else p.primarySoftBg,
                onClick = onSyncHomeRoutes
            )
            QuickActionTile(
                modifier = Modifier.weight(1f),
                icon = if (officialSyncing) Icons.Default.Stop else Icons.Default.CloudSync,
                label = if (officialSyncing) "停止同步" else "全网枢纽",
                tint = if (officialSyncing) p.danger else if (isMist) MistyAccentPillar.violet else p.primary,
                bg = if (officialSyncing) p.dangerBg else if (isMist) MistyAccentPillar.violetBg else p.primarySoftBg,
                onClick = onToggleNetworkSync
            )
        }

        if (officialSyncing) {
            Spacer(Modifier.height(10.dp))
            if (syncTotalCount > 0) {
                LinearProgressIndicator(
                    progress = { (syncDoneCount.toFloat() / syncTotalCount.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(AppRadii.full)),
                    color = p.primary,
                    trackColor = p.primarySoftBg
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(AppRadii.full)),
                    color = p.primary,
                    trackColor = p.primarySoftBg
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                officialSyncProgress,
                style = LoongType.caption.copy(fontFeatureSettings = "tnum"),
                color = p.primary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}
