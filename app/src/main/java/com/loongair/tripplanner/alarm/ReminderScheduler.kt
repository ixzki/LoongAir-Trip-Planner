package com.loongair.tripplanner.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.loongair.tripplanner.data.local.ReminderEntity

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(reminder: ReminderEntity) {
        if (!reminder.enabled || reminder.fired) {
            cancel(reminder.id)
            return
        }
        if (reminder.triggerAtMillis <= System.currentTimeMillis()) {
            return
        }
        val pi = buildPendingIntent(
            reminderId = reminder.id,
            planId = reminder.planId,
            title = reminder.title,
            message = reminder.message
        )
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    pi
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.triggerAtMillis,
                    pi
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pi
            )
        }
    }

    fun cancel(reminderId: Long) {
        val pi = buildPendingIntent(
            reminderId = reminderId,
            planId = -1L,
            title = "",
            message = ""
        )
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun buildPendingIntent(
        reminderId: Long,
        planId: Long,
        title: String,
        message: String
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.loongair.tripplanner.ACTION_TICKET_REMINDER_$reminderId"
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationHelper.EXTRA_PLAN_ID, planId)
            putExtra(NotificationHelper.EXTRA_TITLE, title)
            putExtra(NotificationHelper.EXTRA_MESSAGE, message)
        }
        return PendingIntent.getBroadcast(
            context,
            (reminderId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
