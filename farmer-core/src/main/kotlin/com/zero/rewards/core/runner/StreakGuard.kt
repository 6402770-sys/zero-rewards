package com.zero.rewards.core.runner

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.zero.rewards.core.account.AccountStore
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.log.FarmerLog
import java.util.Calendar

/**
 * Checks each enabled account's last run against the local day boundary.
 * If an account did not complete any Ok task today and the clock is past
 * [reminderHour] local time, posts a per-account warning notification.
 *
 * The next scheduled FarmWorker run handles the actual backfill; this guard
 * only surfaces the risk so you notice before the streak breaks at midnight.
 */
class StreakGuard(
    private val context: Context,
    private val db: FarmerDatabase,
    private val channelId: String,
    private val reminderHour: Int = 21,
) {

    suspend fun check() {
        val store = AccountStore(db)
        val accounts = store.all()
        val cal = Calendar.getInstance()
        val nowH = cal.get(Calendar.HOUR_OF_DAY)
        if (nowH < reminderHour) {
            FarmerLog.d("StreakGuard: before reminder hour ($reminderHour); no checks")
            return
        }

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        var idSeed = 1_000
        for (account in accounts) {
            val last = account.lastRunEpochMs ?: 0L
            if (last >= dayStart) continue
            val label = account.label
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Streak at risk: $label")
                .setContentText("No successful run today. The next scheduled window will try again.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            nm.notify(idSeed++, builder.build())
            FarmerLog.w("StreakGuard: $label missed today's window")
        }
    }
}
