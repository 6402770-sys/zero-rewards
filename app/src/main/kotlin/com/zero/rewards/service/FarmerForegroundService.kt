package com.zero.rewards.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zero.rewards.R
import com.zero.rewards.ZeroApp
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.runner.FarmRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts one or more sequential account farm runs. Started from the
 * dashboard play button or the streak-prompt
 * batch picker. Can be stopped from the dashboard via [ACTION_STOP]
 * which cancels the in-flight run AND clears any queued accounts.
 */
class FarmerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var job: Job? = null
    private val pending = ArrayDeque<String>()
    private val queueLock = Any()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FarmerLog.i("stop requested by user")
            synchronized(queueLock) { pending.clear() }
            job?.cancel()
            FarmerState.setRunning(null)
            FarmerState.setPhase(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val incoming = extractAccountIds(intent)
        if (incoming.isEmpty()) {
            if (job?.isActive != true) stopSelf(startId)
            return START_NOT_STICKY
        }

        // If a job is already running, just append — the current loop picks
        // up new items each iteration.
        val wasRunning = job?.isActive == true
        synchronized(queueLock) { pending.addAll(incoming) }
        if (wasRunning) {
            FarmerLog.i("queue: appended ${incoming.size} id(s); total pending=${pending.size}")
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("Starting…"))

        val host = ZeroAccessibilityService.get()
        if (host == null) {
            FarmerLog.e("accessibility service not bound; aborting")
            synchronized(queueLock) { pending.clear() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId); return START_NOT_STICKY
        }

        job = scope.launch {
            try {
                val db = FarmerDatabase.get(applicationContext)
                val store = com.zero.rewards.core.account.AccountStore(db)
                while (true) {
                    val nextId = synchronized(queueLock) {
                        if (pending.isEmpty()) null else pending.removeFirst()
                    } ?: break
                    FarmerState.setRunning(nextId)
                    FarmerState.setPhase(null)
                    val account = store.byId(nextId)
                    if (account == null) {
                        FarmerLog.e("service: unknown account $nextId, skipping")
                        continue
                    }
                    FarmerLog.i("service: starting ${account.label}")
                    runCatching { FarmRun(applicationContext, db, host).execute(account) }
                        .onFailure { FarmerLog.e("farm crashed for ${account.label}", it) }
                }
            } finally {
                FarmerState.setRunning(null)
                FarmerState.setPhase(null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun extractAccountIds(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        val list = intent.getStringArrayListExtra(EXTRA_ACCOUNT_IDS)
        if (!list.isNullOrEmpty()) return list.filter { it.isNotBlank() }
        val single = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }

    override fun onDestroy() {
        job?.cancel()
        synchronized(queueLock) { pending.clear() }
        FarmerState.setRunning(null)
        FarmerState.setPhase(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, ZeroApp.CHANNEL_FARMER)
            .setContentTitle(getString(R.string.notif_title_running))
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val NOTIF_ID = 42
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_ACCOUNT_IDS = "account_ids"
        const val ACTION_STOP = "com.zero.rewards.ACTION_STOP"

        fun start(context: Context, accountId: String) {
            val i = Intent(context, FarmerForegroundService::class.java)
                .putExtra(EXTRA_ACCOUNT_ID, accountId)
            context.startForegroundService(i)
        }

        fun startBatch(context: Context, accountIds: List<String>) {
            if (accountIds.isEmpty()) return
            val i = Intent(context, FarmerForegroundService::class.java)
                .putStringArrayListExtra(EXTRA_ACCOUNT_IDS, ArrayList(accountIds))
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, FarmerForegroundService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
