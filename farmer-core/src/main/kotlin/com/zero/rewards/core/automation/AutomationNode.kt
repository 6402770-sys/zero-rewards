package com.zero.rewards.core.automation

/**
 * A snapshot of an accessibility node. The [handle] is opaque — the host
 * resolves it back to a live `AccessibilityNodeInfo` when you call tap/scroll
 * on it. Don't hold onto a node across more than a few ticks; nodes go stale
 * when the UI tree changes.
 */
data class AutomationNode(
    val handle: Long,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val packageName: String?,
    val boundsL: Int,
    val boundsT: Int,
    val boundsR: Int,
    val boundsB: Int,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val focusable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
) {
    val centerX: Int get() = (boundsL + boundsR) / 2
    val centerY: Int get() = (boundsT + boundsB) / 2
    val width: Int get() = boundsR - boundsL
    val height: Int get() = boundsB - boundsT

    fun describe(): String = buildString {
        append(className?.substringAfterLast('.') ?: "Node")
        viewId?.let { append("#").append(it.substringAfterLast('/')) }
        text?.let { append(" \"").append(it.take(40)).append("\"") }
        contentDescription?.takeIf { it != text }?.let { append(" [").append(it.take(40)).append("]") }
        append(" @(").append(centerX).append(",").append(centerY).append(")")
    }
}
