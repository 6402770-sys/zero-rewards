package com.zero.rewards.core.runner

import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPackage
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog
import kotlin.math.abs

/**
 * Shared helpers for navigating to the Bing app's Rewards panel and driving
 * tiles under a section header.
 *
 * The panel is a vertically scrolled list with several named sections:
 *   - "Conjunto diario" / Daily set
 *   - "Intervalos diarios" / Daily streak
 *   - "Más actividades" / More activities
 *   - punchcards, point offers, etc.
 *
 * Each tile credits points on first tap through to its content (daily set
 * tiles usually +10, more-activities +5, streak intervals +50 each).
 */
class BingRewardsPanel(private val primitives: BingPrimitives) {

    suspend fun openPanel(): Boolean {
        if (!primitives.ensureBingForeground()) {
            FarmerLog.w("rewards panel: Bing didn't foreground")
            return false
        }
        primitives.idle(500, 1_000)

        if (isOnRewardsHub(timeoutMs = 800L)) return true

        primitives.backToBingHome()
        primitives.idle(300, 700)

        if (tapRewardsEntryIfVisible()) {
            primitives.idle(900, 1_600)
            if (isOnRewardsHub(timeoutMs = 2_000L)) return true
        }

        if (openAppsPanel()) {
            if (tapRewardsEntryIfVisible()) {
                primitives.idle(900, 1_600)
                if (isOnRewardsHub(timeoutMs = 2_000L)) return true
            }
        }

        FarmerLog.w("rewards panel: entry not found. tree=\n${primitives.host.dumpTree(7)}")
        return false
    }

    private suspend fun tapRewardsEntryIfVisible(): Boolean {
        val target = primitives.findByAnyHint(BingPackage.REWARDS_HINTS, 2_500L) ?: return false
        FarmerLog.d("rewards panel: entry via ${target.describe()}")
        return primitives.host.tap(target)
    }

    private suspend fun openAppsPanel(): Boolean {
        val apps = primitives.host.findByContentDesc("Aplicaciones", 1_500L)
            ?: primitives.host.findByContentDesc("Apps", 1_500L)
            ?: primitives.host.findByContentDesc("Más", 1_200L)
            ?: primitives.host.findByContentDesc("More", 1_200L)
            ?: return false
        FarmerLog.d("rewards panel: opening apps grid via ${apps.describe()}")
        primitives.host.tap(apps); primitives.idle(1_200, 2_000)
        return true
    }

    private suspend fun isOnRewardsHub(timeoutMs: Long = 1_000L): Boolean {
        // Any hub-specific text means we're there.
        return primitives.findByAnyHint(BingPackage.REWARDS_HUB_HINTS, timeoutMs) != null
    }

