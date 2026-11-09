package com.zero.rewards.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.reminder.StreakReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers every active streak reminder alarm after a device
 * reboot (alarms are wiped by the OS). Also handles LOCKED_BOOT_COMPLETED
 * and TIME_SET / TIMEZONE_CHANGED so the schedule follows wall-clock
 * changes instead of drifting an hour when the user travels or DST hits.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FarmerDatabase.get(appContext)
                val active = StreakReminderStore(db).allActive()
                FarmerLog.i("boot/time-change ($action): re-registering ${active.size} reminders")
                StreakReminderScheduler(appContext).registerAll(active)
            } catch (t: Throwable) {
                FarmerLog.e("boot: failed to reschedule reminders", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
