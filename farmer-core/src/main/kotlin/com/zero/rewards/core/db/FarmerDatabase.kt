package com.zero.rewards.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zero.rewards.core.db.dao.AccountDao
import com.zero.rewards.core.db.dao.PointLedgerDao
import com.zero.rewards.core.db.dao.RunDao
import com.zero.rewards.core.db.dao.StreakReminderDao
import com.zero.rewards.core.db.entity.AccountEntity
import com.zero.rewards.core.db.entity.PointLedgerEntity
import com.zero.rewards.core.db.entity.RunEntity
import com.zero.rewards.core.db.entity.StreakReminderEntity
import com.zero.rewards.core.db.entity.TaskEntity

@Database(
    entities = [
        AccountEntity::class,
        RunEntity::class,
        TaskEntity::class,
        PointLedgerEntity::class,
        StreakReminderEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class FarmerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun runDao(): RunDao
    abstract fun pointLedgerDao(): PointLedgerDao
    abstract fun streakReminderDao(): StreakReminderDao

    companion object {
        @Volatile private var instance: FarmerDatabase? = null

        fun get(context: Context): FarmerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FarmerDatabase::class.java,
                    "zero_farmer.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
