package com.zero.rewards.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.zero.rewards.core.automation.AutomationHost
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.log.FarmerLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Accessibility service that implements [AutomationHost].
 *
 * Users enable it once in:  Settings → Accessibility → Downloaded apps →
 * Zero Rewards → Enable. After that it binds persistently and runners can
 * drive the Bing app's UI through it.
 */
class ZeroAccessibilityService : AccessibilityService(), AutomationHost {

    @Volatile private var currentPkg: String? = null
    private val nodeCache = ConcurrentHashMap<Long, AccessibilityNodeInfo>()
    private val handleSeq = AtomicLong(1)

    override val isAvailable: Boolean get() = instance === this
    override val screenWidth: Int get() = displayMetrics.widthPixels
    override val screenHeight: Int get() = displayMetrics.heightPixels

    private val displayMetrics: DisplayMetrics
        get() {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
            return m
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        FarmerLog.i("accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        nodeCache.clear()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPkg = event.packageName?.toString()
        }
    }

    // ----- AutomationHost -----

    override suspend fun currentPackage(): String? = currentPkg ?: rootInActiveWindow?.packageName?.toString()

    override suspend fun launchApp(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            startActivity(intent); true
        } catch (t: Throwable) {
            FarmerLog.e("launchApp $packageName failed", t); false
        }
    }

    override suspend fun waitForPackage(packageName: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (currentPackage() == packageName) return true
            delay(200)
        }
        return false
    }

    override suspend fun findByText(substr: String, timeoutMs: Long): AutomationNode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val needle = substr.lowercase()
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val found = walk(root) { matchesText(it, needle) }
                if (found != null) return snapshot(found)
            }
            delay(250)
        }
        return null
    }

    override suspend fun findAllByText(substr: String): List<AutomationNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val needle = substr.lowercase()
        val out = mutableListOf<AutomationNode>()
        walkAll(root) { node ->
            if (matchesText(node, needle)) out += snapshot(node)
        }
        return out
    }

    /**
     * Case-insensitive substring match across text, contentDescription, AND
     * hintText (placeholder). Bing's search bar exposes "Buscar" as a hint
     * when empty, not as text.
     */
    private fun matchesText(node: AccessibilityNodeInfo, needle: String): Boolean {
        val t = node.text?.toString()?.lowercase()
        if (t?.contains(needle) == true) return true
        val d = node.contentDescription?.toString()?.lowercase()
        if (d?.contains(needle) == true) return true
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val h = node.hintText?.toString()?.lowercase()
            if (h?.contains(needle) == true) return true
        }
        return false
    }

    override suspend fun findByViewId(viewId: String, timeoutMs: Long): AutomationNode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val list = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!list.isNullOrEmpty()) return snapshot(list.first())
            }
            delay(250)
        }
        return null
    }

    override suspend fun findByClass(className: String, textSubstr: String?): List<AutomationNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val needle = textSubstr?.lowercase()
        val out = mutableListOf<AutomationNode>()
        walkAll(root) { node ->
            val cls = node.className?.toString()
            if (cls == className || cls?.endsWith(".$className") == true) {
                val t = node.text?.toString()?.lowercase()
                val d = node.contentDescription?.toString()?.lowercase()
                if (needle == null || (t?.contains(needle) == true) || (d?.contains(needle) == true)) {
                    out += snapshot(node)
                }
            }
        }
        return out
    }

    override suspend fun findByContentDesc(substr: String, timeoutMs: Long): AutomationNode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val needle = substr.lowercase()
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val found = walk(root) {
                    it.contentDescription?.toString()?.lowercase()?.contains(needle) == true
                }
                if (found != null) return snapshot(found)
            }
            delay(250)
        }
        return null
    }

    override suspend fun tap(node: AutomationNode): Boolean {
        val live = nodeCache[node.handle]
        if (live != null) {
            // Walk upward until we find a clickable ancestor.
            var cur: AccessibilityNodeInfo? = live
            while (cur != null) {
                if (cur.isClickable) {
                    if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    break
                }
                cur = cur.parent
            }
        }
        return tapCoord(node.centerX.toFloat(), node.centerY.toFloat())
    }

    override suspend fun tapCoord(x: Float, y: Float): Boolean {
        val maxX = (screenWidth - 1).coerceAtLeast(1).toFloat()
        val maxY = (screenHeight - 1).coerceAtLeast(1).toFloat()
        val cx = x.coerceIn(0f, maxX)
        val cy = y.coerceIn(0f, maxY)
        // Human-like tap: ~110ms duration with a 2px micro-movement. Touch
        // handlers in RecyclerView rows (seen on the Cuentas / Switch Account
        // screen) use a GestureDetector that filters out zero-length <80ms
        // taps as spurious input — a stationary 50ms gesture got accepted
        // but never fired the click. Mimicking a real finger jitter gets
        // through.
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo((cx + 2f).coerceAtMost(maxX), (cy + 2f).coerceAtMost(maxY))
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 110)
        return dispatchGestureSync(GestureDescription.Builder().addStroke(stroke).build())
    }

    override suspend fun setText(node: AutomationNode, text: String): Boolean {
        val live = nodeCache[node.handle] ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return live.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun pressImeAction(node: AutomationNode): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 30) return false
        val live = nodeCache[node.handle] ?: return false
        return live.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
        )
    }

    override suspend fun swipe(
        startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long,
    ): Boolean {
        // Clamp to the on-screen range; GestureDescription rejects any Path
        // whose computed bounds go negative and crashes with
        // "Path bounds must not be negative". Callers that compute offsets
        // from screenHeight can easily produce slightly out-of-range coords.
        val maxX = (screenWidth - 1).coerceAtLeast(1).toFloat()
        val maxY = (screenHeight - 1).coerceAtLeast(1).toFloat()
        val sx = startX.coerceIn(0f, maxX)
        val sy = startY.coerceIn(0f, maxY)
        val ex = endX.coerceIn(0f, maxX)
        val ey = endY.coerceIn(0f, maxY)
        val dur = durationMs.coerceIn(10L, 10_000L)
        val path = Path().apply {
            moveTo(sx, sy)
            if (sx == ex && sy == ey) lineTo(ex + 1f, ey) else lineTo(ex, ey)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, dur)
        return dispatchGestureSync(GestureDescription.Builder().addStroke(stroke).build())
    }

    override suspend fun scrollBy(dx: Float, dy: Float): Boolean {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        return swipe(cx, cy, cx - dx, cy - dy, 350L)
    }

    override suspend fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    override suspend fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    override suspend fun atLauncher(): Boolean {
        val pkg = currentPackage() ?: return false
        return pkg.contains("launcher", ignoreCase = true) ||
            pkg == "com.google.android.apps.nexuslauncher" ||
            pkg == "com.sec.android.app.launcher"
    }

    override suspend fun dumpTree(maxDepth: Int): String {
        val root = rootInActiveWindow ?: return "<no root>"
        val sb = StringBuilder()
        fun rec(n: AccessibilityNodeInfo?, depth: Int) {
            n ?: return
            if (depth > maxDepth) return
            sb.append(" ".repeat(depth * 2))
            sb.append(n.className?.toString()?.substringAfterLast('.') ?: "?")
            n.viewIdResourceName?.let { sb.append("#").append(it.substringAfterLast('/')) }
            n.text?.let { sb.append(" \"").append(it.toString().take(30)).append("\"") }
            n.contentDescription?.let { sb.append(" [").append(it.toString().take(30)).append("]") }
            sb.append("\n")
            for (i in 0 until n.childCount) rec(n.getChild(i), depth + 1)
        }
        rec(root, 0)
        return sb.toString()
    }

    // ----- helpers -----

    private fun snapshot(node: AccessibilityNodeInfo): AutomationNode {
        val b = Rect().also { node.getBoundsInScreen(it) }
        val handle = handleSeq.getAndIncrement()
        nodeCache[handle] = node
        // Keep the cache bounded — drop old handles.
        if (nodeCache.size > 512) {
            val keys = nodeCache.keys.sorted().take(nodeCache.size - 256)
            keys.forEach { nodeCache.remove(it) }
        }
        return AutomationNode(
            handle = handle,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            boundsL = b.left, boundsT = b.top, boundsR = b.right, boundsB = b.bottom,
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            scrollable = node.isScrollable,
            focusable = node.isFocusable,
            editable = node.isEditable,
            enabled = node.isEnabled,
            visible = node.isVisibleToUser,
        )
    }

    private inline fun walk(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (predicate(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        return null
    }

    private inline fun walkAll(
        root: AccessibilityNodeInfo,
        visit: (AccessibilityNodeInfo) -> Unit,
    ) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            visit(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
    }

    private suspend fun dispatchGestureSync(gesture: GestureDescription): Boolean {
        val done = CompletableDeferred<Boolean>()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { done.complete(true) }
            override fun onCancelled(g: GestureDescription?) { done.complete(false) }
        }, null)
        if (!ok) return false
        return done.await()
    }

    companion object {
        @Volatile private var instance: ZeroAccessibilityService? = null
        fun get(): ZeroAccessibilityService? = instance
    }
}
