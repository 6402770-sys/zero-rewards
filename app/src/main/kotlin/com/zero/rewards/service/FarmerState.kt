package com.zero.rewards.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-global observable for the currently running account.
 * Dashboard observes this to show a Stop button while a farm is in progress.
 */
object FarmerState {
    private val _runningAccountId = MutableStateFlow<String?>(null)
    val runningAccountId: StateFlow<String?> = _runningAccountId.asStateFlow()

    private val _phase = MutableStateFlow<String?>(null)
    val phase: StateFlow<String?> = _phase.asStateFlow()

    fun setRunning(accountId: String?) { _runningAccountId.value = accountId }
    fun setPhase(phase: String?) { _phase.value = phase }
}
