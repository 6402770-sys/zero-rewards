package com.zero.rewards.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val status: String,
    val pointsBefore: Int,
    val pointsAfter: Int,
    val errorMessage: String?,
)