    /**
     * Drive every unvisited tile in the section identified by [sectionHints].
     *
     * Walks top-of-panel → finds header → for each pass, locates an unvisited
     * tile that lives between THIS section header and the next known section
     * header (so we never bleed into another section). Stops when:
     *   - the next section header is near the top of the viewport, OR
     *   - two consecutive scrolls yield no new tile, OR
     *   - [maxScrolls] is reached.
     *
     * Returns the number of tiles [onTile] reported credited.
     */
    suspend fun driveSection(
        sectionHints: List<String>,
        maxScrolls: Int = 14,
        onTile: suspend () -> Boolean,
    ): Int {
        // Reset to top of panel.
        repeat(5) { primitives.scrollUp(0.7f); primitives.idle(200, 400) }
        primitives.idle(400, 700)

        // Scroll until we find the section header, then anchor on it.
        val anchored = primitives.scrollUntil(maxScrolls = maxScrolls, fraction = 0.5f) {
            primitives.findByAnyHint(sectionHints, 600L)
        }
        if (anchored == null) {
            FarmerLog.w("rewards panel: section header not found for $sectionHints")
            return 0
        }

        val otherSectionTexts = BingPackage.ALL_REWARDS_SECTION_HINTS.filter { txt ->
            sectionHints.none { it.equals(txt, ignoreCase = true) }
        }

        val visited = mutableSetOf<String>()
        var completed = 0
        var stale = 0
        var scrolls = 0
        var diagDumped = false

        while (scrolls < maxScrolls) {
            val hereHeader = primitives.findByAnyHint(sectionHints, 500L)
            val sectionTopY = hereHeader?.boundsB ?: 0
            val nextHeader = findNextSectionHeader(otherSectionTexts, minY = sectionTopY)
            val sectionBottomY = nextHeader?.boundsT ?: Int.MAX_VALUE

            val tile = findUnvisitedTile(sectionTopY, sectionBottomY, visited)
            val nextNearTop = nextHeader != null &&
                nextHeader.boundsT < primitives.host.screenHeight * 0.30

            if (tile == null) {
                if (nextNearTop) {
                    FarmerLog.d("driveSection: next section at y=${nextHeader?.boundsT}, stop")
                    break
                }
                if (!diagDumped) {
                    dumpSectionDiagnostic(sectionHints, hereHeader, sectionTopY, sectionBottomY, nextHeader)
                    diagDumped = true
                }
                stale++
                if (stale >= 4) {
                    FarmerLog.d("driveSection: no new tiles after $stale scrolls, stop")
                    break
                }
                // Aggressive scroll when the header is near the bottom of
                // the screen — tiles are offscreen below and need a big
                // scroll to come into view.
                val screenH = primitives.host.screenHeight
                val fraction = if (sectionTopY > screenH * 0.65) 0.7f else 0.5f
                primitives.scrollDown(fraction); primitives.idle(400, 700); scrolls++
                continue
            }

            stale = 0
            // Dedup by title text AND by Y bucket — post-claim the badge text
            // flips from "+10" to "10 ✓" and the neighbor title might come
            // back as a different string, re-selecting the same tile.
            visited += tile.key
            visited += "@y=${tile.node.boundsT / 50}"
            FarmerLog.d("driveSection[$sectionHints]: tile '${tile.key}' @y=${tile.node.boundsT}")

            // Use tapCoord (real touch gesture), NOT host.tap (ACTION_CLICK).
            // Rewards tiles accept ACTION_CLICK but don't navigate off it —
            // their onClick lives on a touch listener. Same lesson as the
            // more-activities / account-switcher tiles.
            primitives.host.tapCoord(tile.node.centerX.toFloat(), tile.node.centerY.toFloat())
            primitives.idle(1_800, 3_000)

            // If we're still on the hub, the tap failed to navigate — skip
            // this tile and move on (don't count as credited).
            if (isOnRewardsHub(900L)) {
                FarmerLog.w("driveSection: tap didn't navigate for '${tile.key}' — skip")
                primitives.scrollDown(0.3f); scrolls++
                continue
            }

            if (onTile()) completed++
            primitives.host.back(); primitives.idle(1_000, 1_800)

            // Re-anchor — the back may have thrown us out of the panel view.
            if (primitives.findByAnyHint(sectionHints, 1_200L) == null) {
                if (primitives.findByAnyHint(BingPackage.ALL_REWARDS_SECTION_HINTS, 1_200L) == null) {
                    FarmerLog.d("driveSection: lost panel, reopening")
                    if (!openPanel()) return completed
                }
                primitives.scrollUntil(maxScrolls = maxScrolls, fraction = 0.5f) {
                    primitives.findByAnyHint(sectionHints, 500L)
                }
            }
        }
        return completed
    }

