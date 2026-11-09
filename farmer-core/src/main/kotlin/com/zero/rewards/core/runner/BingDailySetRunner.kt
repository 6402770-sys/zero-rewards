package com.zero.rewards.core.runner

import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPackage
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog

/**
 * Daily-set runner.
 *
 * Structure observed on current Bing build (screen 1080x2460):
 *   - "CONJUNTO DIARIO" header TextView at y≈240..295.
 *   - Three tiles below, each rendered as a pair of clickable Views
 *     sharing the same contentDescription (outer wrapper + inner card):
 *       Tile 1 — y≈247, 880x148, desc "Próximos eventos cerca de mí, …"
 *       Tile 2 — y≈447/477, 940x213 outer + 880x153 inner,
 *                desc "Mercados de inicio festivo en Londres, …"
 *       Tile 3 — y≈682/712, 940x253 outer + 880x193 inner,
 *                desc "¿Cuento de Benigni?, …"
 *   - "Actividades diarias" header at y≈1035 is the next section boundary.
 *     Beyond it live "Buscar para ganar" and "Leer para ganar" tiles —
 *     DIFFERENT section (Actividades), do NOT tap them here.
 *
 * Strategy: anchor on CONJUNTO DIARIO, bound the tile band by the next
 * section header, find clickable wide Views with non-empty desc in the
 * band, dedup by desc prefix (collapses outer+inner pairs, keeps the
 * larger wrapper). Tap each → drive content → back → re-anchor → repeat.
 */
class BingDailySetRunner(private val primitives: BingPrimitives) {

    private val panel = BingRewardsPanel(primitives)

    suspend fun run(account: Account): TaskResult {
        if (!panel.openPanel()) return TaskResult.Failed("rewards panel didn't open")
        primitives.idle(600, 1_000)

        // Reset to top so the CONJUNTO DIARIO header lands near the top.
        FarmerLog.d("[${account.label}] daily-set: reset to top")
        repeat(6) { primitives.scrollUp(0.75f); primitives.idle(160, 280) }
        primitives.idle(500, 900)

        val visitedKeys = mutableSetOf<String>()
        var completed = 0
        var missStreak = 0

        for (iter in 0 until 10) {
            val tiles = findDailySetTiles()
            if (tiles.isEmpty()) {
                missStreak++
                FarmerLog.d("[${account.label}] daily-set iter $iter: no tiles in band (miss #$missStreak)")
                if (missStreak >= 2) break
                reAnchorOnHeader()
                continue
            }
            missStreak = 0

            val next = tiles.firstOrNull { descKey(it) !in visitedKeys }
            if (next == null) {
                FarmerLog.d("[${account.label}] daily-set: all ${tiles.size} visible tiles processed")
                break
            }
            val key = descKey(next)
            visitedKeys += key
            FarmerLog.d("[${account.label}] daily-set tile #${completed + 1}: '${key.take(40)}' @y=${next.boundsT} size=${next.width}x${next.height}")

            primitives.host.tapCoord(next.centerX.toFloat(), next.centerY.toFloat())
            primitives.idle(2_000, 3_000)

            if (!panel.isHubVisible(900L)) {
                driveTileContent()
                primitives.host.back()
                primitives.idle(1_500, 2_500)
                completed++
                // Re-anchor on CONJUNTO DIARIO so the next iter finds the band.
                if (!reAnchorOnHeader()) {
                    FarmerLog.w("[${account.label}] daily-set: lost header after tile #$completed")
                    break
                }
            } else {
                FarmerLog.w("[${account.label}] daily-set: tap didn't navigate for '${key.take(30)}' (claimed / locked)")
            }
        }

        FarmerLog.i("[${account.label}] daily-set: $completed tiles completed (visited=${visitedKeys.size})")
        primitives.backToBingHome()
        return if (completed == 0) TaskResult.Skipped("no daily-set tiles credited")
        else TaskResult.Ok(pointsClaimed = completed * 10, detail = "$completed tiles")
    }

    /**
     * Find the daily-set tile wrappers anchored on the "CONJUNTO DIARIO"
     * header, strictly bounded above the next section (Actividades
     * diarias / Más actividades / Puntos de hoy). Returns tiles
     * de-duplicated by contentDescription prefix, sorted top-to-bottom.
     */
    private suspend fun findDailySetTiles(): List<AutomationNode> {
        val header = primitives.host.findByText("CONJUNTO DIARIO", 500L)
            ?: primitives.host.findByText("Conjunto diario", 300L)
            ?: primitives.host.findByText("Daily set", 300L)
        if (header == null) {
            FarmerLog.d("daily-set: CONJUNTO DIARIO header not found")
            return emptyList()
        }
        val bandTop = header.boundsB + 5
        val bandBot = findNextSectionTop(header.boundsB)
            ?: (primitives.host.screenHeight * 0.9).toInt()

        return findTilesInBand(bandTop, bandBot).also {
            FarmerLog.d("daily-set: header@y=${header.boundsT} band=[$bandTop..$bandBot] -> ${it.size} tiles")
        }
    }

