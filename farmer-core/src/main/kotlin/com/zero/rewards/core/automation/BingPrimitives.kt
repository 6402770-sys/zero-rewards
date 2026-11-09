package com.zero.rewards.core.automation

import com.zero.rewards.core.log.FarmerLog
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Higher-level helpers over [AutomationHost] that are specific to the
 * Bing Android app. Runners use these instead of hitting the host directly
 * so the fallback / hint-cascade logic lives in one place.
 */
class BingPrimitives(val host: AutomationHost) {

    suspend fun ensureBingForeground(timeoutMs: Long = 20_000L): Boolean {
        if (host.currentPackage() != BingPackage.PACKAGE) {
            if (!host.launchApp(BingPackage.PACKAGE)) return false
            if (!host.waitForPackage(BingPackage.PACKAGE, timeoutMs)) return false
        }
        if (waitForRoot(timeoutMs = 6_000L)) return true

        // Root still null even though Bing is supposedly foregrounded.
        // This happens when Bing got minimized / an overlay sits on top /
        // the a11y service hasn't received a WINDOW_STATE_CHANGED event
        // recently. Force a relaunch to kick the window state event loop.
        FarmerLog.d("ensureBingForeground: no root; forcing relaunch to kick a11y events")
        if (!host.launchApp(BingPackage.PACKAGE)) return false
        if (!host.waitForPackage(BingPackage.PACKAGE, 10_000L)) return false
        if (waitForRoot(timeoutMs = 8_000L)) return true

        // One more try with a longer settle delay — handles cold-start cases.
        FarmerLog.d("ensureBingForeground: still no root; cold-start retry")
        delay(2_000)
        host.launchApp(BingPackage.PACKAGE)
        host.waitForPackage(BingPackage.PACKAGE, 10_000L)
        return waitForRoot(timeoutMs = 10_000L)
    }

