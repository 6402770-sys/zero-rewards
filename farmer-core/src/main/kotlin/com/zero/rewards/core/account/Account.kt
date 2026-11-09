package com.zero.rewards.core.account

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A farmed account. [label] is user-visible. [bingEmail] is the Microsoft
 * email string that the Bing app's account switcher uses to find the
 * right MSA — it must match the email as it appears in the Bing app's
 * account list exactly (case-insensitive substring match).
 */
@Serializable
data class Account(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val bingEmail: String = "",
    val locale: String = "es-AR",
    val market: String = "es-AR",
    val country: String = "AR",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastRunEpochMs: Long? = null,
    val lastKnownPoints: Int = 0,
    val streakDays: Int = 0,
    // Per-account settings
    val searchCount: Int = 20,
    val searchDelayMinSec: Int = 8,
    val searchDelayMaxSec: Int = 16,
    val farmSearch: Boolean = true,
    val farmDailySet: Boolean = true,
    val farmMoreActivities: Boolean = true,
    val farmReadToEarn: Boolean = true,
    val farmIntervalos: Boolean = true,
)
