package com.zero.rewards.core.reminder

import java.util.UUID

/**
 * Domain model for a daily streak reminder. The scheduler translates
 * this into an AlarmManager alarm that fires every day at
 * [hourOfDay]:[minute] local time; when it fires, the receiver checks
 * which accounts still missed today's farm and posts a notification
 * naming them. [enabled] gates the alarm registration.
 */
data class StreakReminder(
    val id: String = UUID.randomUUID().toString(),
    val hourOfDay: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(hourOfDay in 0..23) { "hourOfDay must be 0..23, got $hourOfDay" }
        require(minute in 0..59) { "minute must be 0..59, got $minute" }
    }

    fun formatTime(): String = "%02d:%02d".format(hourOfDay, minute)
}