    /**
     * Drive every unvisited bonus tile anywhere in the hub, without a section
     * header anchor. The previous +N-text scan missed tiles on current Bing
     * builds because the "+ 5"/"+ 10" badges are drawables and the badge text
     * doesn't carry the "+" through the a11y tree. New strategy: find
     * clickable tile-shaped Views anywhere in the hub, dedupe by their text /
     * contentDescription, and tap them.
     */
    suspend fun driveAllRemainingBadges(
        maxTiles: Int = 24,
        onTile: suspend () -> Boolean,
    ): Int {
        // Reset to top.
        repeat(5) { primitives.scrollUp(0.75f); primitives.idle(200, 400) }
        primitives.idle(400, 700)

        val visited = mutableSetOf<String>()
        var completed = 0
        var scrolls = 0
        val maxScrolls = 24
        var sawAnyTile = false
        var stale = 0
        var diagDumped = false

        while (scrolls < maxScrolls && completed < maxTiles) {
            val tile = findClickableCard(minY = 0, visited = visited)
            if (tile == null) {
                if (sawAnyTile) stale++
                if (!diagDumped && scrolls >= 6) {
                    dumpSectionDiagnostic(listOf("<any card>"), null, 0, Int.MAX_VALUE, null)
                    diagDumped = true
                }
                if (stale >= 3) break
                primitives.scrollDown(0.65f); primitives.idle(400, 700); scrolls++
                continue
            }
            sawAnyTile = true
            stale = 0
            visited += tile.key
            FarmerLog.d("driveAll: attempt tap '${tile.key.take(60)}' @y=${tile.node.boundsT} size=${tile.node.width}x${tile.node.height}")

            // Primary tap: center of the tile.
            primitives.host.tapCoord(tile.node.centerX.toFloat(), tile.node.centerY.toFloat())
            primitives.idle(1_000, 1_600)

            if (isHubVisible(700L)) {
                val badgeX = (tile.node.boundsR - tile.node.height / 2).toFloat()
                val badgeY = tile.node.centerY.toFloat()
                FarmerLog.d("driveAll: retry on badge area ($badgeX, $badgeY)")
                primitives.host.tapCoord(badgeX, badgeY)
                primitives.idle(900, 1_400)
                if (isHubVisible(700L)) {
                    FarmerLog.d("driveAll: still on hub after badge retry — tile claimed/locked")
                    primitives.scrollDown(0.5f); primitives.idle(200, 400); scrolls++
                    continue
                }
            }

            FarmerLog.d("driveAll: entered tile content")
            val credited = onTile()
            FarmerLog.d("driveAll: tile credited=$credited; backing out")
            if (credited) completed++
            primitives.host.back(); primitives.idle(500, 900)

            // Lost the hub after back? Reopen.
            if (!isHubVisible(1_500L)) {
                FarmerLog.d("driveAll: lost hub after back; reopening")
                if (!openPanel()) return completed
            }
        }
        FarmerLog.d("driveAll: loop exit after $scrolls scrolls, completed=$completed")
        return completed
    }

    /**
     * Find a clickable tile-shaped card the hub, not yet visited.
     *
     * A tile card is: visible, clickable, wider than 40% of screen, height
     * 100..500 (tile-sized, not a nav icon, not a full-screen container),
     * carries a text or contentDescription substantial enough to dedupe on.
     * Known UI chrome (bottom-nav labels) is excluded by a small denylist.
     */
    private suspend fun findClickableCard(minY: Int, visited: Set<String>): TileHit? {
        val classes = listOf(
            "android.view.View",
            "android.view.ViewGroup",
            "android.widget.LinearLayout",
            "android.widget.FrameLayout",
            "android.widget.RelativeLayout",
            "androidx.recyclerview.widget.RecyclerView\$ViewHolder",
        )
        val screenW = primitives.host.screenWidth
        val screenH = primitives.host.screenHeight
        val all = buildList {
            for (c in classes) addAll(primitives.host.findByClass(c))
        }.distinctBy { it.handle }

        val denyText = setOf(
            "inicio", "home", "cámara", "camara", "camera", "copilot",
            "pestañas", "pestanas", "tabs", "aplicaciones", "apps",
            "buscar 1 vez", "conjunto diario", "puntos de hoy",
            "rachas", "intervalos diarios", "actividades diarias",
        )

        val candidates = all.asSequence()
            .filter { n ->
                n.visible && n.clickable &&
                    n.boundsT > minY &&
                    n.boundsT < screenH * 0.92 &&
                    n.width > screenW * 0.4 &&
                    n.height in 100..520
            }
            .mapNotNull { n ->
                val raw = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
                if (raw.length < 5) null else n to raw
            }
            .filter { (_, key) -> key.lowercase() !in denyText }
            .sortedBy { it.first.boundsT }

        for ((n, key) in candidates) {
            if (visited.any { it.equals(key, ignoreCase = true) }) continue
            return TileHit(n, key)
        }
        return null
    }

