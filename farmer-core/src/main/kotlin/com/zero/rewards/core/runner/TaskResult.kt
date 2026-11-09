package com.zero.rewards.core.runner

sealed class TaskResult {
    data class Ok(val pointsClaimed: Int, val detail: String? = null) : TaskResult()
    data class Skipped(val reason: String) : TaskResult()
    data class Failed(val reason: String, val cause: Throwable? = null) : TaskResult()
}