    /** Spin until the accessibility tree has a root with children, or timeout. */
    suspend fun waitForRoot(timeoutMs: Long = 8_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val t = host.dumpTree(1)
            if (t.isNotBlank() && !t.startsWith("<no root>")) return true
            delay(400)
        }
        FarmerLog.w("waitForRoot: no accessibility root after ${timeoutMs}ms (currentPkg=${host.currentPackage()})")
        return false
    }

    /** Find the first node matching any of the [hints] (text or contentDesc). */
    suspend fun findByAnyHint(hints: List<String>, timeoutMs: Long = 6_000L): AutomationNode? {
        val perHint = (timeoutMs / hints.size).coerceAtLeast(400L)
        for (h in hints) {
            val n = host.findByText(h, perHint)
            if (n != null) return n
        }
        return null
    }

    /** Find all nodes matching any of the [hints]. */
    suspend fun findAllByAnyHint(hints: List<String>): List<AutomationNode> {
        val out = mutableListOf<AutomationNode>()
        val seen = mutableSetOf<Long>()
        for (h in hints) {
            for (n in host.findAllByText(h)) {
                if (seen.add(n.handle)) out += n
            }
        }
        return out
    }

    /** Jittered sleep. */
    suspend fun idle(minMs: Long, maxMs: Long) = delay(Random.nextLong(minMs, maxMs))

    /** Standard inter-action pacing. */
    suspend fun breathe() = idle(300L, 700L)

    /** Scroll down the current screen by ~60% of the viewport, with inertia. */
    suspend fun scrollDown(fraction: Float = 0.6f): Boolean {
        val cx = host.screenWidth / 2f
        val h = host.screenHeight.toFloat()
        // Start at 80% down, end bounded above 10%. End must stay on-screen —
        // the host clamps too, but we keep the intended travel predictable.
        val startY = h * 0.80f
        val endY = (startY - h * fraction.coerceIn(0.05f, 0.75f)).coerceAtLeast(h * 0.05f)
        return host.swipe(cx, startY, cx, endY, Random.nextLong(220L, 380L))
    }

    /** Scroll up the current screen. */
    suspend fun scrollUp(fraction: Float = 0.6f): Boolean {
        val cx = host.screenWidth / 2f
        val h = host.screenHeight.toFloat()
        val startY = h * 0.20f
        val endY = (startY + h * fraction.coerceIn(0.05f, 0.75f)).coerceAtMost(h * 0.95f)
        return host.swipe(cx, startY, cx, endY, Random.nextLong(220L, 380L))
    }

    /**
     * Scroll down until [predicate] yields non-null or the page stops
     * producing new content for [staleLimit] consecutive scrolls.
     * Returns the predicate's hit, or null if exhausted.
     */
    suspend fun <T : Any> scrollUntil(
        maxScrolls: Int = 12,
        fraction: Float = 0.55f,
        staleLimit: Int = 2,
        predicate: suspend () -> T?,
    ): T? {
        predicate()?.let { return it }
        var lastSig = pageSignature()
        var stale = 0
        repeat(maxScrolls) {
            scrollDown(fraction)
            idle(300, 550)
            predicate()?.let { return it }
            val sig = pageSignature()
            if (sig == lastSig) {
                stale++
                if (stale >= staleLimit) return null
            } else {
                stale = 0
                lastSig = sig
            }
        }
        return null
    }

    /** Hash of the top visible TextViews — used to detect "no scroll progress". */
    suspend fun pageSignature(): Int {
        val tv = host.findByClass("android.widget.TextView")
        return tv.asSequence()
            .filter { it.visible }
            .take(24)
            .joinToString("|") { "${it.text ?: ""}@${it.boundsT}" }
            .hashCode()
    }

    /**
     * Return to Bing home.
     *
     * Strategy (in order): if we look like we're already on home, done. Else
     * tap the "Inicio" button in the bottom nav (image 1 shows this on the
     * current build). Else `launchApp` as the final authoritative move.
     *
     * We never press BACK as a return path — BACK from Bing home closes the
     * app, and BACK from a sub-screen whose home-hint we couldn't match would
     * eventually land on home and the next BACK would exit. The Inicio tab
     * is the correct Bing-visible navigation gesture.
     *
     * Returns true when Bing is the foreground package by the end.
     */
    suspend fun backToBingHome(@Suppress("UNUSED_PARAMETER") maxBacks: Int = 5): Boolean {
        if (looksLikeBingHome()) return true

        // Retry the Inicio search a few times — the a11y tree lags window
        // transitions and the bottom nav may not be rendered yet on the first
        // poll. Previous builds fell through to launchApp before Inicio was
        // ready, which brought Bing back on the same screen we tried to
        // escape from (no help).
        repeat(4) {
            if (host.currentPackage() == BingPackage.PACKAGE) {
                val inicio = findInicioTab()
                if (inicio != null) {
                    FarmerLog.d("backToBingHome: tap Inicio ${inicio.describe()}")
                    host.tap(inicio); delay(1_200)
                    if (looksLikeBingHome()) return true
                }
            }
            delay(600)
        }

        // Authoritative: relaunch Bing's main activity, then try Inicio again
        // on the fresh view.
        host.launchApp(BingPackage.PACKAGE)
        host.waitForPackage(BingPackage.PACKAGE, 8_000L)
        waitForRoot(6_000L)
        if (looksLikeBingHome()) return true

        val inicio = findInicioTab()
        if (inicio != null) {
            FarmerLog.d("backToBingHome: tap Inicio post-launch ${inicio.describe()}")
            host.tap(inicio); delay(1_200)
            if (looksLikeBingHome()) return true
        }

        FarmerLog.d("backToBingHome: no home-hint after launch; tree=\n${host.dumpTree(5)}")
        return host.currentPackage() == BingPackage.PACKAGE
    }

    private suspend fun findInicioTab(): com.zero.rewards.core.automation.AutomationNode? {
        return host.findByContentDesc("Inicio", 1_000L)
            ?: host.findByContentDesc("Home", 800L)
            ?: host.findByText("Inicio", 600L)
            ?: host.findByText("Home", 600L)
    }

    /**
     * True when we're on Bing HOME specifically — not a sub-screen that
     * happens to show the bottom nav. Keeps per-call cost low (<2s) with
     * short per-check timeouts; this is polled from waitBingHome up to
     * dozens of times.
     */
    suspend fun looksLikeBingHome(): Boolean {
        if (host.currentPackage() != BingPackage.PACKAGE) return false
        // Fast positive: the home search placeholder — only visible on home.
        if (host.findByText("Pregunta cualquier cosa", 300L) != null) return true
        if (host.findByText("Ask anything", 200L) != null) return true
        // Strong negatives: browser-chrome navigation icons appear on any
        // sub-screen (search results page swaps the bottom nav to show
        // Atrás/Reenviar instead of Cámara/Aplicaciones). Home has neither.
        if (host.findByContentDesc("Atrás", 150L) != null) return false
        if (host.findByContentDesc("Reenviar", 150L) != null) return false
        // Specific sub-screen text markers.
        if (host.findByText("Sesión iniciada como", 150L) != null) return false
        if (host.findByText("Cambiar a otra cuenta", 150L) != null) return false
        if (host.findByText("CONJUNTO DIARIO", 150L) != null) return false
        if (host.findByText("Intervalos diarios", 150L) != null) return false
        if (host.findByText("Puntos de hoy", 150L) != null) return false
        if (host.findByText("Cuentas", 150L) != null) return false
        // Fallback: bottom-nav Inicio visible with no sub-screen marker.
        val inicio = host.findByContentDesc("Inicio", 250L)
            ?: host.findByContentDesc("Home", 250L)
        return inicio != null
    }

    /** Diagnostic dump. */
    suspend fun log(label: String) {
        FarmerLog.d("$label @ pkg=${host.currentPackage()} tree:\n${host.dumpTree(6)}")
    }
}
