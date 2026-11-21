package com.zero.rewards.core.runner

import com.zero.rewards.core.automation.AutomationHost
import com.zero.rewards.core.automation.AutomationNode
import com.zero.rewards.core.automation.TreeNode
import com.zero.rewards.core.log.FarmerLog
import kotlinx.coroutines.delay

/**
 * Structure-dump mode switches (HANDOFF REGLA CERO). While a flag is true the
 * matching phase only dumps the Bing UI to logcat and never farms. Flip back to
 * false (or delete the dump branch) once the log has been collected.
 */
object StructureDumpMode {
    // Both off after the 2026-11-21 dump: the real runners now use the dumped structure.
    val SWITCHER: Boolean = false
    val READ_TO_EARN: Boolean = false

    /** Diagnostic: snapshot the screen right after the switcher's "Cambiar ahora" tap and 3/6/10 s later. */
    val POST_COMMIT_TRACE: Boolean = false

    /** Diagnostic replacing read-to-earn: sweep the feed logging every card's ad/video signals and open "Video:" cards. */
    val READ_TO_EARN_VIDEO_HUNT: Boolean = false
}

/**
 * Read-only dumper of the Bing UI. Prints, per snapshot, the sections the
 * HANDOFF asks for: anchors, nodes by 40px Y row, CLICKABLE nodes, tile-shaped
 * nodes, text nodes, contentDescription nodes, and the full tree (depth ≤ 8)
 * chunked to 1000 chars per log line.
 *
 * Everything is emitted under the logcat tag `ZeroFarmer`, prefixed
 * `DUMP#<n> [<label>]`. The tree is large — capture logcat LIVE to a file,
 * the 256 KB ring buffer will overwrite it otherwise.
 */
class BingStructureDump(private val host: AutomationHost) {

    private var seq = 0

    suspend fun snapshot(
        label: String,
        anchors: List<String> = DEFAULT_ANCHORS,
        treeDepth: Int = DumpFormat.TREE_DEPTH,
    ) {
        dump(label, host.snapshotTree(DumpFormat.FETCH_DEPTH), anchors, treeDepth)
    }

    /**
     * Passive recording while the operator drives Bing by hand: polls the tree
     * and prints a full snapshot each time the screen settles (same signature
     * on two consecutive polls) on something not yet dumped. Returns the number
     * of snapshots taken.
     */
    suspend fun watch(
        label: String,
        durationMs: Long,
        leadInMs: Long = 5_000L,
        pollMs: Long = 1_500L,
        minGapMs: Long = 3_000L,
        maxSnapshots: Int = 25,
        anchors: List<String> = DEFAULT_ANCHORS,
    ): Int {
        FarmerLog.i("WATCH [$label] starts in ${leadInMs / 1000}s and lasts ${durationMs / 1000}s — navigate by hand; a dump is printed each time the screen settles on a different layout")
        // The caller has just dumped the current screen; don't dump it again.
        val seed = host.snapshotTree(DumpFormat.FETCH_DEPTH).takeIf { it.isNotEmpty() }?.let { DumpFormat.layoutKeys(it) }
        delay(leadInMs)
        FarmerLog.i("WATCH [$label] >>> GO")

        val endAt = System.currentTimeMillis() + durationMs
        var lastDumped: Set<String>? = seed
        var previousPoll: Set<String>? = null
        var lastDumpAt = 0L
        var taken = 0
        while (System.currentTimeMillis() < endAt && taken < maxSnapshots) {
            val tree = host.snapshotTree(DumpFormat.FETCH_DEPTH)
            if (tree.isNotEmpty()) {
                val keys = DumpFormat.layoutKeys(tree)
                val gapOk = System.currentTimeMillis() - lastDumpAt >= minGapMs
                if (gapOk && DumpFormat.shouldDump(lastDumped, previousPoll, keys)) {
                    dump("$label/watch", tree, anchors, DumpFormat.TREE_DEPTH)
                    lastDumped = keys
                    lastDumpAt = System.currentTimeMillis()
                    taken++
                }
                previousPoll = keys
            }
            delay(pollMs)
        }
        FarmerLog.i("WATCH [$label] <<< END snapshots=$taken")
        return taken
    }

