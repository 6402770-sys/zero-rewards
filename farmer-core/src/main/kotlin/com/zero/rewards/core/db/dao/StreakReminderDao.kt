package com.zero.rewards.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zero.rewards.core.db.entity.StreakReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakReminderDao {
    @Query("SELECT * FROM streak_reminders ORDER BY hourOfDay ASC, minute ASC")
    fun observeAll(): Flow<List<StreakReminderEntity>>

    @Query("SELECT * FROM streak_reminders WHERE enabled = 1")
    suspend fun allActive(): List<StreakReminderEntity>

    @Query("SELECT * FROM streak_reminders WHERE id = :id")
    suspend fun byId(id: String): StreakReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: StreakReminderEntity)

    @Query("DELETE FROM streak_reminders WHERE id = :id")
    suspend fun delete(id: String)
}
