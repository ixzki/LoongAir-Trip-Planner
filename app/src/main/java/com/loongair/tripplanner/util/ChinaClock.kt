package com.loongair.tripplanner.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 长龙航空国内航班时刻、60/40 天放票日历与抢票窗口均以北京时间 (Asia/Shanghai, UTC+8) 为准。
 * 统一通过此工具获取当前日期与时间，避免用户在境外漫游或模拟器时区为 UTC 时出现跨日偏差。
 */
object ChinaClock {
    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun nowDateTime(): LocalDateTime = LocalDateTime.now(ZONE)

    fun nowFormatted(): String = nowDateTime().format(DATETIME_FMT)
}

/**
 * 在前后台切换（ON_RESUME）及跨零点返回页面时自动刷新的北京当前日期，
 * 避免使用静态 `remember { LocalDate.now() }` 导致跨零点后日历窗口陈旧。
 */
@Composable
fun rememberChinaToday(): LocalDate {
    var today by remember { mutableStateOf(ChinaClock.today()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val latest = ChinaClock.today()
                if (latest != today) {
                    today = latest
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return today
}

/**
 * 协程安全的 runCatching：严格透传 CancellationException，绝不吞掉任务取消信号。
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
