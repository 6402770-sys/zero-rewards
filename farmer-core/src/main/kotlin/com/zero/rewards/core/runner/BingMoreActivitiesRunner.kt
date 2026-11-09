package com.zero.rewards.core.runner

import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPackage
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog

/**
 * "Más actividades" / More activities runner.
 *
 * Structure observed on current Bing build (screen 1080x2460):
 *   - "MÁS ACTIVIDADES" header TextView at y≈1815 with a "N/M" badge
 *     TextView (e.g. "0/5") at y≈1820 on the right edge. IMPORTANT:
 *     that badge is POINTS earned / max points for this section, NOT
 *     a tile count — different tiles award different point values, so
 *     we read it only for logging context and never use it to size the
 *     loop. Tile count comes from what's actually visible + dedup.
 *   - Tiles below the header are clickable Views sharing their desc as
 *     outer+inner pairs, same shape as daily-set tiles:
 *       e.g. y≈1950/1980, 940x207 outer + 880x147 inner,
 *            desc "¿Conoces la respuesta?, …"
 *   - Only the first tile fits on the initial viewport; the rest are
 *     revealed by scrolling down. No section header follows MA, but the
 *     bottom nav (Inicio/Cámara/Copilot/Pestañas/Aplicaciones icons at
 *     y≈2200, 140x140 each) must never be tapped as a tile.
 *   - Above MA sits "Actividades diarias" (Buscar para ganar, Leer para
 *     ganar). Those are NOT more-activities tiles — the band top is
 *     strictly header.boundsB; the band bottom is unbounded below (no
 *     next section with clickable tiles), but every candidate still
 *     needs non-empty desc and width > 50% screen, which filters the
 *     bottom nav icons out.
 *
 * Strategy: scroll until MA header is near the top → find unvisited
 * tile in band → tap → drive content → back → next iteration finds
 * next unvisited (desc dedup) → when stale, scroll down to reveal more.
 * Loop terminates on consecutive stale rounds, never on a tile count.
 */
class BingMoreActivitiesRunner(private val primitives: BingPrimitives) {

    private val panel = BingRewardsPanel(primitives)

