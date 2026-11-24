package com.loongair.tripplanner.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loongair.tripplanner.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RESCHEDULE_ACTIONS) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = ReminderScheduler(context)
                val activeReminders = AppDatabase.getInstance(context)
                    .reminderDao()
                    .getPendingActive(System.currentTimeMillis())
                activeReminders.forEach { scheduler.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
