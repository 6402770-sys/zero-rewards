package com.zero.rewards

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.scheduler.FarmScheduler

class ZeroApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, FarmerDatabase.get(this))
        createNotificationChannel()
        if (isMainProcess()) FarmScheduler.cancelLegacyDailyRun(this)
    }

    private fun isMainProcess(): Boolean =
        android.app.Application.getProcessName() == packageName

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_FARMER,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_FARMER = "zero_farmer"
    }
}