    suspend fun run(account: Account): TaskResult {
        if (!panel.openPanel()) return TaskResult.Failed("rewards panel didn't open")
        primitives.idle(600, 1_000)

        FarmerLog.d("[${account.label}] more-activities: reset to top")
        repeat(6) { primitives.scrollUp(0.75f); primitives.idle(160, 280) }
        primitives.idle(500, 900)

        val initialHeader = scrollUntilMaHeader()
        if (initialHeader == null) {
            FarmerLog.w("[${account.label}] more-activities: MÁS ACTIVIDADES header not found")
            primitives.backToBingHome()
            return TaskResult.Skipped("MÁS ACTIVIDADES header not found")
        }

        // Badge is POINTS, not tile count. Log it for context only — never
        // use it to bound the loop, since tiles have variable point values.
        val (pointsDone, pointsMax) = readProgressBadge(initialHeader)
        FarmerLog.i("[${account.label}] more-activities: points badge ${pointsDone ?: '?'}/${pointsMax ?: '?'} (POINTS, not tile count)")

        val visitedKeys = mutableSetOf<String>()
        var completed = 0
        var staleRounds = 0
        // Fixed generous cap — handles up to ~15 tiles with scroll steps
        // between each + occasional miss retries. Real termination comes
        // from staleRounds, not this ceiling.
        val maxIters = 40
        val screenH = primitives.host.screenHeight

        for (iter in 0 until maxIters) {
            if (staleRounds >= 5) {
                FarmerLog.d("[${account.label}] more-activities: $staleRounds stale rounds, assume done")
                break
            }

            // Recover if we ended up off the hub (e.g. back action over-shot).
            if (!panel.isHubVisible(400L)) {
                FarmerLog.d("[${account.label}] more-activities iter $iter: hub lost, reopen")
                if (!panel.openPanel()) break
                scrollUntilMaHeader() ?: break
                primitives.idle(300, 500)
            }

            // Prefer header-anchored band. When the header has scrolled OFF
            // the top of the viewport (we're deep in MA territory looking at
            // tiles 7-10 of 10), fall back to "any clickable wide tile in
            // the viewport" — safe because there's no section with
            // clickable tiles BELOW MA on current Bing (bottom nav icons
            // are filtered by width, Puntos de hoy is a static summary).
            val header = primitives.findByAnyHint(BingPackage.MORE_ACTIVITIES_HINTS, 500L)
                ?.takeIf { it.visible && it.boundsT > 0 }

            val tiles = if (header != null) {
                findMaTilesBelowHeader(header)
            } else {
                findTilesInBand(0, (screenH * 0.85).toInt())
            }

            val next = tiles.firstOrNull { descKey(it) !in visitedKeys }
            if (next == null) {
                staleRounds++
                FarmerLog.d("[${account.label}] more-activities iter $iter: no new tiles visible (stale #$staleRounds), scroll down")
                primitives.scrollDown(0.55f); primitives.idle(400, 700)
                continue
            }
            staleRounds = 0
            val key = descKey(next)
            visitedKeys += key
            FarmerLog.d("[${account.label}] more-activities tile #${completed + 1}: '${key.take(40)}' @y=${next.boundsT} size=${next.width}x${next.height} header_visible=${header != null}")

            primitives.host.tapCoord(next.centerX.toFloat(), next.centerY.toFloat())
            primitives.idle(2_000, 3_000)

            if (!panel.isHubVisible(900L)) {
                driveTileContent()
                primitives.host.back()
                primitives.idle(1_500, 2_500)
                completed++
                // Deliberately DON'T re-anchor on MA header here. Bing
                // restores the scroll position on back, so the next iter
                // sees the same tiles — dedup skips the one we just
                // tapped, and if the next unvisited sits past the band
                // the stale-scroll loop above walks us down the list.
            } else {
                FarmerLog.w("[${account.label}] more-activities: tap didn't navigate for '${key.take(30)}' (claimed / locked)")
            }
        }

        // Post-run: re-scroll to MA header, re-read the points badge so
        // we can log before/after state. Delta vs initial reflects real
        // credit earned this run.
        scrollUntilMaHeader()
        val reHeader = primitives.findByAnyHint(BingPackage.MORE_ACTIVITIES_HINTS, 500L)
        if (reHeader != null) {
            val (postDone, postMax) = readProgressBadge(reHeader)
            val gained = if (pointsDone != null && postDone != null) postDone - pointsDone else null
            FarmerLog.i("[${account.label}] more-activities: points ${pointsDone ?: '?'}→${postDone ?: '?'} / ${postMax ?: '?'} (gained=${gained ?: '?'}), tiles_tapped=$completed, visited=${visitedKeys.size}")
        } else {
            FarmerLog.i("[${account.label}] more-activities: tiles_tapped=$completed, visited=${visitedKeys.size} (final badge unreadable)")
        }

        primitives.backToBingHome()
        return if (completed == 0) TaskResult.Skipped("no bonus tiles credited")
        else TaskResult.Ok(pointsClaimed = completed * 5, detail = "$completed tiles")
    }

    /**
     * Read the "N/M" progress badge TextView adjacent to the MA header.
     * Returns (done, total) — nulls if the badge couldn't be parsed.
     */
    private suspend fun readProgressBadge(header: AutomationNode): Pair<Int?, Int?> {
        val tvs = primitives.host.findByClass("android.widget.TextView")
        val rx = Regex("""(\d+)\s*/\s*(\d+)""")
        val near = tvs.firstOrNull { n ->
            n.visible &&
                n.boundsT in (header.boundsT - 30)..(header.boundsB + 30) &&
                n.boundsL > header.boundsR &&  // badge is to the RIGHT of the title
                rx.containsMatchIn(n.text?.toString() ?: "")
        } ?: return Pair(null, null)
        val m = rx.find(near.text!!) ?: return Pair(null, null)
        val done = m.groupValues[1].toIntOrNull()
        val total = m.groupValues[2].toIntOrNull()
        FarmerLog.d("more-activities: badge '${near.text}' -> $done/$total")
        return Pair(done, total)
    }

