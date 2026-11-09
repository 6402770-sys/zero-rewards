package com.zero.rewards.core.runner

import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.log.FarmerLog

/**
 * Leer para ganar / Read to earn runner.
 *
 * Entry flow (per operator spec):
 *   1. Open Rewards panel, scroll down to the "Leer para ganar" tile
 *      inside the "Actividades diarias" section.
 *   2. Tap the tile → Bing opens its news feed (same surface as the
 *      Para Ti / Noticias feed on Bing home, but reached via Rewards).
 *   3. In the feed, pick cards that are neither ads nor videos. Tap each.
 *   4. Dwell 3-3.5 s inside the article — enough for the read credit.
 *   5. Back → scroll past the just-read article → next.
 *
 * Cap: 10 articles × 3 pts = 30 pts per day (Microsoft Rewards daily cap).
 *
 * Card structure observed on current Bing build (screen 1080x2460):
 *   Each feed card exposes 3-4 sibling Views at the SAME bounds,
 *   xy=(50, Y), width=980, height 174..778:
 *     - a visual wrapper (click=false, desc=null)
 *     - the tap target (click=true, desc=null)  ← we tap this
 *     - a desc sibling (click=false, desc="<title + source>")
 *     - optionally an inner image View at xy=(80, Y+30), width=920
 *
 * Ad signals (any match → skip):
 *   - desc contains "Anuncio" as a word (ad slots end their desc with
 *     ". Anuncio," before the sponsor info)
 *   - nested TextView with EXACT text "Anuncio" inside the card bounds
 *   - nested View with desc "Privacidad de los anuncios" inside bounds
 *
 * Video signals (any match → skip):
 *   - desc contains "Reproducir" (truncated "Reproducir vídeo")
 *   - desc contains a known video platform: Dailymotion / YouTube /
 *     Vimeo / TikTok / Twitch
 *   - nested View with desc "Reproducir vídeo" inside the card bounds
 *   - duration pattern mm:ss in a nested TextView inside the card
 *
 * Non-targets excluded by the selector (width/left-edge filter):
 *   - top "glance_card" tiles (Rewards/Generador imágenes/Dinero) — 290..350 wide
 *   - "Más" context buttons — 120x120
 *   - "Abrir artículo" button — 360x120
 *   - bottom nav icons — 140x140, outside band anyway
 */
