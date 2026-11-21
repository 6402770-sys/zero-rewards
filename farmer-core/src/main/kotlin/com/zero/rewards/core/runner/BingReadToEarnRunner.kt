package com.zero.rewards.core.runner

import com.zero.rewards.core.account.Account
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.BingPrimitives
import com.zero.rewards.core.automation.FrameMotion
import com.zero.rewards.core.log.FarmerLog

/**
 * Leer para ganar / Read to earn runner.
 *
 * Entry flow (per operator spec):
 *   1. Go to Bing Inicio (the home tab).
 *   2. Scroll down a few viewport fractions — the news feed (a WebView under
 *      the glance row) starts ~1.5 viewports below the search bar.
 *   3. In the feed, pick cards that are neither ads nor videos. Tap each.
 *   4. Dwell 3-3.5 s inside the article — enough for the read credit.
 *   5. Back → scroll past the just-read article → next.
 *
 * Cap: 10 articles × 3 pts = 30 pts per day (Microsoft Rewards daily cap).
 *
 * Feed structure seen in the 2026-11-21 dump (screen 1080x2460, Bing 34.4.x):
 *   - The tap target of a card is its TITLE: a plain `View`, clickable, x=60,
 *     ~960 wide, 52..105 tall, contentDescription = headline, no text. A
 *     sibling TextView at the same bounds carries the same headline as text.
 *   - The title sits at the bottom of a non-clickable 960x775 image View.
 *     Below it: 'Ver más' / 'Compartir' Buttons and a 'Más como este'
 *     ToggleButton — never tapped.
 *   - A sticky header (search 'Buscar' Button, bottom ~333) covers the top
 *     of the viewport and the bottom nav starts at y~2200: titles outside
 *     that band are "visible" to accessibility but not tappable.
 *
 * Ad signals (any match → skip):
 *   - headline desc contains the word 'Anuncio' / 'Patrocinado'
 *   - a 'Patrocinado' text/desc or a 'Selección de anuncio' desc within the
 *     220 px below the title
 *   - an Image whose viewId starts with 'nativead' ending right above the title
 *
 * Video signals (UNVERIFIED — the dump had no video card): 'Reproducir' in the
 * desc, a known video platform in the desc, a 'Reproducir vídeo' desc or an
 * mm:ss badge inside the card's image area.
 */
