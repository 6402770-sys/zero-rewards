package com.zero.rewards.core.reminder

import com.zero.rewards.core.db.FarmerDatabase
import com.zero.rewards.core.db.entity.StreakReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StreakReminderStore(private val db: FarmerDatabase) {

    fun observe(): Flow<List<StreakReminder>> =
        db.streakReminderDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun allActive(): List<StreakReminder> =
        db.streakReminderDao().allActive().map { it.toDomain() }

    suspend fun byId(id: String): StreakReminder? =
        db.streakReminderDao().byId(id)?.toDomain()

    suspend fun upsert(reminder: StreakReminder) {
        db.streakReminderDao().upsert(reminder.toEntity())
    }

    suspend fun delete(id: String) { db.streakReminderDao().delete(id) }

    private fun StreakReminderEntity.toDomain() = StreakReminder(
        id = id,
        hourOfDay = hourOfDay,
        minute = minute,
        enabled = enabled,
        createdAtEpochMs = createdAtEpochMs,
    )

    private fun StreakReminder.toEntity() = StreakReminderEntity(
        id = id,
        hourOfDay = hourOfDay,
        minute = minute,
        enabled = enabled,
        createdAtEpochMs = createdAtEpochMs,
    )
}
