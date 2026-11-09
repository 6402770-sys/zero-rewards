package com.zero.rewards.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val kind: String,
    val subKind: String?,
    val status: String,
    val pointsClaimed: Int,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long?,
    val detail: String?,
)
