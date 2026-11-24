package com.loongair.tripplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loongair.tripplanner.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
        val planId = intent.getLongExtra(NotificationHelper.EXTRA_PLAN_ID, -1L)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "⏰ 长龙畅飞卡抢票提醒"
        val message = intent.getStringExtra(NotificationHelper.EXTRA_MESSAGE) ?: "你安排的畅飞卡行程到了抢票提醒时间，快去查余票吧！"

        NotificationHelper.showReminderNotification(
            context = context,
            reminderId = if (reminderId > 0) reminderId else System.currentTimeMillis(),
            planId = planId,
            title = title,
            message = message
        )

        if (reminderId > 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.getInstance(context).reminderDao().markFired(reminderId)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
