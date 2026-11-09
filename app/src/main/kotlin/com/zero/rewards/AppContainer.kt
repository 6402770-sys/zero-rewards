package com.zero.rewards

import android.content.Context
import com.zero.rewards.core.account.AccountStore
import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.reminder.StreakReminderStore

class AppContainer(
    val appContext: Context,
    val db: FarmerDatabase,
) {
    val accountStore = AccountStore(db)
    val reminderStore = StreakReminderStore(db)
}