class BingReadToEarnRunner(
    private val primitives: BingPrimitives,
    private val targetArticles: Int = 10,
) {

    private val panel = BingRewardsPanel(primitives)

    suspend fun run(account: Account): TaskResult {
        if (!panel.openPanel()) return TaskResult.Failed("rewards panel didn't open")
        primitives.idle(600, 1_000)

        // Find and tap "Leer para ganar" tile.
        FarmerLog.d("[${account.label}] read-to-earn: reset + find 'Leer para ganar' tile")
        repeat(6) { primitives.scrollUp(0.75f); primitives.idle(160, 280) }
        primitives.idle(500, 900)

        val tile = findLeerParaGanarTile()
        if (tile == null) {
            FarmerLog.w("[${account.label}] read-to-earn: 'Leer para ganar' tile not found")
            primitives.backToBingHome()
            return TaskResult.Skipped("Leer para ganar tile not found")
        }
        FarmerLog.i("[${account.label}] read-to-earn: tile @y=${tile.boundsT} size=${tile.width}x${tile.height}")
        primitives.host.tapCoord(tile.centerX.toFloat(), tile.centerY.toFloat())
        primitives.idle(2_500, 3_500)

        if (panel.isHubVisible(900L)) {
            FarmerLog.w("[${account.label}] read-to-earn: tap on tile didn't navigate")
            primitives.backToBingHome()
            return TaskResult.Skipped("tap on Leer para ganar didn't navigate")
        }

        val visitedKeys = mutableSetOf<String>()
        var read = 0
        var skippedAds = 0
        var skippedVideos = 0
        var staleRounds = 0
        val maxIters = 80

        for (iter in 0 until maxIters) {
            if (read >= targetArticles) {
                FarmerLog.d("[${account.label}] read-to-earn: hit target $targetArticles articles")
                break
            }
            if (staleRounds >= 5) {
                FarmerLog.d("[${account.label}] read-to-earn: $staleRounds stale rounds, stop")
                break
            }

            val cards = findFeedCards()
            val next = cards.firstOrNull { descKey(it.desc) !in visitedKeys }

            if (next == null) {
                staleRounds++
                FarmerLog.d("[${account.label}] read-to-earn iter $iter: no new cards (stale #$staleRounds), scroll down")
                primitives.scrollDown(0.65f); primitives.idle(500, 900)
                continue
            }
            staleRounds = 0
            visitedKeys += descKey(next.desc)

            when {
                next.isAd -> {
                    FarmerLog.d("[${account.label}] skip AD: '${next.desc.take(50)}'")
                    skippedAds++
                    primitives.scrollDown(0.4f); primitives.idle(300, 500)
                }
                next.isVideo -> {
                    FarmerLog.d("[${account.label}] skip VIDEO: '${next.desc.take(50)}'")
                    skippedVideos++
                    primitives.scrollDown(0.4f); primitives.idle(300, 500)
                }
                else -> {
                    FarmerLog.i("[${account.label}] read #${read + 1}/$targetArticles (attempt): '${next.desc.take(60)}'")
                    val navigated = tapUntilOpen(next.tapTarget, next.desc)
                    if (!navigated) {
                        FarmerLog.w("[${account.label}] tap on '${next.desc.take(30)}' failed to open after retries — skip, not counted")
                        primitives.scrollDown(0.4f); primitives.idle(300, 500)
                        continue
                    }

                    // Per operator: 3-3.5 s inside the article is enough for
                    // Bing to credit the read. No scrolling, no pretending.
                    primitives.idle(3_000, 3_500)
                    read++
                    FarmerLog.i("[${account.label}] read #$read/$targetArticles DONE")

                    primitives.host.back()
                    primitives.idle(800, 1_300)

                    // Scroll past the just-read card so the next iter starts
                    // on fresh viewport content.
                    primitives.scrollDown(0.55f); primitives.idle(300, 500)
                }
            }
        }

        FarmerLog.i("[${account.label}] read-to-earn: read=$read ads=$skippedAds videos=$skippedVideos target=$targetArticles")
        primitives.host.back(); primitives.idle(500, 900)
        primitives.backToBingHome()
        return if (read == 0) TaskResult.Skipped("no articles read")
        else TaskResult.Ok(
            pointsClaimed = read * 3,
            detail = "$read articles, $skippedAds ads, $skippedVideos videos skipped",
        )
    }

    /**
     * Find the "Leer para ganar" tile inside Actividades diarias. Same
     * shape as daily-set / MA tiles: clickable View, non-empty desc
     * containing the phrase, wider than 50 % of screen, height 100..400.
     * Scrolls the hub down until it appears.
     */
    private suspend fun findLeerParaGanarTile(): AutomationNode? {
        val screenW = primitives.host.screenWidth

        for (attempt in 0 until 14) {
            val classes = listOf(
                "android.view.View", "android.view.ViewGroup",
                "android.widget.FrameLayout", "android.widget.LinearLayout",
            )
            val all = buildList { for (c in classes) addAll(primitives.host.findByClass(c)) }
                .distinctBy { it.handle }

            val hit = all
                .filter { n ->
                    n.visible && n.clickable &&
                        n.width > screenW * 0.5 && n.height in 100..400 &&
                        (n.contentDescription?.toString()?.contains("Leer para ganar", ignoreCase = true) == true ||
                            n.contentDescription?.toString()?.contains("Read to earn", ignoreCase = true) == true)
                }
                .maxByOrNull { it.width * it.height }
            if (hit != null) return hit

            primitives.scrollDown(0.6f); primitives.idle(400, 700)
            if (attempt > 8 && !panel.isHubVisible(300L)) break
        }
        return null
    }

    private data class FeedCard(
        val tapTarget: AutomationNode,
        val desc: String,
        val isAd: Boolean,
        val isVideo: Boolean,
    )

    /**
     * Enumerate feed cards. A card = clickable wrapper View paired with
     * its desc sibling by exact bounds. Returned sorted top-to-bottom,
     * deduped by exact bounds (two click=true at same pos → keep one).
     */
    private suspend fun findFeedCards(): List<FeedCard> {
        val screenH = primitives.host.screenHeight
        val bandTop = (screenH * 0.08).toInt()
        val bandBot = (screenH * 0.88).toInt()

        val classes = listOf(
            "android.view.View", "android.view.ViewGroup",
            "android.widget.FrameLayout", "android.widget.LinearLayout",
            "android.widget.TextView",
        )
        val all = buildList { for (c in classes) addAll(primitives.host.findByClass(c)) }
            .distinctBy { it.handle }
            .filter { it.visible }

        val wrappers = all.filter { n ->
            n.clickable &&
                n.boundsT in bandTop..bandBot &&
                n.width >= 900 &&
                n.boundsL in 40..60 &&
                n.contentDescription.isNullOrBlank() &&
                n.text.isNullOrBlank() &&
                n.height in 150..900
        }.sortedBy { it.boundsT }

        val cards = mutableListOf<FeedCard>()
        val usedBoundsKeys = mutableSetOf<String>()
        for (w in wrappers) {
            val boundsKey = "${w.boundsL},${w.boundsT},${w.boundsR},${w.boundsB}"
            if (boundsKey in usedBoundsKeys) continue
            usedBoundsKeys += boundsKey

            // Prefer the desc sibling with EXACT bounds match (full title + source).
            val descNode = all.firstOrNull { n ->
                !n.clickable &&
                    n.boundsL == w.boundsL && n.boundsT == w.boundsT &&
                    n.boundsR == w.boundsR && n.boundsB == w.boundsB &&
                    !n.contentDescription.isNullOrBlank()
            } ?: all.firstOrNull { n ->
                // Fallback: inner image child (xy=~(80, Y+30), width ≥ 800).
                n.boundsT in w.boundsT..w.boundsB &&
                    n.boundsL >= w.boundsL && n.boundsR <= w.boundsR &&
                    n.width > 800 && !n.contentDescription.isNullOrBlank()
            }
            if (descNode == null) {
                FarmerLog.d("feed: wrapper @y=${w.boundsT} with no desc sibling, skip")
                continue
            }
            val desc = descNode.contentDescription!!.toString().trim()
            cards += FeedCard(w, desc, isAdCard(w, desc, all), isVideoCard(w, desc, all))
        }
        return cards
    }

    /**
     * Ad detection. Returns true on any of:
     *   - desc contains "Anuncio" as a word (chip text embedded in desc).
     *   - nested TextView text="Anuncio" exactly (case-insensitive)
     *     within the card bounds.
     *   - nested View with desc containing "Privacidad de los anuncios"
     *     within the card bounds.
     */
    private fun isAdCard(
        wrapper: AutomationNode,
        desc: String,
        all: List<AutomationNode>,
    ): Boolean {
        if (Regex("""\bAnuncio\b""", RegexOption.IGNORE_CASE).containsMatchIn(desc)) return true

        val chip = all.firstOrNull { n ->
            n.boundsT in wrapper.boundsT..wrapper.boundsB &&
                n.boundsL in wrapper.boundsL..wrapper.boundsR &&
                n.text?.toString()?.trim().equals("Anuncio", ignoreCase = true)
        }
        if (chip != null) return true

        val privacyChip = all.firstOrNull { n ->
            n.boundsT in wrapper.boundsT..wrapper.boundsB &&
                n.boundsL in wrapper.boundsL..wrapper.boundsR &&
                n.contentDescription?.toString()?.contains("Privacidad de los anuncios", ignoreCase = true) == true
        }
        return privacyChip != null
    }

    /**
     * Video detection. Returns true on any of:
     *   - desc contains "Reproducir" (truncated "Reproducir vídeo").
     *   - desc contains a known video-platform source as a word.
     *   - nested View with desc containing "Reproducir vídeo" inside bounds.
     *   - duration pattern mm:ss in a nested TextView inside bounds.
     */
    private fun isVideoCard(
        wrapper: AutomationNode,
        desc: String,
        all: List<AutomationNode>,
    ): Boolean {
        if (desc.contains("Reproducir", ignoreCase = true)) return true
        if (Regex("""\b(Dailymotion|YouTube|Vimeo|TikTok|Twitch)\b""", RegexOption.IGNORE_CASE).containsMatchIn(desc)) return true

        val playNode = all.firstOrNull { n ->
            n.boundsT in wrapper.boundsT..wrapper.boundsB &&
                n.boundsL in wrapper.boundsL..wrapper.boundsR &&
                n.contentDescription?.toString()?.contains("Reproducir vídeo", ignoreCase = true) == true
        }
        if (playNode != null) return true

        val durationRx = Regex("""\b\d{1,2}:\d{2}\b""")
        val durNode = all.firstOrNull { n ->
            n.boundsT in wrapper.boundsT..wrapper.boundsB &&
                n.boundsL in wrapper.boundsL..wrapper.boundsR &&
                durationRx.containsMatchIn(n.text?.toString() ?: "")
        }
        return durNode != null
    }

    /**
     * Normalize a card's desc to a stable dedup key. Bing renders the
     * desc differently across scroll passes for the same article:
     *   - with source:  "20 Ferraris que nunca se fabricaron. Classic & Sports Car"
     *   - without source: "20 Ferraris que nunca se fabricaron"
     * take(50) on both diverges, so we extract the TITLE head (everything
     * before the first ". " — source / suffix is always after) and
     * normalize case + length. This collapses both variants to one key.
     * If the head is too short (<15 chars), fall back to the full desc
     * truncated to 50 chars so we never dedup unrelated articles together.
     */
    private fun descKey(desc: String): String {
        val trimmed = desc.trim()
        val head = trimmed.split(". ").firstOrNull()?.trim().orEmpty()
        val base = if (head.length >= 15) head else trimmed
        return base.take(40).lowercase()
    }

    /**
     * Try to open the article. Primary tap on wrapper center; on failure,
     * retry at image-area center (wrapper top + ~250 px) which is the
     * canonical news-card touch region; on second failure, retry at title
     * area (wrapper bottom - ~80 px). Each retry waits up to 3.5 s for
     * navigation off the feed. Returns true when navigated, false after
     * all three attempts.
     *
     * Operator report: "a veces no tappea bien el articulo y no lo abre"
     * — accessibility gestures occasionally get swallowed and the
     * navigation never happens. Retries at different Y offsets land on
     * different touch zones inside the card and recover most misses.
     */
    private suspend fun tapUntilOpen(target: AutomationNode, desc: String): Boolean {
        val cx = target.centerX.toFloat()
        val offsets = listOf(
            "center" to target.centerY.toFloat(),
            "image"  to (target.boundsT + 250).toFloat().coerceAtMost(target.boundsB - 20f),
            "title"  to (target.boundsB - 80).toFloat().coerceAtLeast(target.boundsT + 20f),
        )
        for ((label, cy) in offsets) {
            FarmerLog.d("  tap try='$label' ($cx, $cy) for '${desc.take(30)}'")
            primitives.host.tapCoord(cx, cy)
            if (waitArticleOpen(timeoutMs = 3_500L)) {
                FarmerLog.d("  tap OK on '$label'")
                return true
            }
            FarmerLog.d("  tap '$label' didn't navigate — retry")
        }
        return false
    }

    /**
     * Poll until the feed-card signature disappears from the current view,
     * meaning we've navigated off the feed into an article. Returns true
     * when navigated, false on timeout. 300 ms poll interval keeps the
     * cost low; findByClass is fast enough on short timeouts.
     */
    private suspend fun waitArticleOpen(timeoutMs: Long = 5_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        // Minimum settle time — the gesture needs at least a frame.
        primitives.idle(600, 900)
        while (System.currentTimeMillis() < deadline) {
            if (!stillOnFeed()) return true
            primitives.idle(300, 450)
        }
        return false
    }

    /**
     * True when the current view shows a feed-card wrapper (clickable
     * View with boundsL ≈ 50, width ≥ 900, height 150..900, desc/text
     * both null). This signature is unique to the news feed — article
     * viewers render completely different hierarchies.
     */
    private suspend fun stillOnFeed(): Boolean {
        val views = primitives.host.findByClass("android.view.View")
        return views.any { n ->
            n.visible && n.clickable &&
                n.boundsL in 40..60 && n.width >= 900 &&
                n.height in 150..900 &&
                n.contentDescription.isNullOrBlank() &&
                n.text.isNullOrBlank()
        }
    }
}
