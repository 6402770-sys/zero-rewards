package com.zero.rewards.core.scheduler

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zero.rewards.core.account.AccountStore
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.runner.StreakGuard
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Daily farm worker. Starts [FarmerForegroundService] once per enabled
 * account, serially, with 3–8 min pacing between accounts.
 */
class FarmWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = FarmerDatabase.get(applicationContext)
        val store = AccountStore(db)
        val accounts = store.all().shuffled()
        if (accounts.isEmpty()) { FarmerLog.i("no accounts configured"); return Result.success() }

        FarmerLog.i("daily farm: ${accounts.size} account(s)")
        for ((i, account) in accounts.withIndex()) {
            FarmerLog.i("farming ${account.label} (${i + 1}/${accounts.size})")
            val intent = Intent().apply {
                setClassName(
                    applicationContext.packageName,
                    "com.zero.rewards.service.FarmerForegroundService",
                )
                putExtra("account_id", account.id)
            }
            applicationContext.startForegroundService(intent)

            // Rough per-account wait: scheduled passes take 20-60 min per account.
            // Pace generously to let the previous run finish before kicking the next.
            val perAccountWait = 55L * 60_000L
            delay(perAccountWait)

            if (i < accounts.lastIndex) {
                val gap = Random.nextLong(3 * 60_000L, 8 * 60_000L)
                delay(gap)
            }
        }

        StreakGuard(applicationContext, db, "zero_farmer").check()
        return Result.success()
    }
}
