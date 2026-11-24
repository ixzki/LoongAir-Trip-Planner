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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

private const val APP_VERSION_NAME = "1.6.0"

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
    val session by viewModel.sessionState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val officialAirports by viewModel.officialAirports.collectAsState()
    val departureAirportsLoading by viewModel.departureAirportsLoading.collectAsState()
    val officialLoading by viewModel.officialDestLoading.collectAsState()
    val cardSyncLoading by viewModel.cardSyncLoading.collectAsState()
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
            containerColor = Color.White,
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
            containerColor = Color.White,
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
            containerColor = Color.White,
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
            containerColor = Color.White,
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
                        "当前选用 scgTid：${session.secCardGrantTid.ifBlank { "(空)" }}\n已识别卡数：${session.detectedCards.size}",
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

/**
 * 独立子页顶部栏（复刻 chher-public LargeTitleScaffold：38dp 纯白柔影圆钮 + 32sp Bold 大标题）
 */
@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
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
private fun CardsSubPage(
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
                                text = "当前生效授权 ID：${session.secCardGrantTid.ifBlank { "未识别，请同步卡包" }}",
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
                            Text("正在从长龙同步卡包...", style = LoongType.filter)
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
                                                "ID: ${card.grantTid}",
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
                        Text("请先返回「我的」登录长龙会员后再同步名下畅飞卡。", style = LoongType.footnote, color = p.textSecondary)
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
private fun SettingsSubPage(
    currentTheme: AppThemeStyle,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onSelectTheme: (AppThemeStyle) -> Unit,
    onResetTemplates: () -> Unit,
    onClearCache: () -> Unit,
    onOpenDiagnostic: () -> Unit,
    onOpenManual: () -> Unit,
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

        // 1. 外观双主题可视化选择（复刻 chher-public settings_appearance.dart StylePreviewCard）
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

        // 2. 通用分组卡（同心圆角，行间纯留白无生硬灰线）
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
                        title = "清空本地航线与缓存",
                        onClick = onClearCache
                    )
                    SettingsRowItem(
                        icon = Icons.Default.BugReport,
                        title = "卡包诊断（已脱敏）",
                        onClick = onOpenDiagnostic
                    )
                    SettingsRowItem(
                        icon = Icons.Default.Key,
                        title = "手动凭证 / scgTid",
                        onClick = onOpenManual
                    )
                    SettingsRowItem(
                        icon = Icons.Default.Info,
                        title = "关于 长龙畅飞规划",
                        trailingPill = "v$APP_VERSION_NAME",
                        onClick = onOpenAbout
                    )
                }
            }
        }

        // 3. 独立药丸退出登录按钮（复刻 chher-public settings_page.dart）
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
private fun AboutSubPage(onBack: () -> Unit) {
    val p = LocalLoongPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(p.pageBg),
        contentPadding = PaddingValues(bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { SubPageHeader(title = "关于 长龙畅飞规划", onBack = onBack) }

        // 复刻 chher-public about_page.dart：居中 88dp 圆角大图标 + 版本号
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
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
                        modifier = Modifier.size(44.dp)
                    )
                }
                Text(
                    text = "版本 $APP_VERSION_NAME",
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
                GroupedSectionLabel("介绍")
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                    Text(
                        text = "长龙畅飞规划是专为长龙航空畅飞卡旅客打造的纯本地 Android 助手，支持全国随心飞航网地图浏览、周末与指定日期往返智能配对、近 40 天放票日历核验与精确抢票闹钟提醒。",
                        style = LoongType.body,
                        color = p.textPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "所有会话凭据、卡包信息、缓存与行程计划均仅保存在您的手机本地，不经过任何第三方云服务器。",
                        style = LoongType.body,
                        color = p.textPrimary
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = AppSpace.groupInset),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupedSectionLabel("致谢与免责声明")
                SoftCard(cornerRadius = AppRadii.card, contentPadding = 16.dp) {
                    Text(
                        text = "• 开源参考：长龙官方接口协议参考开源项目 eureka-wu/clairline（MIT License）；界面动效、字阶与视觉语言致敬开源项目 zzNaLogiczz/chher-public。\n\n" +
                            "• 零预置原则：本应用不内置任何离线城市或历史时刻表，所有城市、航线与航班时刻均由您登录后直连长龙官方接口实时同步。\n\n" +
                            "• 免责声明：本应用为个人行程规划工具，与浙江长龙航空有限公司无任何官方关联；最终可兑资格、航班时刻与剩余座位数请以长龙航空官方 App 及微信小程序为准。",
                        style = LoongType.subhead.copy(lineHeight = 24.sp),
                        color = p.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * 复刻 chher-public LacEntryCell：
 * 按下弹性微缩 + 叠加主题 pressed 底色，松手按 entryTap 轻快回弹
 */
@Composable
private fun QuickActionTile(
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

/**
 * 复刻 chher-public settings_appearance.dart -> StylePreviewCard：
 * - 22dp 外圆角 + 6dp 垫边 + 104dp 高 16dp 圆角缩略预览图
 * - 选中描边平滑淡入 (2dp) + 右下角勾号以 AppMotion.pop 弹簧弹出
 */
@Composable
private fun StylePreviewCard(
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
            // 104dp 高度微缩主题示意图（精确还原 _StyleMiniature）
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
                // 迷你头像圆点 + 昵称条
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
                // 第一条微缩卡片（四色/朱砂点）
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
                // 第二条微缩卡片
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

            // 底部主题名称 + 弹簧勾号圆圈
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

/**
 * 复刻 chher-public _SettingsRow：
 * - 52dp 最小行高、18dp 同心圆角按压高亮 (PressHighlight，不缩行)
 * - 32dp IconTile (10dp 圆角, 18dp 图标) + 14dp 间距 + 16sp w500 标题 + 14.5sp text3 值 + 15dp 轻盈右箭头
 */
@Composable
private fun SettingsRowItem(
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YidunCaptchaDialog(
    onDismiss: () -> Unit,
    onValidated: (String) -> Unit,
    onError: (String) -> Unit
) {
    val p = LocalLoongPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "完成官方安全验证",
                    style = LoongType.headline,
                    color = p.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onSuccess(validate: String) {
                                        post { onValidated(validate) }
                                    }

                                    @JavascriptInterface
                                    fun onError(msg: String) {
                                        post { onError(msg) }
                                    }
                                },
                                "YidunBridge"
                            )
                            loadUrl("file:///android_asset/yidun_captcha.html")
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", style = LoongType.filter)
                    }
                }
            }
        }
    }
}