    /** True when we can see any hub-exclusive text. */
    suspend fun isHubVisible(timeoutMs: Long = 1_000L): Boolean =
        isOnRewardsHub(timeoutMs)

    /**
     * Legacy collect-all API kept for callers that need a tile list up-front.
     * New code should use [driveSection]. This is a best-effort list limited
     * to the first scroll-page of the section and will under-count large
     * sections — the old impl mis-deduped "+N" labels.
     */
    suspend fun collectTilesUnder(sectionHints: List<String>, maxScrolls: Int = 6): List<AutomationNode> {
        repeat(4) { primitives.scrollUp(0.7f); primitives.idle(200, 400) }
        val header = primitives.scrollUntil(maxScrolls = maxScrolls, fraction = 0.5f) {
            primitives.findByAnyHint(sectionHints, 600L)
        } ?: run {
            FarmerLog.w("collectTilesUnder: header not found for $sectionHints")
            return emptyList()
        }
        val otherSectionTexts = BingPackage.ALL_REWARDS_SECTION_HINTS.filter { txt ->
            sectionHints.none { it.equals(txt, ignoreCase = true) }
        }
        val nextHeader = findNextSectionHeader(otherSectionTexts, minY = header.boundsB)
        val topY = header.boundsB
        val botY = nextHeader?.boundsT ?: Int.MAX_VALUE
        return pointBadgesInBand(topY, botY)
    }

    private data class TileHit(val node: AutomationNode, val key: String)

    private suspend fun findUnvisitedTile(
        sectionTopY: Int,
        sectionBottomY: Int,
        visited: Set<String>,
    ): TileHit? {
        // Strategy 1 (legacy Bing): "+N" text badges with neighbor title.
        val badges = pointBadgesInBand(sectionTopY, sectionBottomY)
        if (badges.isNotEmpty()) {
            val textViews = primitives.host.findByClass("android.widget.TextView")
                .filter { it.visible && (it.text?.length ?: 0) >= 6 }
            for (p in badges) {
                val neighbor = textViews
                    .filter {
                        it.boundsT in sectionTopY..sectionBottomY &&
                            abs(it.centerY - p.centerY) < p.height.coerceAtLeast(1) * 4
                    }
                    .minByOrNull {
                        abs(it.centerY - p.centerY) * 10_000 + abs(it.centerX - p.centerX)
                    }
                val key = (neighbor?.text?.trim() ?: "badge@${p.boundsT / 24}:${p.text}")
                if (key in visited) continue
                return TileHit(p, key)
            }
        }

        // Strategy 2 (current Bing): clickable tile-shaped cards scoped to
        // the section band. The "+5/+10" badges are drawables here, not
        // text, so strategy 1 finds nothing — but the tile cards are
        // clickable Views we can detect by shape + bounds.
        return findClickableCardInBand(sectionTopY, sectionBottomY, visited)
    }

    /**
     * Find a clickable tile card within the section band (sectionTopY to
     * sectionBottomY). Same criteria as findClickableCard (hub-wide) but
     * scoped so we never bleed into adjacent sections.
     */
    private suspend fun findClickableCardInBand(
        sectionTopY: Int,
        sectionBottomY: Int,
        visited: Set<String>,
    ): TileHit? {
        val classes = listOf(
            "android.view.View", "android.view.ViewGroup",
            "android.widget.LinearLayout", "android.widget.FrameLayout",
            "android.widget.RelativeLayout",
        )
        val screenW = primitives.host.screenWidth
        val all = buildList {
            for (c in classes) addAll(primitives.host.findByClass(c))
        }.distinctBy { it.handle }

        val candidates = all.asSequence()
            .filter { n ->
                n.visible && n.clickable &&
                    n.boundsT >= sectionTopY && n.boundsB <= sectionBottomY &&
                    n.width > screenW * 0.4 &&
                    n.height in 100..520
            }
            .mapNotNull { n ->
                val raw = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
                if (raw.length < 5) null else n to raw
            }
            .sortedBy { it.first.boundsT }

        for ((n, key) in candidates) {
            if (visited.any { it.equals(key, ignoreCase = true) }) continue
            return TileHit(n, key)
        }
        return null
    }

