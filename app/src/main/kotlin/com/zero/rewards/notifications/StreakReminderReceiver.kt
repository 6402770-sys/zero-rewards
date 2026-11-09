package com.zero.rewards.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.zero.rewards.MainActivity
import com.zero.rewards.ZeroApp
import com.zero.rewards.core.account.Account
import com.zero.rewards.core.account.AccountStore
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.reminder.StreakReminderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Fires when a user-configured streak reminder alarm triggers.
 * Posts a notification listing the accounts that haven't farmed yet
 * today, then reschedules itself for the same time tomorrow.
 */
class StreakReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StreakReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getStringExtra(StreakReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FarmerDatabase.get(appContext)
                val accountStore = AccountStore(db)
                val reminderStore = StreakReminderStore(db)

                val allAccounts = accountStore.all()
                val dayStart = startOfTodayMs()
                val missed = allAccounts.filter { (it.lastRunEpochMs ?: 0L) < dayStart }

                FarmerLog.i("reminder fire: id=$reminderId missed=${missed.size}/${allAccounts.size}")
                postStreakNotification(appContext, missed, allAccounts.size)

                // Reschedule for tomorrow (or cancel if the reminder was
                // disabled / deleted between schedule and fire).
                val reminder = reminderStore.byId(reminderId)
                if (reminder != null && reminder.enabled) {
                    StreakReminderScheduler(appContext).apply(reminder)
                }
            } catch (t: Throwable) {
                FarmerLog.e("reminder: onReceive failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun postStreakNotification(
        context: Context,
        missed: List<Account>,
        total: Int,
    ) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val (title, body) = when {
            total == 0 -> "Zero Rewards" to "No hay cuentas configuradas todavía."
            missed.isEmpty() -> "Racha a salvo" to "Todas tus $total cuentas farmearon hoy."
            missed.size == 1 -> "Racha en riesgo — ${missed[0].label}" to
                "${missed[0].label} todavía no farmeó hoy. Tocá para correr."
            else -> "Racha en riesgo — ${missed.size} cuentas" to
                missed.joinToString(", ") { it.label }
        }
        val openApp = android.app.PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(StreakPromptBus.EXTRA_STREAK_PROMPT, true)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, ZeroApp.CHANNEL_FARMER)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        nm.notify(STREAK_NOTIF_ID, builder.build())
    }

    private fun startOfTodayMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val STREAK_NOTIF_ID = 42_001
    }
}
