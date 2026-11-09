package com.zero.rewards.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide signal that a streak reminder notification was tapped
 * (or an intent with EXTRA_STREAK_PROMPT was delivered). The UI root
 * observes [requestVersion] and shows the Sí/No dialog whenever the
 * version increments. Using a version counter instead of a boolean so
 * the same prompt can fire again if dismissed and re-triggered.
 */
object StreakPromptBus {
    private val _requestVersion = MutableStateFlow(0)
    val requestVersion: StateFlow<Int> = _requestVersion

    fun request() { _requestVersion.value = _requestVersion.value + 1 }

    const val EXTRA_STREAK_PROMPT = "streak_prompt"
}