    /** All "+N" point badges whose Y sits inside [topY, bottomY]. */
    private suspend fun pointBadgesInBand(topY: Int, bottomY: Int): List<AutomationNode> {
        val raw = primitives.host.findAllByText("+")
        return raw.filter { n ->
            n.visible &&
                n.text?.matches(Regex("^\\+\\s?\\d+\\s*$")) == true &&
                n.boundsT in topY..bottomY
        }.sortedWith(compareBy({ it.boundsT }, { it.boundsL }))
    }

    private suspend fun findNextSectionHeader(candidates: List<String>, minY: Int): AutomationNode? {
        val root = primitives.host.findByClass("android.widget.TextView")
        return root.asSequence()
            .filter { it.visible && it.boundsT > minY }
            .filter { n ->
                val t = n.text?.toString()
                t != null && candidates.any { c -> t.contains(c, ignoreCase = true) }
            }
            .minByOrNull { it.boundsT }
    }

    /**
     * Dump the content of the section band so the operator can see the real
     * node structure. Used when we failed to identify any tile — gives us
     * the data to pick a better selector.
     */
    private suspend fun dumpSectionDiagnostic(
        sectionHints: List<String>,
        hereHeader: AutomationNode?,
        topY: Int,
        bottomY: Int,
        nextHeader: AutomationNode?,
    ) {
        FarmerLog.d("DRIVE-SECTION DIAG >>> hints=$sectionHints")
        FarmerLog.d("  header=${hereHeader?.describe()} nextHeader=${nextHeader?.describe()}")
        FarmerLog.d("  band=[$topY..$bottomY] screen=${primitives.host.screenWidth}x${primitives.host.screenHeight}")

        val badges = pointBadgesInBand(topY, bottomY)
        FarmerLog.d("  +N text badges in band: ${badges.size}")

        // All clickable nodes, any class, visible, in band — these are the
        // real tap targets we care about.
        val classes = listOf(
            "android.view.View", "android.view.ViewGroup",
            "android.widget.LinearLayout", "android.widget.FrameLayout",
            "android.widget.RelativeLayout", "android.widget.Button",
            "android.widget.ImageButton", "android.widget.ImageView",
        )
        val clickables = buildList {
            for (c in classes) addAll(primitives.host.findByClass(c))
        }.distinctBy { it.handle }
            .filter { it.visible && it.clickable && it.boundsT in topY..bottomY }
            .sortedBy { it.boundsT }
        FarmerLog.d("  CLICKABLE nodes in band: ${clickables.size}")
        for (c in clickables.take(40)) {
            FarmerLog.d("    click ${c.className?.substringAfterLast('.')} " +
                "text='${c.text?.take(40)}' desc='${c.contentDescription?.take(50)}' " +
                "size=${c.width}x${c.height} y=${c.boundsT}")
        }

        val tvs = primitives.host.findByClass("android.widget.TextView")
            .filter { it.visible && it.boundsT in topY..bottomY }
        FarmerLog.d("  TextViews in band: ${tvs.size}")
        for (t in tvs.take(60)) {
            FarmerLog.d("    tv '${t.text?.take(50)}' desc='${t.contentDescription?.take(40)}' " +
                "size=${t.width}x${t.height} y=${t.boundsT}")
        }

        FarmerLog.d("<<< DRIVE-SECTION DIAG")
    }
}
