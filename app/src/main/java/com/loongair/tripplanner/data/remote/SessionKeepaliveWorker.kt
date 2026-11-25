package com.loongair.tripplanner.data.remote

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.loongair.tripplanner.TripPlannerApp
import java.util.concurrent.TimeUnit

sealed class KeepaliveResult {
    data object NotLoggedIn : KeepaliveResult()
    data object SkippedFresh : KeepaliveResult()
    data class Alive(val tokenRotated: Boolean) : KeepaliveResult()
    data object Refreshed : KeepaliveResult()
    data object Expired : KeepaliveResult()
    data class TransientError(val reason: String) : KeepaliveResult()
}

/**
 * 后台定时会话保活 Worker：
 * 仅在设备联网且用户处于登录状态时，定时向长龙网关发送轻量级心跳（sales_user_userDetailInfo），
 * 刷新服务端滑动过期时间并落盘服务端下发的新 SESSION Cookie；遇到 403/100001 时尝试静默 refreshToken。
 */
class SessionKeepaliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TripPlannerApp ?: return Result.success()
        if (!app.authPrefs.current().isLoggedIn) {
            SessionKeepaliveScheduler.cancel(applicationContext)
            return Result.success()
        }
        return when (app.api.keepaliveSession(force = false)) {
            is KeepaliveResult.Alive,
            is KeepaliveResult.Refreshed,
            is KeepaliveResult.SkippedFresh -> Result.success()

            KeepaliveResult.NotLoggedIn -> {
                SessionKeepaliveScheduler.cancel(applicationContext)
                Result.success()
            }

            KeepaliveResult.Expired -> Result.success()

            is KeepaliveResult.TransientError -> {
                if (runAttemptCount < 2) Result.retry() else Result.success()
            }
        }
    }
}

object SessionKeepaliveScheduler {
    private const val UNIQUE_WORK_NAME = "loongair_session_keepalive"
    private const val REPEAT_INTERVAL_MINUTES = 30L
    private const val FLEX_INTERVAL_MINUTES = 10L

    fun sync(context: Context, isLoggedIn: Boolean) {
        if (isLoggedIn) {
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SessionKeepaliveWorker>(
            REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            FLEX_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
