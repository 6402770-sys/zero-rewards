package com.zero.rewards.core.runner

import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.BingPackage
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog
import com.zero.rewards.core.search.TermPool
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Bing app search runner.
 *
 * Per query:
 *   1. Ensure Bing app is foregrounded, back to home if necessary
 *   2. Find and tap the search bar
 *   3. Find the focused EditText, type the query
 *   4. Fire IME ACTION_SEARCH (API 30+). Fallback: tap a visible search/submit
 *      button (magnifier icon, "Buscar" contentDesc, etc.)
 *   5. Wait briefly for the results page, back to home
 *
 * Does NOT try to detect results via UI text — Bing's results tabs vary too
 * much. Instead, we check that enough time passed AND the Bing app is still
 * foregrounded, which is a reliable proxy.
 */
class BingSearchRunner(
    private val primitives: BingPrimitives,
    private val pool: TermPool,
) {

    suspend fun run(account: Account, targetCount: Int): TaskResult {
        if (!primitives.ensureBingForeground()) {
            return TaskResult.Failed("Bing app did not foreground")
        }
        primitives.idle(1_500, 3_000)
        // Make sure we start at the Bing home screen, not on a leftover
        // Cuentas / Rewards / results page from a previous phase.
        primitives.backToBingHome(maxBacks = 5)
        primitives.idle(800, 1_400)

        val terms = pool.take(targetCount)
        if (terms.isEmpty()) return TaskResult.Skipped("empty term pool")

        val minMs = (account.searchDelayMinSec.coerceAtLeast(1)) * 1000L
        val maxMs = (account.searchDelayMaxSec.coerceAtLeast(account.searchDelayMinSec + 1)) * 1000L
        var done = 0
        for ((i, term) in terms.withIndex()) {
            FarmerLog.d("[${account.label}] search ${i + 1}/${terms.size} → $term")
            if (performOne(term)) done++ else FarmerLog.w("[${account.label}] search miss: $term")
            primitives.idle(minMs, maxMs)
        }
        return if (done == 0) TaskResult.Failed("no searches landed")
        else TaskResult.Ok(pointsClaimed = done * 2, detail = "$done/${terms.size} searches")
    }

    private suspend fun performOne(term: String): Boolean {
        // Step 1: locate the search bar or a search-icon entry point.
        var bar = primitives.findByAnyHint(BingPackage.SEARCH_HINTS, 4_000L)
        if (bar == null) {
            primitives.scrollUp(0.9f); primitives.idle(600, 1_200)
            bar = primitives.findByAnyHint(BingPackage.SEARCH_HINTS, 3_000L)
        }
        // Also try common search-icon contentDescriptions used on home screens
        if (bar == null) {
            bar = primitives.host.findByContentDesc("Buscar en Bing", 1_500L)
                ?: primitives.host.findByContentDesc("Search Bing", 1_000L)
                ?: primitives.host.findByContentDesc("Barra de búsqueda", 1_000L)
                ?: primitives.host.findByContentDesc("Search bar", 1_000L)
                ?: primitives.host.findByContentDesc("Search", 1_000L)
                ?: primitives.host.findByContentDesc("Buscar", 1_000L)
        }
        if (bar == null) {
            FarmerLog.w("search: bar not found — dumping Bing home tree:")
            FarmerLog.d(primitives.host.dumpTree(7))
            return false
        }
        FarmerLog.d("  found search bar: ${bar.describe()}")

        if (!primitives.host.tap(bar)) {
            FarmerLog.w("search: tap on bar failed"); return false
        }
        primitives.idle(900, 1_600)

        // Step 2: find the editable field.
        val editors = primitives.host.findByClass("android.widget.EditText")
        val editor = editors.firstOrNull { it.editable }
            ?: editors.firstOrNull { it.focusable && it.visible }
            ?: editors.firstOrNull()
        if (editor == null) {
            FarmerLog.w("search: no EditText found after tapping bar (dumping tree)")
            FarmerLog.d(primitives.host.dumpTree(6))
            return false
        }
        FarmerLog.d("  found editor: ${editor.describe()}")

        // Clear any existing text, then type.
        primitives.host.setText(editor, "")
        primitives.idle(200, 400)
        if (!primitives.host.setText(editor, term)) {
            FarmerLog.w("search: setText failed")
            return false
        }
        primitives.idle(600, 1_200)

        // Step 3: submit. Try IME enter first (API 30+), then physical taps.
        val imeOk = primitives.host.pressImeAction(editor)
        FarmerLog.d("  imeAction=$imeOk")
        if (!imeOk) {
            if (!trySubmitByUi(term)) {
                FarmerLog.w("search: submit UI not found and IME action failed")
                return false
            }
        }

        // Step 4: wait for results page. We don't need to see specific tabs —
        // just that the Bing app is still foregrounded and some time passed.
        delay(Random.nextLong(3_500L, 6_000L))
        val stillInBing = primitives.host.currentPackage() == BingPackage.PACKAGE
        if (!stillInBing) {
            FarmerLog.w("search: Bing lost foreground after submit")
            return false
        }

        // Step 5: back to home — usually one BACK press returns to the Bing homepage.
        primitives.backToBingHome(maxBacks = 3)
        primitives.idle(800, 1_400)
        return true
    }

    /** Try various physical submit buttons (icon buttons, menu entries). */
    private suspend fun trySubmitByUi(term: String): Boolean {
        // Magnifying-glass icon button near the editor
        val submit = primitives.host.findByContentDesc("Buscar", 1_000L)
            ?: primitives.host.findByContentDesc("Search", 1_000L)
            ?: primitives.host.findByContentDesc("Ir", 500L)
            ?: primitives.host.findByContentDesc("Go", 500L)
        if (submit != null) {
            primitives.host.tap(submit); return true
        }
        // Tap the first suggestion row that matches our term
        val suggestions = primitives.host.findAllByText(term)
            .filter { it.clickable && it.visible }
        if (suggestions.isNotEmpty()) {
            primitives.host.tap(suggestions.first())
            return true
        }
        return false
    }
}