class BingReadToEarnRunner(
    private val primitives: BingPrimitives,
    private val targetArticles: Int = 10,
) {

    /**
     * Structure dump mode (HANDOFF REGLA CERO) — replaces the farm while
     * [StructureDumpMode.READ_TO_EARN] is on. Goes to Inicio, dumps it, scrolls
     * down in 6 calibrated 0.7 steps dumping at each stop together with what
     * the feed selector finds there, then taps ONE non-ad article (tapCoord on
     * its title), dumps the article screen, presses BACK and dumps again. A
     * final 60 s watch lets the operator wander by hand (e.g. the Rewards hub).
     */
    private suspend fun dumpStructure(account: Account): TaskResult {
        val dump = BingStructureDump(primitives.host)
        if (!primitives.ensureBingForeground()) return TaskResult.Skipped("Bing not foreground")
        if (!primitives.backToBingHome()) return TaskResult.Skipped("Bing Inicio not reachable")
        primitives.idle(800, 1_200)

        dump.snapshot("R2E/INICIO_TOP")
        probeFeed("INICIO_TOP")
        for (step in 1..6) {
            primitives.scrollDown(0.7f); primitives.idle(700, 1_000)
            dump.snapshot("R2E/SCROLL_$step")
            probeFeed("SCROLL_$step")
        }

        val target = findFeedCards().firstOrNull { !it.isAd && !it.isVideo }
        if (target == null) {
            FarmerLog.w("[${account.label}] R2E DUMP: no non-ad card in the tappable band at SCROLL_6 — article not dumped")
        } else {
            FarmerLog.i("[${account.label}] R2E DUMP: tapCoord on '${target.desc.take(60)}' ${target.tapTarget.describe()}")
            primitives.host.tapCoord(target.tapTarget.centerX.toFloat(), target.tapTarget.centerY.toFloat())
            primitives.idle(4_000, 4_800)
            dump.snapshot("R2E/ARTICLE")
            FarmerLog.d("R2E DUMP: after tap onFeed=${onFeed()} pkg=${primitives.host.currentPackage()}")
            primitives.host.back()
            primitives.idle(1_500, 2_200)
            dump.snapshot("R2E/AFTER_BACK")
            probeFeed("AFTER_BACK")
        }

        FarmerLog.i("[${account.label}] R2E DUMP: 60 s free watch — optionally open the Rewards hub and its 'Leer para ganar' entry by hand")
        dump.watch("R2E", durationMs = 60_000L)

        primitives.backToBingHome()
        return TaskResult.Skipped("structure dump mode — see R2E DUMP log")
    }

    /** Read-only: what the feed selector finds at the current scroll position. */
    private suspend fun probeFeed(where: String) {
        val cards = findFeedCards()
        FarmerLog.d("FEED PROBE @$where: cards=${cards.size} onFeed=${onFeed()}")
        for (c in cards) {
            FarmerLog.d("  card y=${c.tapTarget.boundsT}..${c.tapTarget.boundsB} ad=${c.isAd} video=${c.isVideo} desc='${c.desc.take(60)}'")
        }
    }

    /**
     * Diagnostic (replaces the farm while [StructureDumpMode.READ_TO_EARN_VIDEO_HUNT]
     * is on). The 2026-11-21 dump showed ads are covered, but a card titled
     * "Video: …" looked exactly like an article. Sweeps up to 14 feed positions,
     * logs EVERY card once with its ad signals, whether it has the 'Compartir'
     * meta row (ads don't) and how many nodes sit in its image area, snapshots
     * the screen when a non-ad card lacks the meta row, and opens up to 2 "Video:"
     * cards (tapCoord on the title) to dump what the player/article screen is.
     */
    private suspend fun videoHunt(account: Account): TaskResult {
        val dump = BingStructureDump(primitives.host)
        if (!primitives.ensureBingForeground()) return TaskResult.Skipped("Bing not foreground")
        if (!primitives.backToBingHome()) return TaskResult.Skipped("Bing Inicio not reachable")
        primitives.idle(800, 1_200)
        FarmerLog.i("[${account.label}] VIDEO HUNT: sweeping the feed — every card is logged with its signals")
        repeat(3) { primitives.scrollDown(0.7f); primitives.idle(450, 750) }

        val seen = mutableSetOf<String>()
        var opened = 0
        var anomalySnapshots = 0
        for (step in 1..14) {
            val all = snapshotVisible()
            val cards = findFeedCards()
            var candidate: FeedCard? = null
            var anomaly = false
            for (c in cards) {
                if (!seen.add(descKey(c.desc))) continue
                val t = c.tapTarget
                val signals = adSignals(t, c.desc, all)
                val hasMeta = all.any { n ->
                    n.boundsT in t.boundsB..(t.boundsB + 260) &&
                        n.contentDescription?.trim().equals("Compartir", ignoreCase = true)
                }
                val inArea = all.count { n ->
                    n.boundsT in (t.boundsB - 780)..(t.boundsB + 120) &&
                        n.boundsL >= t.boundsL - 20 && n.boundsR <= t.boundsR + 20
                }
                FarmerLog.d("HUNT step=$step card y=${t.boundsT}..${t.boundsB} adSignals=$signals video=${c.isVideo} meta=$hasMeta areaNodes=$inArea desc='${c.desc.take(70)}'")
                if (candidate == null && c.isVideo && !c.isAd && opened < 2) candidate = c
                if (!hasMeta && signals.isEmpty()) anomaly = true
            }
            if (anomaly && anomalySnapshots < 3) {
                anomalySnapshots++
                dump.snapshot("HUNT/NO_META_step$step")
            }
            if (candidate != null) {
                opened++
                dump.snapshot("HUNT/VIDEO_CARD_FEED_step$step")
                FarmerLog.i("[${account.label}] VIDEO HUNT: tapCoord on '${candidate.desc.take(60)}' ${candidate.tapTarget.describe()}")
                primitives.host.tapCoord(candidate.tapTarget.centerX.toFloat(), candidate.tapTarget.centerY.toFloat())
                primitives.idle(4_000, 4_800)
                dump.snapshot("HUNT/VIDEO_ARTICLE_$opened")
                FarmerLog.d("HUNT: after tap onFeed=${onFeed()}")
                primitives.host.back()
                primitives.idle(1_500, 2_200)
            }
            primitives.scrollDown(0.7f); primitives.idle(700, 1_000)
        }
        FarmerLog.i("[${account.label}] VIDEO HUNT: done — cards seen=${seen.size} videoOpened=$opened noMetaSnapshots=$anomalySnapshots")
        primitives.backToBingHome()
        return TaskResult.Skipped("structure dump mode — see VIDEO HUNT log")
    }

    suspend fun run(account: Account): TaskResult {
        if (StructureDumpMode.READ_TO_EARN) return dumpStructure(account)
        if (StructureDumpMode.READ_TO_EARN_VIDEO_HUNT) return videoHunt(account)

        // Reach Inicio. backToBingHome handles the "already there / tap
        // Inicio tab / relaunch" cascade internally.
        if (!primitives.backToBingHome()) {
            FarmerLog.w("[${account.label}] read-to-earn: Inicio didn't come up")
            return TaskResult.Skipped("Bing Inicio not reachable")
        }
        primitives.idle(600, 1_000)

        // Scroll down into the feed. Three viewport chunks is enough on
        // every Bing build we've seen — the "Para Ti" strip starts ~1.5
        // viewports below the Copilot search bar.
        FarmerLog.d("[${account.label}] read-to-earn: on Inicio, scrolling into feed")
        repeat(3) { primitives.scrollDown(0.7f); primitives.idle(450, 750) }

        // Verify feed is actually visible. If not, scroll a few more times
        // before giving up — some builds push the strip further down after
        // a "glance" carousel or a promo card.
        if (findFeedCards().isEmpty()) {
            FarmerLog.d("[${account.label}] read-to-earn: no feed cards yet, scrolling further")
            repeat(3) { primitives.scrollDown(0.7f); primitives.idle(450, 750) }
            if (findFeedCards().isEmpty()) {
                FarmerLog.w("[${account.label}] read-to-earn: feed not visible on Inicio")
                primitives.backToBingHome()
                return TaskResult.Skipped("feed not visible on Inicio")
            }
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
                else -> {
                    // Feed videos autoplay (muted) about a second after entering view and
                    // the play icon disappears, so decide by motion in the image area.
                    // The title heuristic only applies when screenshots are unavailable.
                    primitives.idle(900, 1_200)
                    var card = next
                    if (imageVisiblePx(card) < MIN_IMAGE_PX) {
                        // 2026-11-21 run: 3 of 10 cards had their thumbnail hidden under the
                        // sticky header, so the motion test couldn't run. Bring the card down.
                        primitives.scrollUp(0.25f); primitives.idle(1_000, 1_300)
                        card = findFeedCards().firstOrNull { descKey(it.desc) == descKey(next.desc) } ?: card
                    }
                    val playing = looksLikePlayingVideo(card)
                    if (playing == true || (playing == null && card.isVideo)) {
                        FarmerLog.d("[${account.label}] skip VIDEO (${if (playing == true) "motion" else "title heuristic"}): '${next.desc.take(50)}'")
                        skippedVideos++
                        primitives.scrollDown(0.4f); primitives.idle(300, 500)
                        continue
                    }
                    FarmerLog.i("[${account.label}] read #${read + 1}/$targetArticles (attempt): '${next.desc.take(60)}'")
                    val navigated = tapUntilOpen(card.tapTarget, card.desc)
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
        // No back() here — we end on Inicio (scrolled deep), and hitting
        // back on Inicio closes Bing. backToBingHome taps the Inicio tab,
        // which scrolls the feed back to the top.
        primitives.backToBingHome()
        return if (read == 0) TaskResult.Skipped("no articles read")
        else TaskResult.Ok(
            pointsClaimed = read * 3,
            detail = "$read articles, $skippedAds ads, $skippedVideos videos skipped",
        )
    }

    private data class FeedCard(
        val tapTarget: AutomationNode,
        val desc: String,
        val isAd: Boolean,
        val isVideo: Boolean,
    )

    private companion object {
        // Title View geometry from the 2026-11-21 dump: x=60, 960 wide, 52..105 tall.
        val TITLE_LEFT = 50..70
        val TITLE_WIDTH = 900..1_000
        val TITLE_HEIGHT = 40..160
        val AD_WORD = Regex("""\b(Anuncio|Patrocinado)\b""", RegexOption.IGNORE_CASE)
        /** Share of sampled image pixels that must change between two frames to call it a playing video. */
        const val VIDEO_MOTION_MIN = 0.01

        /** Minimum visible thumbnail height (px) to trust the motion test. */
        const val MIN_IMAGE_PX = 120

        /** Above this share of changed pixels in the (static) title row, the feed itself is moving. */
        const val VIDEO_CONTROL_MAX = 0.005
        val DURATION = Regex("""\b\d{1,2}:\d{2}\b""")
        // 2026-11-21 dumps (25 cards): video cards expose NO badge / 'Reproducir' / extra node, and
        // the article page is an empty WebView, so the only signal left is the word in the title.
        // Heuristic: it also skips some plain articles that merely mention a video — cheap.
        val VIDEO_TITLE = Regex("""\bv[ií]deos?\b""", RegexOption.IGNORE_CASE)
        val VIDEO_PLATFORM =Regex("""\b(Dailymotion|YouTube|Vimeo|TikTok|Twitch)\b""", RegexOption.IGNORE_CASE)
    }

    /** One accessibility snapshot, visible non-empty nodes only. */
    private suspend fun snapshotVisible(): List<AutomationNode> =
        primitives.host.snapshotTree(DumpFormat.FETCH_DEPTH)
            .map { it.node }
            .filter { it.visible && it.width > 0 && it.height > 0 }

    /**
     * Y band where a title can actually be tapped: below the sticky search
     * header and above the bottom nav. Falls back to screen fractions when
     * either anchor isn't on screen (e.g. Inicio not scrolled yet).
     */
    private fun feedBand(all: List<AutomationNode>): Pair<Int, Int> {
        val screenH = primitives.host.screenHeight
        val search = all.firstOrNull { n ->
            n.clickable && n.width >= 900 && n.boundsT < screenH * 0.3 &&
                n.contentDescription?.trim().equals("Buscar", ignoreCase = true)
        }
        val nav = all.firstOrNull { n ->
            n.clickable && n.boundsT > screenH * 0.8 &&
                (n.contentDescription?.trim().equals("Cámara", ignoreCase = true) ||
                    n.contentDescription?.trim().equals("Copilot", ignoreCase = true))
        }
        val top = (search?.boundsB ?: (screenH * 0.12).toInt()) + 25
        val bot = nav?.boundsT ?: (screenH * 0.89).toInt()
        return top to bot
    }

    /**
     * Enumerate feed cards. A card = clickable title View (see the class KDoc)
     * fully inside the tappable band. Returned sorted top-to-bottom.
     */
    private suspend fun findFeedCards(): List<FeedCard> {
        val all = snapshotVisible()
        val (bandTop, bandBot) = feedBand(all)

        val titles = all.filter { n ->
            n.clickable &&
                n.className?.substringAfterLast('.') == "View" &&
                n.boundsL in TITLE_LEFT &&
                n.width in TITLE_WIDTH &&
                n.height in TITLE_HEIGHT &&
                n.text.isNullOrBlank() &&
                !n.contentDescription.isNullOrBlank() &&
                n.boundsT >= bandTop && n.boundsB <= bandBot
        }.sortedBy { it.boundsT }

        return titles.map { t ->
            val desc = t.contentDescription!!.trim()
            FeedCard(t, desc, isAdCard(t, desc, all), isVideoCard(t, desc, all))
        }
    }

    /** Ad detection — signals listed in the class KDoc. */
    private fun isAdCard(title: AutomationNode, desc: String, all: List<AutomationNode>): Boolean =
        adSignals(title, desc, all).isNotEmpty()

    /** Names of the ad signals that fire for this title ("word", "chip", "image"); empty = not an ad. */
    private fun adSignals(title: AutomationNode, desc: String, all: List<AutomationNode>): List<String> = buildList {
        if (AD_WORD.containsMatchIn(desc)) add("word")

        val chipTop = title.boundsT - 20
        val chipBot = title.boundsB + 220
        if (all.any { n ->
                n !== title && n.boundsT in chipTop..chipBot &&
                    (n.text?.trim().equals("Patrocinado", ignoreCase = true) ||
                        n.contentDescription?.trim().equals("Patrocinado", ignoreCase = true) ||
                        n.contentDescription?.contains("Selección de anuncio", ignoreCase = true) == true)
            }
        ) add("chip")

        if (all.any { n ->
                n.viewId?.substringAfterLast('/')?.startsWith("nativead") == true &&
                    n.boundsB in (title.boundsT - 100)..(title.boundsT + 30)
            }
        ) add("image")
    }

    /**
     * Video detection — UNVERIFIED, there was no video card in the dump. Looks
     * at the title desc and at the card's image area (up to 780 px above the
     * title) for 'Reproducir' / a platform name / a small mm:ss badge.
     */
    private fun isVideoCard(title: AutomationNode, desc: String, all: List<AutomationNode>): Boolean {
        if (desc.contains("Reproducir", ignoreCase = true)) return true
        if (VIDEO_PLATFORM.containsMatchIn(desc) || VIDEO_TITLE.containsMatchIn(desc)) return true

        val areaTop = title.boundsB - 780
        val areaBot = title.boundsB + 120
        return all.any { n ->
            n !== title && n.boundsT in areaTop..areaBot &&
                n.boundsL >= title.boundsL - 20 && n.boundsR <= title.boundsR + 20 &&
                (n.contentDescription?.contains("Reproducir vídeo", ignoreCase = true) == true ||
                    (n.width < 400 && DURATION.containsMatchIn(n.text ?: "")))
        }
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
            // Run of 2026-11-21: 7 of 10 first taps timed out and the second one
            // "worked" ~1 s later. Either the first tap was swallowed (scroll
            // inertia) or the article just opened late. Re-check before tapping
            // again so a late open doesn't get a second tap inside the article,
            // and log which case it was.
            primitives.idle(1_200, 1_500)
            if (!onFeed()) {
                FarmerLog.d("  tap '$label' opened LATE (article up only after the extra wait)")
                return true
            }
            FarmerLog.d("  tap '$label' didn't navigate (still on feed after the extra wait) — retry")
        }
        return false
    }

    /**
     * Poll until the feed-card signature disappears from the current view,
     * meaning we've navigated off the feed into an article. Returns true
     * when navigated, false on timeout. 300 ms poll interval keeps the
     * cost low.
     */
    private suspend fun waitArticleOpen(timeoutMs: Long = 5_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        // Minimum settle time — the gesture needs at least a frame.
        primitives.idle(600, 900)
        while (System.currentTimeMillis() < deadline) {
            if (!onFeed()) return true
            primitives.idle(300, 450)
        }
        return false
    }

    /**
     * Motion test for an auto-playing feed video. Compares the card's image area
     * (≈ title.top-520 .. title.top-70, the thumbnail sits ~45 px above the title)
     * between screenshots ~0.5 s apart, twice. Still images change 0.00 % of the
     * sampled pixels; playing videos 6–96 % (2026-11-21 captures). The title row
     * is a control: if it changes too, the feed is still scrolling and that pair
     * is ignored.
     *
     * Returns true = playing video, false = static, null = can't tell (no screenshot
     * support/capability, image area mostly under the sticky header, or still scrolling).
     */
    /** Pixels of the card's thumbnail area (title.top-520 .. title.top-70) not hidden under the sticky header. */
    private suspend fun imageVisiblePx(card: FeedCard): Int {
        val t = card.tapTarget
        val (bandTop, _) = feedBand(snapshotVisible())
        return (t.boundsT - 70) - maxOf(t.boundsT - 520, bandTop)
    }

    private suspend fun looksLikePlayingVideo(card: FeedCard): Boolean? {
        val t = card.tapTarget
        val (bandTop, _) = feedBand(snapshotVisible())
        val imgTop = maxOf(t.boundsT - 520, bandTop)
        val imgBot = t.boundsT - 70
        if (imgBot - imgTop < MIN_IMAGE_PX) {
            FarmerLog.d("  videoCheck: only ${imgBot - imgTop} px of the image area is visible — unknown")
            return null
        }
        var prev = primitives.host.captureScreen() ?: run {
            FarmerLog.w("  videoCheck: no screenshot (API < 30, capability missing or refused) — using title heuristic")
            return null
        }
        // Two clean pairs (title row still) decide "static". A pair where the title row
        // moved means the feed is still scrolling (run 7, card #9: 10.8 % / 5.4 %), so
        // keep comparing — up to 4 pairs — instead of giving up with "unknown".
        var stablePairs = 0
        repeat(4) {
            primitives.idle(500, 600)
            val cur = primitives.host.captureScreen() ?: return null
            val img = FrameMotion.changedFraction(prev, cur, 90, imgTop, 990, imgBot) ?: return null
            val ctrl = FrameMotion.changedFraction(prev, cur, 60, t.boundsT, 1_020, t.boundsB) ?: 0.0
            FarmerLog.d("  videoCheck: image changed ${"%.1f".format(img * 100)}%, title row ${"%.1f".format(ctrl * 100)}%")
            if (ctrl <= VIDEO_CONTROL_MAX) {
                if (img >= VIDEO_MOTION_MIN) return true
                if (++stablePairs >= 2) return false
            }
            prev = cur
        }
        return if (stablePairs >= 1) false else null
    }

    /**
     * True while the Inicio search bar (clickable 'Buscar' Button, 900+ wide)
     * is on screen. ASSUMPTION, not yet seen in a dump: an opened article does
     * not show it — the R2E/ARTICLE dump confirms or refutes this.
     */
    private suspend fun onFeed(): Boolean =
        snapshotVisible().any { n ->
            n.clickable && n.width >= 900 &&
                n.contentDescription?.trim().equals("Buscar", ignoreCase = true)
        }
}
