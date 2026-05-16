package com.pokercoach.core.eval

import com.pokercoach.core.model.Card
import com.pokercoach.core.model.Rank

/**
 * 7 張牌（2 hole + 5 board）取最佳 5 張的撲克牌型評分器。
 *
 * 評分使用 32-bit 整數："類別 (4-bit) | rank1..rank5 (各 4-bit)"，
 * 數值越大代表牌型越強，可直接 `compareTo` 比較任意兩手。
 *
 * 類別碼（高至低）：
 *   8 STRAIGHT_FLUSH, 7 FOUR_OF_A_KIND, 6 FULL_HOUSE, 5 FLUSH,
 *   4 STRAIGHT, 3 THREE_OF_A_KIND, 2 TWO_PAIR, 1 ONE_PAIR, 0 HIGH_CARD
 */
object HandEvaluator {

    enum class Category(val code: Int, val displayName: String) {
        HIGH_CARD(0, "High Card"),
        ONE_PAIR(1, "One Pair"),
        TWO_PAIR(2, "Two Pair"),
        THREE_OF_A_KIND(3, "Three of a Kind"),
        STRAIGHT(4, "Straight"),
        FLUSH(5, "Flush"),
        FULL_HOUSE(6, "Full House"),
        FOUR_OF_A_KIND(7, "Four of a Kind"),
        STRAIGHT_FLUSH(8, "Straight Flush");

        companion object {
            fun fromCode(c: Int): Category = values().first { it.code == c }
        }
    }

    data class Score(val value: Int, val category: Category, val kickers: List<Int>) :
        Comparable<Score> {
        override fun compareTo(other: Score): Int = value.compareTo(other.value)
        override fun toString(): String =
            "${category.displayName} [${kickers.joinToString(",")}] (=$value)"
    }

    /** 主入口：給任意 5..7 張，回傳最佳 5 張的分數。 */
    fun evaluate(cards: List<Card>): Score {
        require(cards.size in 5..7) { "Need 5..7 cards, got ${cards.size}" }
        require(cards.toSet().size == cards.size) { "Duplicate cards in $cards" }

        // 列舉所有 C(n,5) 組合（n<=7，最多 21 種），取最高分。
        var best: Score? = null
        for (combo in choose5(cards)) {
            val s = score5(combo)
            if (best == null || s > best) best = s
        }
        return best!!
    }

    // ---------------------------------------------------------------------
    // 5 張評分
    // ---------------------------------------------------------------------
    private fun score5(cards: List<Card>): Score {
        require(cards.size == 5)
        val ranks = cards.map { it.rank.value }.sortedDescending()
        val suits = cards.map { it.suit }
        val isFlush = suits.toSet().size == 1
        val straightHigh = straightHighRank(ranks)
        val isStraight = straightHigh != null

        // 統計每個 rank 的次數 → 拆成 (count, rank)，count 高的優先排，count 同則 rank 高的優先
        val counts: List<Pair<Int, Int>> = ranks.groupingBy { it }.eachCount()
            .map { it.value to it.key }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

        return when {
            isFlush && isStraight ->
                pack(Category.STRAIGHT_FLUSH, listOf(straightHigh!!))
            counts[0].first == 4 -> {
                val quadRank = counts[0].second
                val kicker = counts[1].second
                pack(Category.FOUR_OF_A_KIND, listOf(quadRank, kicker))
            }
            counts[0].first == 3 && counts[1].first == 2 ->
                pack(Category.FULL_HOUSE, listOf(counts[0].second, counts[1].second))
            isFlush ->
                pack(Category.FLUSH, ranks)                          // 同花高張順序
            isStraight ->
                pack(Category.STRAIGHT, listOf(straightHigh!!))
            counts[0].first == 3 -> {
                val tripRank = counts[0].second
                val kickers = ranks.filter { it != tripRank }.take(2)
                pack(Category.THREE_OF_A_KIND, listOf(tripRank) + kickers)
            }
            counts[0].first == 2 && counts[1].first == 2 -> {
                val hi = maxOf(counts[0].second, counts[1].second)
                val lo = minOf(counts[0].second, counts[1].second)
                val kicker = ranks.first { it != hi && it != lo }
                pack(Category.TWO_PAIR, listOf(hi, lo, kicker))
            }
            counts[0].first == 2 -> {
                val pair = counts[0].second
                val kickers = ranks.filter { it != pair }.take(3)
                pack(Category.ONE_PAIR, listOf(pair) + kickers)
            }
            else -> pack(Category.HIGH_CARD, ranks)
        }
    }

    /** 順子最高張；無順子回傳 null。處理輪子（A2345）→ high = 5。 */
    private fun straightHighRank(sortedDescRanks: List<Int>): Int? {
        val uniq = sortedDescRanks.distinct()
        if (uniq.size < 5) return null

        // 一般情況：找連續 5 張
        for (i in 0..(uniq.size - 5)) {
            if (uniq[i] - uniq[i + 4] == 4) return uniq[i]
        }
        // 輪子：A,5,4,3,2 → high = 5
        if (uniq.containsAll(listOf(Rank.ACE.value, 5, 4, 3, 2))) return 5
        return null
    }

    /** 將類別 + kickers 編碼為單一整數。 */
    private fun pack(cat: Category, kickers: List<Int>): Score {
        // kickers 補到 5 個（用 0 補位），保證可比較
        val k = (kickers + List(5 - kickers.size) { 0 }).take(5)
        var v = cat.code
        for (rk in k) v = (v shl 4) or (rk and 0xF)
        return Score(v, cat, k)
    }

    // ---------------------------------------------------------------------
    // 組合工具：從 list 取 5 個
    // ---------------------------------------------------------------------
    private fun <T> choose5(items: List<T>): Sequence<List<T>> = sequence {
        val n = items.size
        if (n == 5) { yield(items); return@sequence }
        val idx = IntArray(5) { it }
        while (true) {
            yield(idx.map { items[it] })
            // 找最後一個可遞增的位置
            var i = 4
            while (i >= 0 && idx[i] == n - 5 + i) i--
            if (i < 0) break
            idx[i]++
            for (j in i + 1 until 5) idx[j] = idx[j - 1] + 1
        }
    }
}
