package com.zero.rewards.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val label: String,
    val bingEmail: String,
    val locale: String,
    val market: String,
    val country: String,
    val createdAtEpochMs: Long,
    val lastRunEpochMs: Long?,
    val lastKnownPoints: Int,
    val streakDays: Int,
    val searchCount: Int,
    val searchDelayMinSec: Int,
    val searchDelayMaxSec: Int,
    val farmSearch: Boolean,
    val farmDailySet: Boolean,
    val farmMoreActivities: Boolean,
    val farmReadToEarn: Boolean,
    val farmIntervalos: Boolean,
)