    /** Scroll until MÁS ACTIVIDADES header sits in the top 30% of the viewport. */
    private suspend fun scrollUntilMaHeader(): AutomationNode? {
        val screenH = primitives.host.screenHeight
        val targetMaxY = (screenH * 0.30).toInt()
        for (attempt in 0 until 16) {
            val h = primitives.findByAnyHint(BingPackage.MORE_ACTIVITIES_HINTS, 500L)
            if (h != null && h.visible) {
                when {
                    h.boundsT in 10..targetMaxY -> return h
                    h.boundsT > screenH * 0.6 -> primitives.scrollDown(0.4f)
                    h.boundsT < 10 -> primitives.scrollUp(0.3f)
                    else -> return h
                }
                primitives.idle(300, 500)
            } else {
                primitives.scrollDown(0.65f); primitives.idle(300, 500)
            }
        }
        return primitives.findByAnyHint(BingPackage.MORE_ACTIVITIES_HINTS, 500L)
    }

    /**
     * Find MA tile wrappers strictly below the given header. No section
     * follows MA on this build, so the band extends to 88% of screen —
     * excludes the bottom nav (icons at y>2160 are 140x140, also filtered
     * by width > 50% screen).
     */
    private suspend fun findMaTilesBelowHeader(header: AutomationNode): List<AutomationNode> {
        val screenH = primitives.host.screenHeight
        val bandTop = header.boundsB + 5
        val nextSection = primitives.host.findByText("Puntos de hoy", 200L)
            ?.takeIf { it.visible && it.boundsT > header.boundsT }
        val bandBot = nextSection?.boundsT ?: (screenH * 0.88).toInt()
        return findTilesInBand(bandTop, bandBot)
    }

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
     * Inside an MA tile: usually a quiz / poll / informational card. Bing
     * credits MA tiles by entry alone on this build, so a short dwell plus
     * best-effort action-button taps for quiz paths is enough.
     */
    private suspend fun driveTileContent(maxTicks: Int = 6): Boolean {
        primitives.idle(1_500, 2_000)

        val start = System.currentTimeMillis()
        val maxDurationMs = 6_000L
        val screenH = primitives.host.screenHeight
        val chromeTopY = (screenH * 0.85).toInt()

        val actionWords = listOf(
            "Iniciar", "Start", "Comenzar", "Begin",
            "Siguiente", "Next", "Continuar", "Continue",
            "Votar", "Vote", "Enviar", "Submit",
            "Finalizar", "Finish", "Hecho", "Done",
            "Jugar", "Play", "Aceptar", "Accept",
            "Mostrar respuesta", "Show answer",
        )
        val optionDescs = listOf("Opción", "Option")

        var meaningful = 0
        var idleTicks = 0
        var lastTapY = -1
        var sameYCount = 0
        var tick = 0
        var noButtonTicks = 0

        while (tick < maxTicks) {
            tick++
            if (System.currentTimeMillis() - start > maxDurationMs) break
            if (primitives.findByAnyHint(BingPackage.REWARDS_HUB_HINTS, 300L) != null) break
            if (meaningful >= 2) break

            val btn = firstAction(actionWords, chromeTopY)
                ?: firstOption(optionDescs, chromeTopY)

            if (btn != null) {
                if (btn.boundsT == lastTapY) {
                    sameYCount++
                    if (sameYCount >= 2) break
                } else sameYCount = 0
                lastTapY = btn.boundsT
                noButtonTicks = 0
                val label = (btn.text ?: btn.contentDescription)?.toString()?.take(24)
                FarmerLog.d("  MA tile: tap '$label' @y=${btn.boundsT}")
                primitives.host.tapCoord(btn.centerX.toFloat(), btn.centerY.toFloat())
                meaningful++; idleTicks = 0
                primitives.idle(900, 1_500)
                continue
            }

            noButtonTicks++
            if (meaningful == 0 && noButtonTicks >= 1) {
                FarmerLog.d("  MA tile: no actions, entry credit only")
                return true
            }
            idleTicks++
            if (idleTicks >= 2) break
            primitives.idle(400, 700)
        }
        return true
    }

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

    private suspend fun firstOption(descs: List<String>, chromeTopY: Int): AutomationNode? {
        for (d in descs) {
            val candidates = primitives.host.findAllByText(d).filter { c ->
                c.visible && c.boundsT < chromeTopY &&
                    c.contentDescription?.toString()?.trim().equals(d, ignoreCase = true)
            }
            if (candidates.isNotEmpty()) return candidates.first()
        }
        return null
    }
}
