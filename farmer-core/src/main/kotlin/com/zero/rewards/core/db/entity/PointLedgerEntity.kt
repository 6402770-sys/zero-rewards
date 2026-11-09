package com.zero.rewards.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "point_ledger")
data class PointLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val epochMs: Long,
    val delta: Int,
    val reason: String,
)
