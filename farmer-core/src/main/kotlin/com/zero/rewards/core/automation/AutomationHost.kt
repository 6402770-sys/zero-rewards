package com.zero.rewards.core.automation

/**
 * Abstract gesture + node-lookup surface for driving another app's UI.
 *
 * Implemented by the app-module AccessibilityService. Runners work against
 * this interface so farmer-core stays android-UI-free.
 *
 * All suspend methods are cancellable. A null [AutomationNode] return means
 * the node wasn't found within the timeout. A false boolean return means
 * the gesture was dispatched but the action didn't complete (e.g., tap on
 * an off-screen element, type without a focused editor).
 */
interface AutomationHost {

    /** True if the accessibility service is bound and ready to drive. */
    val isAvailable: Boolean

    /** Package name of the app currently in the foreground, or null. */
    suspend fun currentPackage(): String?

    /** Launch [packageName] via its default launcher intent. */
    suspend fun launchApp(packageName: String): Boolean

    /** Wait until the foreground package matches [packageName]. */
    suspend fun waitForPackage(packageName: String, timeoutMs: Long = 10_000L): Boolean

    /** Find the first node whose visible text contains [substr] (case-insensitive). */
    suspend fun findByText(substr: String, timeoutMs: Long = 5_000L): AutomationNode?

    /** Find every node whose visible text contains [substr]. Empty list if none. */
    suspend fun findAllByText(substr: String): List<AutomationNode>

    /** Find a node by its exact resource-id. */
    suspend fun findByViewId(viewId: String, timeoutMs: Long = 5_000L): AutomationNode?

    /** Find nodes by className (optionally containing [textSubstr]). */
    suspend fun findByClass(className: String, textSubstr: String? = null): List<AutomationNode>

    /** Find a node by its contentDescription (case-insensitive substring). */
    suspend fun findByContentDesc(substr: String, timeoutMs: Long = 5_000L): AutomationNode?

    /** Tap the node. Prefers ACTION_CLICK; falls back to a tap gesture at its center. */
    suspend fun tap(node: AutomationNode): Boolean

    /** Tap the given screen coordinate directly. */
    suspend fun tapCoord(x: Float, y: Float): Boolean

    /** Set text on the node if editable. Returns true on success. */
    suspend fun setText(node: AutomationNode, text: String): Boolean

    /**
     * Fire the IME "enter" action on an editable node. For a search box
     * configured with imeOptions="actionSearch", this triggers the search.
     * Requires API 30+; returns false on older devices.
     */
    suspend fun pressImeAction(node: AutomationNode): Boolean

    /** Dispatch a swipe gesture between two screen points. */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 400L,
    ): Boolean

    /** Scroll the window. Positive dy swipes UP (content moves up). */
    suspend fun scrollBy(dx: Float, dy: Float): Boolean

    /** Global BACK action. */
    suspend fun back(): Boolean

    /** Global HOME action. */
    suspend fun home(): Boolean

    /** Returns true if the current package is the launcher. */
    suspend fun atLauncher(): Boolean

    /** Dump a short string of the visible UI tree. Used for diagnostics. */
    suspend fun dumpTree(maxDepth: Int = 8): String

    /** Screen width in pixels. */
    val screenWidth: Int

    /** Screen height in pixels. */
    val screenHeight: Int
}