    private suspend fun dump(label: String, tree: List<TreeNode>, anchors: List<String>, treeDepth: Int) {
        seq++
        val tag = "DUMP#$seq [$label]"
        val pkg = host.currentPackage()
        val sw = host.screenWidth
        val sh = host.screenHeight
        if (tree.isEmpty()) {
            FarmerLog.w("$tag pkg=$pkg — no accessibility root, nothing to dump")
            return
        }
        val nodes = tree.map { it.node }
        val visible = nodes.filter { it.visible && it.width > 0 && it.height > 0 }
        FarmerLog.i("$tag >>> pkg=$pkg screen=${sw}x$sh nodes=${nodes.size} visible=${visible.size} maxDepth=${tree.maxOf { it.depth }} t=${System.currentTimeMillis()}")

        emit("$tag ANCHORS", DumpFormat.anchorLines(visible, anchors))

        val rows = visible.groupBy { DumpFormat.rowBucket(it.boundsT) }.toSortedMap()
        emit("$tag ROWS(40px)", buildList {
            for ((y, group) in rows) {
                add("-- y=$y (${group.size})")
                group.sortedWith(compareBy({ it.boundsT }, { it.boundsL })).forEach { add("  " + DumpFormat.nodeLine(it)) }
            }
        })

        val clickables = visible.filter { it.clickable || it.longClickable }.sortedWith(compareBy({ it.boundsT }, { it.boundsL }))
        emit("$tag CLICKABLE(${clickables.size})", clickables.map { DumpFormat.nodeLine(it) })

        val tiles = visible.filter { DumpFormat.isTileShaped(it, sw) }.sortedBy { it.boundsT }
        emit("$tag TILE-SHAPED(${tiles.size}) w>=40%screen h=100..900", tiles.map { DumpFormat.nodeLine(it) })

        val texts = visible.filter { !it.text.isNullOrBlank() || !it.hintText.isNullOrBlank() }
            .sortedWith(compareBy({ it.boundsT }, { it.boundsL }))
        emit("$tag TEXT(${texts.size})", texts.map { DumpFormat.nodeLine(it) })

        val descs = visible.filter { !it.contentDescription.isNullOrBlank() }
            .sortedWith(compareBy({ it.boundsT }, { it.boundsL }))
        emit("$tag DESC(${descs.size})", descs.map { DumpFormat.nodeLine(it) })

        val treeLines = tree.filter { it.depth <= treeDepth }.map { t ->
            "  ".repeat(t.depth) + t.depth + ":" + DumpFormat.nodeLine(t.node, includeVisible = true)
        }
        emit("$tag TREE(depth<=$treeDepth, ${treeLines.size}/${tree.size})", treeLines)

        FarmerLog.i("$tag <<<")
    }

    /** Chunk [lines] to ≤1000 chars per logcat entry; the small delay dodges logd's chatty throttling. */
    private suspend fun emit(title: String, lines: List<String>) {
        if (lines.isEmpty()) {
            FarmerLog.d("$title (none)")
            return
        }
        val chunks = DumpFormat.chunkLines(lines, DumpFormat.CHUNK_CHARS)
        chunks.forEachIndexed { i, chunk ->
            FarmerLog.d("$title part ${i + 1}/${chunks.size}\n$chunk")
            delay(12)
        }
    }

    companion object {
        /** Known Bing strings (ES + EN) that locate the screen and the flows we're fixing. */
        val DEFAULT_ANCHORS = listOf(
            "Pregunta cualquier cosa", "Ask anything", "Inicio", "Home", "Buscar",
            "Perfil", "Profile", "Cuenta", "Account", "Avatar", "Cuentas",
            "Sesión iniciada como", "Cambiar a otra cuenta", "Administrar cuentas",
            "Cambiar cuenta", "Cambiar ahora", "Cerrar sesión", "Quitar cuenta",
            "Reenviar", "Atrás", "seleccionado", "Configuración",
            "Rewards", "Leer para ganar", "Para ti", "Noticias", "Anuncio", "Reproducir",
            "Patrocinado", "Selección de anuncio", "Compartir", "Ver más", "Más como este",
            "Cámara", "Copilot", "Pestañas",
            "Notificaciones", "Total points", "Settings", "Sign out", "Switch", "Add account",
            "Agregar cuenta", "Añadir cuenta", "Cambiar",
            "Intervalos diarios", "CONJUNTO DIARIO", "MÁS ACTIVIDADES", "Actividades diarias",
        )
    }
}

/** Pure formatting / decision helpers for [BingStructureDump] — no host, no logging. */
internal object DumpFormat {
    const val TREE_DEPTH = 8
    const val FETCH_DEPTH = 20
    const val CHUNK_CHARS = 1_000
    private const val TEXT_CAP = 120

    /** 40px Y bucket (floor, also for negative tops). */
    fun rowBucket(top: Int): Int = Math.floorDiv(top, 40) * 40

    fun isTileShaped(n: AutomationNode, screenW: Int): Boolean =
        n.visible && n.width >= screenW * 0.4 && n.height in 100..900