    /**
     * Returns the Y of the closest known section header below [afterY],
     * so the tile band never bleeds into a different section.
     */
    private suspend fun findNextSectionTop(afterY: Int): Int? {
        val hints = listOf(
            "Actividades diarias", "Daily activities",
            "MÁS ACTIVIDADES", "Más actividades", "More activities",
            "Puntos de hoy", "Today's points",
            "Canjea tus puntos",
        )
        var best: Int? = null
        for (h in hints) {
            val n = primitives.host.findByText(h, 150L) ?: continue
            if (!n.visible) continue
            if (n.boundsT <= afterY) continue
            if (best == null || n.boundsT < best) best = n.boundsT
        }
        return best
    }

    /**
     * Clickable wide Views with non-empty contentDescription in the band.
     * Dedup by desc prefix so outer+inner pairs collapse; keep the
     * widest-and-tallest wrapper per unique tile.
     */
    private suspend fun findTilesInBand(top: Int, bot: Int): List<AutomationNode> {
        val classes = listOf(
            "android.view.View",
            "android.view.ViewGroup",
            "android.widget.FrameLayout",
            "android.widget.LinearLayout",
            "android.widget.RelativeLayout",
        )
        val screenW = primitives.host.screenWidth
        val all = buildList { for (c in classes) addAll(primitives.host.findByClass(c)) }
            .distinctBy { it.handle }

        val candidates = all.filter { n ->
            n.visible && n.clickable &&
                n.boundsT in top..bot && n.boundsB in top..bot &&
                n.width > screenW * 0.5 &&
                n.height in 100..400 &&
                !n.contentDescription.isNullOrBlank()
        }
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .groupBy { descKey(it) }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.width * it.height } }
            .sortedBy { it.boundsT }
    }

    private fun descKey(n: AutomationNode): String =
        n.contentDescription?.toString()?.trim()?.take(50) ?: ""

    /**
     * After back from a tile, bring CONJUNTO DIARIO back into the top
     * third of the viewport so the next iteration's findDailySetTiles
     * sees the band clearly. Returns true when the header is visible.
     */
    private suspend fun reAnchorOnHeader(): Boolean {
        val screenH = primitives.host.screenHeight
        for (attempt in 0 until 8) {
            val h = primitives.host.findByText("CONJUNTO DIARIO", 500L)
                ?: primitives.host.findByText("Conjunto diario", 300L)
            if (h != null && h.visible) {
                when {
                    h.boundsT < screenH * 0.4 -> return true
                    h.boundsT > screenH * 0.6 -> primitives.scrollDown(0.3f)
                    else -> return true
                }
                primitives.idle(300, 500)
            } else {
                primitives.scrollUp(0.5f); primitives.idle(300, 500)
            }
        }
        return primitives.host.findByText("CONJUNTO DIARIO", 500L) != null
    }

    /**
     * Inside a tile: quiz / poll / article. Current Bing credits daily-set
     * tiles by entry alone (a short dwell is enough), but we still try the
     * action-button path for quiz tiles that give extra interaction credit.
     * Caps hard at ~6s so the pipeline doesn't stall.
     */
    private suspend fun driveTileContent(maxTicks: Int = 8): Boolean {
        val start = System.currentTimeMillis()
        val maxDurationMs = 6_000L
        val screenH = primitives.host.screenHeight
        val chromeTopY = (screenH * 0.85).toInt()

        val actionWords = listOf(
            "Iniciar cuestionario", "Start quiz",
            "Iniciar", "Start", "Comenzar", "Begin",
            "Siguiente", "Next", "Continuar", "Continue",
            "Votar", "Vote", "Enviar", "Submit",
            "Finalizar", "Finish", "Hecho", "Done",
        )
        var meaningful = 0
        var idleTicks = 0

        // Minimum dwell so the entry credit ticks server-side.
        primitives.idle(1_200, 1_800)

        var tick = 0
        while (tick < maxTicks) {
            tick++
            if (System.currentTimeMillis() - start > maxDurationMs) break
            if (primitives.findByAnyHint(BingPackage.REWARDS_HUB_HINTS, 300L) != null) break

            val btn = firstAction(actionWords, chromeTopY)
                ?: primitives.host.findByContentDesc("Opción", 250L)
                ?: primitives.host.findByContentDesc("Option", 200L)
            if (btn != null && btn.boundsT < chromeTopY) {
                FarmerLog.d("  daily: tap '${(btn.text ?: btn.contentDescription)?.take(24)}' @y=${btn.boundsT}")
                primitives.host.tapCoord(btn.centerX.toFloat(), btn.centerY.toFloat())
                meaningful++; idleTicks = 0
                primitives.idle(900, 1_500)
                continue
            }
            idleTicks++
            if (idleTicks >= 2) break
            primitives.idle(500, 900)
        }
        return meaningful > 0
    }

    /** Return the first node matching an exact action-word text, ignoring chrome. */
    private suspend fun firstAction(words: List<String>, chromeTopY: Int): AutomationNode? {
        for (w in words) {
            val candidates = primitives.host.findAllByText(w).filter { c ->
                c.visible && c.boundsT < chromeTopY &&
                    c.text?.toString()?.trim().equals(w, ignoreCase = true)
            }
            if (candidates.isNotEmpty()) return candidates.first()
        }
        return null
    }
}
