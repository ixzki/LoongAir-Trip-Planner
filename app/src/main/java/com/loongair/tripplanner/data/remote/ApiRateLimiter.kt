package com.loongair.tripplanner.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.random.Random

class SessionExpiredException(message: String) : IllegalStateException(message)

class ApiRateLimitException(message: String) : IllegalStateException(message)

/**
 * 全局网关限流与风控退避控制器：
 * - 通过协程 Mutex 保证所有业务查询请求（日历、余票、可达航线）全局最小间隔，防止多入口并发突刺触发风控；
 * - 遇到 HTTP 429 / 502 / 503 / 504 或网关繁忙提示时，自动拉长全局冷却窗口并执行带抖动的指数退避。
 */
class ApiRateLimiter(
    private val minQueryIntervalMs: Long = 240L,
    private val timeProvider: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var nextAllowedAtMs: Long = 0L
    private var consecutiveThrottleCount: Int = 0

    suspend fun acquirePermit(isAuthOrVerify: Boolean = false) {
        val waitMs = mutex.withLock {
            val now = timeProvider()
            val spacing = if (isAuthOrVerify) 80L else minQueryIntervalMs + Random.nextLong(0, 70)
            val target = max(now, nextAllowedAtMs)
            nextAllowedAtMs = target + spacing
            (target - now).coerceAtLeast(0L)
        }
        if (waitMs > 0L) {
            delay(waitMs)
        }
    }

    suspend fun onSuccess() {
        mutex.withLock {
            if (consecutiveThrottleCount > 0) {
                consecutiveThrottleCount = 0
            }
        }
    }

    /**
     * 记录一次限流或瞬时网关繁忙，返回建议的退避等待毫秒数。
     */
    suspend fun recordThrottleAndComputeBackoffMs(): Long {
        return mutex.withLock {
            consecutiveThrottleCount = (consecutiveThrottleCount + 1).coerceAtMost(4)
            val base = 900L * (1L shl (consecutiveThrottleCount - 1))
            val backoff = base + Random.nextLong(150L, 550L)
            val now = timeProvider()
            nextAllowedAtMs = max(nextAllowedAtMs, now + backoff)
            backoff
        }
    }

    companion object {
        private val THROTTLE_KEYWORDS = listOf("频繁", "过快", "限流", "繁忙", "稍后再试", "风控", "系统忙")

        fun isThrottleSignal(httpCode: Int, errorCode: Int, message: String): Boolean {
            if (httpCode == 429 || httpCode == 502 || httpCode == 503 || httpCode == 504) return true
            if (errorCode == 429) return true
            return THROTTLE_KEYWORDS.any { message.contains(it) }
        }
    }
}
