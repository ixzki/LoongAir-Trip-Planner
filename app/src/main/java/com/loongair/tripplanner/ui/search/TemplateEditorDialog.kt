package com.loongair.tripplanner.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.loongair.tripplanner.data.local.TripTemplateEntity
import com.loongair.tripplanner.data.repository.RoundTripMatcher
import com.loongair.tripplanner.ui.theme.AppSpace
import com.loongair.tripplanner.ui.theme.LocalLoongPalette
import com.loongair.tripplanner.ui.theme.LoongType
import com.loongair.tripplanner.ui.theme.bounceOnTouch

data class TimeWindowPreset(
    val label: String,
    val startMinute: Int,
    val endMinute: Int
)

val TIME_WINDOW_PRESETS = listOf(
    TimeWindowPreset("全天", 0, 23 * 60 + 59),
    TimeWindowPreset("早班 06-13", 6 * 60, 13 * 60),
    TimeWindowPreset("午后 12-18", 12 * 60, 18 * 60),
    TimeWindowPreset("午晚 12-24", 12 * 60, 23 * 60 + 59),
    TimeWindowPreset("晚班 18-24", 18 * 60, 23 * 60 + 59),
    TimeWindowPreset("下班后 19-24", 19 * 60, 23 * 60 + 59)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorDialog(
    initial: TripTemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (TripTemplateEntity) -> Unit,
    onDelete: ((TripTemplateEntity) -> Unit)? = null
) {
    val p = LocalLoongPalette.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val initialDays = remember(initial) {
        if (initial == null) setOf(5) else RoundTripMatcher.parseTemplateOutDays(initial)
    }
    var isAnyDay by remember { mutableStateOf(initial != null && initialDays.isEmpty()) }
    var selectedOutDays by remember { mutableStateOf(if (isAnyDay) emptySet() else initialDays) }
    var outStartMin by remember { mutableIntStateOf(initial?.outStartMinute ?: (18 * 60)) }
    var outEndMin by remember { mutableIntStateOf(initial?.outEndMinute ?: (23 * 60 + 59)) }
    var retOffsetDays by remember { mutableIntStateOf(initial?.retOffsetDays ?: 2) }
    var retStartMin by remember { mutableIntStateOf(initial?.retStartMinute ?: (12 * 60)) }
    var retEndMin by remember { mutableIntStateOf(initial?.retEndMinute ?: (23 * 60 + 59)) }
    var weekOffset by remember { mutableIntStateOf(initial?.weekOffset ?: 0) }
    var directOnly by remember { mutableStateOf(initial?.directOnly ?: false) }

    val daysLabel = RoundTripMatcher.formatTemplateOutDaysLabel(selectedOutDays, isAny = isAnyDay)
    val singleRetDayLabel = if (!isAnyDay && selectedOutDays.size == 1) {
        RoundTripMatcher.dayOfWeekLabel(((selectedOutDays.first() - 1 + retOffsetDays) % 7) + 1)
    } else null
    val autoPlaceholder = when {
        isAnyDay -> "${if (weekOffset == 1) "下周" else ""}任意日 → +${retOffsetDays}天回"
        selectedOutDays.size == 1 -> "${if (weekOffset == 1) "下" else ""}$daysLabel → ${singleRetDayLabel.orEmpty()}"
        else -> "${if (weekOffset == 1) "下周" else ""}$daysLabel → +${retOffsetDays}天回"
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = p.primarySoftBg,
        selectedLabelColor = p.primary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (initial == null) "新建往返模板" else "编辑往返模板",
                    style = LoongType.headline,
                    color = p.textPrimary
                )
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除模板", tint = p.danger)
                        Text("删除", style = LoongType.filter, color = p.danger)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模板名称") },
                    placeholder = { Text(autoPlaceholder) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "去程星期几（可多选，当前：$daysLabel）",
                    style = LoongType.footnoteStrong,
                    color = p.textSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = isAnyDay,
                        onClick = {
                            isAnyDay = true
                            selectedOutDays = emptySet()
                        },
                        colors = chipColors,
                        label = { Text("任意", style = LoongType.caption) }
                    )
                    (1..7).forEach { dow ->
                        val selected = !isAnyDay && dow in selectedOutDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (isAnyDay) {
                                    isAnyDay = false
                                    selectedOutDays = setOf(dow)
                                } else {
                                    val next = selectedOutDays.toMutableSet()
                                    if (dow in next) next.remove(dow) else next.add(dow)
                                    if (next.isEmpty() || next.size >= 7) {
                                        isAnyDay = true
                                        selectedOutDays = emptySet()
                                    } else {
                                        selectedOutDays = next.toSortedSet()
                                    }
                                }
                            },
                            colors = chipColors,
                            label = { Text(RoundTripMatcher.dayOfWeekLabel(dow), style = LoongType.caption) }
                        )
                    }
                }

                Text(
                    "去程起飞时段：${RoundTripMatcher.formatMinutes(outStartMin)} - ${RoundTripMatcher.formatMinutes(outEndMin)}",
                    style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                    color = p.textSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TIME_WINDOW_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = outStartMin == preset.startMinute && outEndMin == preset.endMinute,
                            onClick = {
                                outStartMin = preset.startMinute
                                outEndMin = preset.endMinute
                            },
                            colors = chipColors,
                            label = { Text(preset.label, style = LoongType.caption) }
                        )
                    }
                }

                HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)

                Text(
                    if (singleRetDayLabel != null) "停留天数（返程日：$singleRetDayLabel）"
                    else "停留天数（去程后 +${retOffsetDays} 天返程）",
                    style = LoongType.footnoteStrong,
                    color = p.textSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(1 to "次日回(1晚)", 2 to "2天后(2晚)", 3 to "3天后(3晚)", 4 to "4天后", 5 to "5天后", 7 to "7天后").forEach { (d, lbl) ->
                        FilterChip(
                            selected = retOffsetDays == d,
                            onClick = { retOffsetDays = d },
                            colors = chipColors,
                            label = { Text(lbl, style = LoongType.caption) }
                        )
                    }
                }

                Text(
                    "返程起飞时段：${RoundTripMatcher.formatMinutes(retStartMin)} - ${RoundTripMatcher.formatMinutes(retEndMin)}",
                    style = LoongType.footnoteStrong.copy(fontFeatureSettings = "tnum"),
                    color = p.textSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TIME_WINDOW_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = retStartMin == preset.startMinute && retEndMin == preset.endMinute,
                            onClick = {
                                retStartMin = preset.startMinute
                                retEndMin = preset.endMinute
                            },
                            colors = chipColors,
                            label = { Text(preset.label, style = LoongType.caption) }
                        )
                    }
                }

                HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)

                Text("默认周期偏移", style = LoongType.footnoteStrong, color = p.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = weekOffset == 0,
                        onClick = { weekOffset = 0 },
                        colors = chipColors,
                        label = { Text("最近一周", style = LoongType.caption) }
                    )
                    FilterChip(
                        selected = weekOffset == 1,
                        onClick = { weekOffset = 1 },
                        colors = chipColors,
                        label = { Text("顺延一周(下周)", style = LoongType.caption) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("默认仅看直飞航班", style = LoongType.subhead, color = p.textPrimary)
                    Switch(checked = directOnly, onCheckedChange = { directOnly = it })
                }

                if (initial != null && onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onDelete(initial) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceOnTouch()
                    ) {
                        Text("删除此模板", style = LoongType.filter, color = p.danger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalName = name.trim().ifBlank { autoPlaceholder }
                    val primaryDow = if (isAnyDay || selectedOutDays.isEmpty()) 0 else selectedOutDays.min()
                    val encodedDays = if (isAnyDay || selectedOutDays.isEmpty() || selectedOutDays.size >= 7) {
                        "ANY"
                    } else {
                        selectedOutDays.sorted().joinToString("")
                    }
                    onSave(
                        TripTemplateEntity(
                            id = initial?.id ?: 0L,
                            name = finalName,
                            outDayOfWeek = primaryDow,
                            outDaysOfWeek = encodedDays,
                            outStartMinute = outStartMin,
                            outEndMinute = outEndMin,
                            retOffsetDays = retOffsetDays,
                            retStartMinute = retStartMin,
                            retEndMinute = retEndMin,
                            weekOffset = weekOffset,
                            directOnly = directOnly,
                            sortOrder = initial?.sortOrder ?: 10
                        )
                    )
                }
            ) {
                Text("保存模板", style = LoongType.filter)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TemplateManagerDialog(
    templates: List<TripTemplateEntity>,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    onEdit: (TripTemplateEntity) -> Unit,
    onDelete: (TripTemplateEntity) -> Unit
) {
    val p = LocalLoongPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("管理往返模板 (${templates.size})", style = LoongType.headline, color = p.textPrimary)
                TextButton(onClick = onAddNew) {
                    Text("+ 新建", style = LoongType.filter)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (templates.isEmpty()) {
                    Text("暂无保存的模板，点击右上角「+ 新建」添加。", style = LoongType.subhead, color = p.textSecondary)
                } else {
                    templates.forEach { tpl ->
                        val outDays = RoundTripMatcher.parseTemplateOutDays(tpl)
                        val outDaysText = RoundTripMatcher.formatTemplateOutDaysLabel(tpl)
                        val retText = if (outDays.size == 1) {
                            RoundTripMatcher.dayOfWeekLabel(((outDays.first() - 1 + tpl.retOffsetDays) % 7) + 1)
                        } else {
                            "+${tpl.retOffsetDays}天"
                        }
                        val desc = "$outDaysText ${RoundTripMatcher.formatMinutes(tpl.outStartMinute)}-${RoundTripMatcher.formatMinutes(tpl.outEndMinute)} 去 · $retText ${RoundTripMatcher.formatMinutes(tpl.retStartMinute)}-${RoundTripMatcher.formatMinutes(tpl.retEndMinute)} 回"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(tpl.name, style = LoongType.rowLabel, color = p.textPrimary)
                                Text(desc, style = LoongType.footnote.copy(fontFeatureSettings = "tnum"), color = p.textTertiary)
                            }
                            Row {
                                IconButton(onClick = { onEdit(tpl) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = p.textSecondary)
                                }
                                IconButton(onClick = { onDelete(tpl) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = p.danger)
                                }
                            }
                        }
                        HorizontalDivider(thickness = AppSpace.hairline, color = p.separator)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", style = LoongType.filter) }
        }
    )
}
