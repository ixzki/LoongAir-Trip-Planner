package com.loongair.tripplanner.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.loongair.tripplanner.MainActivity
import com.loongair.tripplanner.R
import com.loongair.tripplanner.data.local.AuthPrefs
import com.loongair.tripplanner.ui.theme.AppThemeStyle

object NotificationHelper {
    const val CHANNEL_ID = "loongair_ticket_reminders"
    const val EXTRA_PLAN_ID = "extra_plan_id"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"

    private const val COLOR_CRIMSON_PRIMARY = 0xFFAF2025.toInt()
    private const val COLOR_MIST_PRIMARY = 0xFF0D74CE.toInt()

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "畅飞卡抢票提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "畅飞卡行程放票开抢与兑换截止精确提醒"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            enableLights(true)
            lightColor = resolveAccentColor(context)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return hasRuntimePermission(context) && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
    }

    fun hasRuntimePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun sanitizeTitle(rawTitle: String): String {
        return rawTitle
            .replace("⏰", "")
            .replace("⚠️", "")
            .replace("⚠", "")
            .replace("🔎", "")
            .trim()
            .ifBlank { "畅飞卡提醒" }
    }

    fun resolveBadgeLabel(cleanTitle: String): String = when {
        cleanTitle.contains("测试") -> "测试通知"
        cleanTitle.contains("截止") -> "兑换截止"
        cleanTitle.contains("余票") -> "余票提醒"
        else -> "开抢提醒"
    }

    private fun resolveThemeStyle(context: Context): AppThemeStyle {
        val raw = runCatching {
            context.applicationContext
                .getSharedPreferences(AuthPrefs.PREFS_FILE_NAME, Context.MODE_PRIVATE)
                .getString(AuthPrefs.KEY_THEME_STYLE, AppThemeStyle.CRIMSON.key)
        }.getOrNull()
        return AppThemeStyle.fromKey(raw)
    }

    private fun resolveAccentColor(context: Context): Int {
        return when (resolveThemeStyle(context)) {
            AppThemeStyle.CRIMSON -> COLOR_CRIMSON_PRIMARY
            AppThemeStyle.MIST -> COLOR_MIST_PRIMARY
        }
    }

    private fun buildStyledRemoteViews(
        context: Context,
        layoutRes: Int,
        style: AppThemeStyle,
        badgeLabel: String,
        title: String,
        message: String,
        isExpanded: Boolean
    ): RemoteViews {
        val pillBgRes = if (style == AppThemeStyle.MIST) {
            R.drawable.bg_notif_pill_mist
        } else {
            R.drawable.bg_notif_pill_crimson
        }
        val pillFgColor = ContextCompat.getColor(
            context,
            if (style == AppThemeStyle.MIST) R.color.notif_pill_fg_mist else R.color.notif_pill_fg_crimson
        )
        return RemoteViews(context.packageName, layoutRes).apply {
            setTextViewText(R.id.notif_badge, badgeLabel)
            setInt(R.id.notif_badge, "setBackgroundResource", pillBgRes)
            setTextColor(R.id.notif_badge, pillFgColor)
            setTextViewText(R.id.notif_title, title)
            setTextViewText(R.id.notif_body, message)
            if (isExpanded) {
                val detailBgRes = if (style == AppThemeStyle.MIST) {
                    R.drawable.bg_notif_detail_mist
                } else {
                    R.drawable.bg_notif_detail_crimson
                }
                setInt(R.id.notif_detail_box, "setBackgroundResource", detailBgRes)
            }
        }
    }

    @SuppressLint("MissingPermission") // hasNotificationPermission checks POST_NOTIFICATIONS before posting.
    fun showReminderNotification(
        context: Context,
        reminderId: Long,
        planId: Long,
        title: String,
        message: String
    ): Boolean {
        ensureChannel(context)
        if (!hasNotificationPermission(context)) return false

        val cleanTitle = sanitizeTitle(title)
        val badgeLabel = resolveBadgeLabel(cleanTitle)
        val style = resolveThemeStyle(context)
        val accentColor = if (style == AppThemeStyle.MIST) COLOR_MIST_PRIMARY else COLOR_CRIMSON_PRIMARY

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PLAN_ID, planId)
        }
        val reqCode = (reminderId % Int.MAX_VALUE).toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            reqCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val compactView = buildStyledRemoteViews(
            context = context,
            layoutRes = R.layout.notification_reminder_compact,
            style = style,
            badgeLabel = badgeLabel,
            title = cleanTitle,
            message = message,
            isExpanded = false
        )
        val expandedView = buildStyledRemoteViews(
            context = context,
            layoutRes = R.layout.notification_reminder_expanded,
            style = style,
            badgeLabel = badgeLabel,
            title = cleanTitle,
            message = message,
            isExpanded = true
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_plane)
            .setColor(accentColor)
            .setContentTitle(cleanTitle)
            .setContentText(message)
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(
                reqCode,
                notification
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