    /** Single-line, newline-free, length-capped rendering of free text. */
    fun esc(s: String): String {
        val one = s.replace("\r", " ").replace("\n", "⏎")
        return if (one.length > TEXT_CAP) one.take(TEXT_CAP) + "…(${one.length})" else one
    }

    fun nodeLine(n: AutomationNode, includeVisible: Boolean = false): String = buildString {
        append(n.className?.substringAfterLast('.') ?: "?")
        append(' ').append(n.width).append('x').append(n.height)
        append(" (").append(n.boundsL).append(',').append(n.boundsT).append(')')
        append(" c=").append(if (n.clickable) 1 else 0)
        if (n.longClickable) append(" lc=1")
        if (n.scrollable) append(" sc=1")
        if (n.editable) append(" ed=1")
        if (!n.enabled) append(" en=0")
        if (includeVisible && !n.visible) append(" vis=0")
        n.viewId?.let { append(" id=").append(it.substringAfterLast('/')) }
        n.text?.takeIf { it.isNotEmpty() }?.let { append(" t='").append(esc(it)).append('\'') }
        n.contentDescription?.takeIf { it.isNotEmpty() }?.let { append(" d='").append(esc(it)).append('\'') }
        n.hintText?.takeIf { it.isNotEmpty() }?.let { append(" h='").append(esc(it)).append('\'') }
    }

    /** One line per anchor that is on screen (up to 4 hits each), plus one line listing the absent ones. */
    fun anchorLines(visible: List<AutomationNode>, anchors: List<String>): List<String> {
        val out = mutableListOf<String>()
        val absent = mutableListOf<String>()
        for (a in anchors) {
            val hits = visible.filter { containsIgnoreCase(it, a) }.sortedBy { it.boundsT }
            if (hits.isEmpty()) { absent += a; continue }
            val shown = hits.take(4).joinToString(" | ") { h ->
                val exact = h.text?.trim().equals(a, true) || h.contentDescription?.trim().equals(a, true)
                (if (exact) "exact " else "substr ") +
                    "${h.className?.substringAfterLast('.')} y=${h.boundsT}..${h.boundsB} x=${h.boundsL}..${h.boundsR}"
            }
            out += "'$a' x${hits.size}: $shown"
        }
        if (absent.isNotEmpty()) out += "absent: " + absent.joinToString(", ") { "'$it'" }
        return out
    }

    private fun containsIgnoreCase(n: AutomationNode, needle: String): Boolean =
        n.text?.contains(needle, ignoreCase = true) == true ||
            n.contentDescription?.contains(needle, ignoreCase = true) == true ||
            n.hintText?.contains(needle, ignoreCase = true) == true

    /** Greedy-pack whole lines into chunks of ≤ [maxChars]; a longer single line is split. */
    fun chunkLines(lines: List<String>, maxChars: Int): List<String> {
        require(maxChars > 0)
        val chunks = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { chunks += cur.toString(); cur.setLength(0) } }
        for (line in lines) {
            val pieces = if (line.length <= maxChars) listOf(line) else line.chunked(maxChars)
            for (p in pieces) {
                val extra = if (cur.isEmpty()) p.length else p.length + 1
                if (cur.length + extra > maxChars) flush()
                if (cur.isNotEmpty()) cur.append('\n')
                cur.append(p)
            }
        }
        flush()
        return chunks
    }

    /** Same layout (not text) at ≥ this similarity between two polls = the screen has settled. */
    const val SETTLED_SIMILARITY = 0.9

    /** Layout at < this similarity to the last dumped one = a different screen worth dumping. */
    const val NEW_SCREEN_SIMILARITY = 0.8

    /**
     * Identity of what is on screen: one key per visible node from class,
     * contentDescription and bounds. Text is excluded on purpose — rotating
     * headlines (the Tendencias chip, the news widget) change it every few
     * seconds on an otherwise identical Inicio.
     */
    fun layoutKeys(tree: List<TreeNode>): Set<String> =
        tree.asSequence().map { it.node }
            .filter { it.visible && it.width > 0 && it.height > 0 }
            .map { "${it.className}|${it.contentDescription}|${it.boundsL},${it.boundsT},${it.boundsR},${it.boundsB}" }
            .toSet()

    /** Jaccard similarity of two key sets; two empty sets are identical. */
    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    /** Dump when the layout has settled (stable across two polls) and differs enough from the last dumped one. */
    fun shouldDump(lastDumped: Set<String>?, previousPoll: Set<String>?, current: Set<String>): Boolean {
        if (previousPoll == null || similarity(previousPoll, current) < SETTLED_SIMILARITY) return false
        return lastDumped == null || similarity(lastDumped, current) < NEW_SCREEN_SIMILARITY
    }
}
