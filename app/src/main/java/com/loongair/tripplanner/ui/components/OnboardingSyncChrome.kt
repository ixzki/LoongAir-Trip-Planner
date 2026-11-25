package com.loongair.tripplanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loongair.tripplanner.data.local.OfficialAirportEntity
import com.loongair.tripplanner.data.local.UserSessionState
import com.loongair.tripplanner.data.remote.CityIataMapper
import com.loongair.tripplanner.data.remote.CityMeta
import com.loongair.tripplanner.ui.search.CityPickerDialog
import com.loongair.tripplanner.ui.theme.AppPressScale
import com.loongair.tripplanner.ui.theme.AppRadii
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.bounceOnTouch
import com.loongair.tripplanner.ui.theme.pressScale

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
                                title = "登录账号",
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
                Text("正在获取官方国内城市目录...", style = LoongType.footnote, color = p.textSecondary)
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
        val dirCities = remember(officialAirports) { officialAirports.map { it.cityName }.distinct() }
        val dirMetaMap = remember(officialAirports) {
            officialAirports.associate { ap ->
                ap.cityName to CityMeta(
                    city = ap.cityName,
                    code = ap.airportCode,
                    airport = ap.airportName,
                    pinyin = ap.pinyin
                )
            }
        }
        CityPickerDialog(
            title = "选择出发城市 (${dirCities.size})",
            cities = dirCities,
            cityMetaMap = dirMetaMap,
            allowAll = false,
            onDismiss = { showFullDirectoryDialog = false },
            onSelect = { selected ->
                if (selected.isNotBlank()) {
                    cityInput = selected
                }
                showFullDirectoryDialog = false
            }
        )
    }
}

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
