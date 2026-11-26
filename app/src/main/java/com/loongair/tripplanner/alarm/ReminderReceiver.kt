package com.loongair.tripplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.loongair.tripplanner.data.local.AppDatabase
import com.loongair.tripplanner.data.local.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal const val REMINDER_EARLY_TOLERANCE_MS = 15_000L

internal enum class ReminderTriggerDecision { SKIP, RESCHEDULE, FIRE }

internal fun evaluateReminderTrigger(
    reminder: ReminderEntity,
    nowMillis: Long
): ReminderTriggerDecision = when {
    !reminder.enabled || reminder.fired -> ReminderTriggerDecision.SKIP
    reminder.triggerAtMillis > nowMillis + REMINDER_EARLY_TOLERANCE_MS -> ReminderTriggerDecision.RESCHEDULE
    else -> ReminderTriggerDecision.FIRE
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(NotificationHelper.EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val toReschedule = db.withTransaction {
                    val reminder = db.reminderDao().getById(reminderId) ?: return@withTransaction null
                    when (evaluateReminderTrigger(reminder, System.currentTimeMillis())) {
                        ReminderTriggerDecision.SKIP -> null
                        ReminderTriggerDecision.RESCHEDULE -> reminder
                        ReminderTriggerDecision.FIRE -> {
                            if (NotificationHelper.showReminderNotification(context, reminder.id, reminder.planId, reminder.title, reminder.message)) {
                                db.reminderDao().markFired(reminder.id)
                            }
                            null
                        }
                    }
                }
                if (toReschedule != null) {
                    ReminderScheduler(context).schedule(toReschedule)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
