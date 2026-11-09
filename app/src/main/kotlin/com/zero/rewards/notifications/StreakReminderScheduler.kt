package com.zero.rewards.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.reminder.StreakReminder
import java.util.Calendar

/**
 * Schedules per-reminder daily AlarmManager alarms. Uses
 * setAndAllowWhileIdle (inexact ±15 min on doze but no special
 * permission needed on Android 12+ — SCHEDULE_EXACT_ALARM is user-
 * revocable and brittle). Each alarm fires once; the receiver
 * reschedules for the next day.
 */
class StreakReminderScheduler(private val context: Context) {

    /** Register (or cancel) one reminder based on its enabled flag. */
    fun apply(reminder: StreakReminder) {
        if (reminder.enabled) register(reminder) else cancel(reminder.id)
    }

    /** Re-register all active reminders — called from BootReceiver. */
    fun registerAll(reminders: List<StreakReminder>) {
        for (r in reminders) if (r.enabled) register(r)
    }

    fun cancel(id: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(id, Intent.FLAG_RECEIVER_FOREGROUND))
        FarmerLog.d("reminder: cancel id=$id")
    }

    private fun register(r: StreakReminder) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerMs = nextTriggerMs(r.hourOfDay, r.minute)
        val pi = pendingIntent(r.id)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        FarmerLog.d("reminder: schedule id=${r.id} at ${r.formatTime()} (triggerMs=$triggerMs)")
    }

    private fun pendingIntent(id: String, extraFlags: Int = 0): PendingIntent {
        val intent = Intent(context, StreakReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, id)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags or extraFlags)
    }

    private fun nextTriggerMs(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If target already passed today, fire tomorrow.
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    companion object {
        const val ACTION_FIRE = "com.zero.rewards.STREAK_REMINDER_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
