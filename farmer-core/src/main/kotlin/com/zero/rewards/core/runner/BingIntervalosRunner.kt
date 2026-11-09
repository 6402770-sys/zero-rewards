package com.zero.rewards.core.runner

import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog

/**
 * "Intervalos diarios" / Daily streak runner.
 *
 * Structure observed on current Bing build (screen 1080x2460):
 *   - "Intervalos diarios" header TextView at y≈1080..1135.
 *   - Coin wrappers immediately below, at y≈1157: seven Views of
 *     size ~127x88 at x={70, 205, 340, 475, 610, 747, 882}.
 *   - Each coin has nested inner Views (83x82) and, when claimable, a
 *     "+N" badge TextView at y≈1177 ("10", "15", "50").
 *   - Day N labels (TextViews) at y≈1252.
 *   - Under the intervalos row lives a daily-set tile "Buscar 1 vez"
 *     at y≈1377 which has its OWN visually-identical 7-day streak
 *     widget at y≈1455 with its own "Day N" labels. That is NOT the
 *     intervalos row — hence anchoring on the "Intervalos diarios"
 *     header and bounding the coin band strictly below it.
 *
 * The coin wrappers are not marked clickable; their touch handler lives
 * deeper in the view hierarchy and accepts a real gesture, so we tapCoord
 * the wrapper center (never host.tap, same lesson as elsewhere).
 */
class BingIntervalosRunner(private val primitives: BingPrimitives) {

    private val panel = BingRewardsPanel(primitives)

    suspend fun run(account: Account): TaskResult {
        if (!panel.openPanel()) return TaskResult.Failed("rewards panel didn't open")
        primitives.idle(600, 1_000)

        FarmerLog.d("[${account.label}] intervalos: scroll up very up to reach top")
        repeat(8) { primitives.scrollUp(0.75f); primitives.idle(180, 320) }
        primitives.idle(600, 1_000)

        val tappedBuckets = mutableSetOf<Int>()
        var tapped = 0
        var missStreak = 0

        // Max 10 iterations to cover 7 coins + retries after view jitter.
        for (iter in 0 until 10) {
            val coins = findIntervalosCoins()
            if (coins.isEmpty()) {
                missStreak++
                FarmerLog.d("[${account.label}] intervalos iter $iter: no coins visible (miss #$missStreak)")
                if (missStreak >= 2) break
                // Header lost — try gentle scroll up to recover.
                primitives.scrollUp(0.4f); primitives.idle(400, 700)
                continue
            }
            missStreak = 0

            val next = coins.firstOrNull { bucketOf(it) !in tappedBuckets }
            if (next == null) {
                FarmerLog.d("[${account.label}] intervalos: all ${coins.size} coins already tapped")
                break
            }
            tappedBuckets += bucketOf(next)

            val cx = next.centerX.toFloat()
            val cy = next.centerY.toFloat()
            FarmerLog.d("[${account.label}] intervalos tap #${tapped + 1}: ($cx, $cy) size=${next.width}x${next.height} bucket=${bucketOf(next)}")
            primitives.host.tapCoord(cx, cy)
            tapped++
            primitives.idle(1_800, 2_600)

            dismissAwardPopup()

            // If the tap opened a details screen, back out and recover the hub.
            if (!panel.isHubVisible(500L)) {
                FarmerLog.d("[${account.label}] intervalos: details opened; back")
                primitives.host.back()
                primitives.idle(600, 1_000)
                if (primitives.host.findByText("Intervalos diarios", 500L) == null) {
                    repeat(3) { primitives.scrollUp(0.5f); primitives.idle(200, 400) }
                }
            }
        }

        FarmerLog.i("[${account.label}] intervalos: tapped $tapped coins")
        primitives.backToBingHome()
        return TaskResult.Ok(pointsClaimed = 0, detail = "$tapped coins tapped")
    }

    /**
     * Find the 7 intervalos coin wrappers anchored on the
     * "Intervalos diarios" header. Returns them sorted left-to-right.
     *
     * Anchor: header TextView ("Intervalos diarios" / "Daily streak").
     * Coin band: [headerB + 5, headerB + 140] — covers the ~88px tall
     * coin row that sits immediately below the header. This band is
     * strictly above the Day N labels (y=headerB+117) but excludes the
     * second "Buscar 1 vez" pseudo-row 300px further down.
     *
     * Shape: Views (or ViewGroup / FrameLayout), width 100..160,
     * height 70..110. Nested 83x82 inner decoration Views fall outside
     * (width < 100) and the pseudo-row's Views at y>1400 are out of band.
     *
     * Dedup: group by X bucket (every 100px) and keep the largest by
     * area per group — picks the outer wrapper over nested inner Views
     * at the same X position.
     */
    private suspend fun findIntervalosCoins(): List<AutomationNode> {
        val header = primitives.host.findByText("Intervalos diarios", 500L)
            ?: primitives.host.findByText("Daily streak", 300L)
        if (header == null) {
            FarmerLog.d("intervalos: header 'Intervalos diarios' not found")
            return emptyList()
        }
        val bandTop = header.boundsB + 5
        val bandBot = header.boundsB + 140

        val classes = listOf(
            "android.view.View",
            "android.view.ViewGroup",
            "android.widget.FrameLayout",
        )
        val all = buildList { for (c in classes) addAll(primitives.host.findByClass(c)) }
            .distinctBy { it.handle }

        val candidates = all.filter { n ->
            n.visible &&
                n.boundsT in bandTop..bandBot &&
                n.width in 100..160 &&
                n.height in 70..110
        }
        if (candidates.isEmpty()) {
            FarmerLog.d("intervalos: header @y=${header.boundsT}..${header.boundsB} but no coin-shaped nodes in band [$bandTop..$bandBot]")
            return emptyList()
        }

        val coins = candidates.groupBy { bucketOf(it) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.width * it.height } }
            .sortedBy { it.boundsL }

        FarmerLog.d("intervalos: header@y=${header.boundsT} band=[$bandTop..$bandBot] -> ${coins.size} coins at x=${coins.map { it.boundsL }}")
        return coins
    }

    /** X bucket: coins sit 135px apart, so a 100px bucket keeps them separate. */
    private fun bucketOf(n: AutomationNode): Int = (n.boundsL + 30) / 100

    /**
     * After a coin tap, Bing may show a "+5 puntos" award popup / "Siguiente"
     * card / "Listo" confirmation. Dismiss so the next coin tap lands on
     * the coin, not on the popup.
     */
    private suspend fun dismissAwardPopup() {
        val labels = listOf(
            "Listo", "Done", "OK", "Aceptar", "Accept",
            "Continuar", "Continue", "Cerrar", "Close",
            "Seguir", "Got it",
        )
        for (label in labels) {
            val btn = primitives.host.findByText(label, 150L) ?: continue
            if (!btn.visible) continue
            FarmerLog.d("intervalos: dismiss popup with '$label'")
            primitives.host.tapCoord(btn.centerX.toFloat(), btn.centerY.toFloat())
            primitives.idle(400, 800)
            return
        }
    }
}
