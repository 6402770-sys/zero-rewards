package com.zero.rewards.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-configured daily streak notification. Fires once per day at
 * [hourOfDay]:[minute] local time. When [enabled] is false the alarm is
 * cancelled (row kept so the user can toggle back on without re-adding).
 */
@Entity(tableName = "streak_reminders")
data class StreakReminderEntity(
    @PrimaryKey val id: String,
    val hourOfDay: Int,
    val minute: Int,
    val enabled: Boolean,
    val createdAtEpochMs: Long,
)
