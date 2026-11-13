package com.zero.rewards.core.scheduler

import android.content.Context
import androidx.work.WorkManager

/**
 * Versions up to v0.1.2 enqueued an unattended nightly farm run through
 * WorkManager. That was never meant to ship: runs only start when the user
 * asks for one. WorkManager keeps enqueued work across app updates and
 * reboots, so installs upgraded from those versions still have the job
 * registered. [cancelLegacyDailyRun] removes it; it is a no-op otherwise.
 */
object FarmScheduler {

    private const val LEGACY_DAILY_WORK = "zero_daily_farm"

    fun cancelLegacyDailyRun(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_DAILY_WORK)
    }
}
