package com.zero.rewards.core.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object FarmScheduler {

    private const val DAILY_WORK = "zero_daily_farm"

    fun scheduleDaily(context: Context) {
        val jitterMinutes = Random.nextLong(0, 90)
        val request = PeriodicWorkRequestBuilder<FarmWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(hoursUntilWindow(2) * 60 + jitterMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK)
    }

    /** Minutes until the next `targetHour` (local time), floor 10 min. */
    private fun hoursUntilWindow(targetHour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        val nowH = cal.get(java.util.Calendar.HOUR_OF_DAY)
        var delta = targetHour - nowH
        if (delta <= 0) delta += 24
        return delta.toLong()
    }
}
