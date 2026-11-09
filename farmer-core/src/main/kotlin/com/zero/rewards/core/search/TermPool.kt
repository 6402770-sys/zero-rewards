package com.zero.rewards.core.search

import kotlin.random.Random

interface TermSource {
    suspend fun next(seen: Set<String>): String?
}

class TermPool(private val sources: List<TermSource>) {
    private val delivered = mutableSetOf<String>()

    suspend fun take(count: Int): List<String> {
        if (sources.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val rng = Random(System.nanoTime())
        val shuffled = sources.shuffled(rng)
        var sourceIdx = 0
        var guard = 0
        while (out.size < count && guard < count * 8) {
            val src = shuffled[sourceIdx % shuffled.size]
            val term = src.next(delivered)
            if (term != null && delivered.add(term)) out += term
            sourceIdx++
            guard++
        }
        return out
    }
}

class StaticTermSource(private val terms: List<String>) : TermSource {
    private val rng = Random(System.nanoTime())
    override suspend fun next(seen: Set<String>): String? {
        val available = terms.filterNot { it in seen }
        return if (available.isEmpty()) null else available.random(rng)
    }
}
