package com.pokercoach.core.model

/**
 * 翻前 169 種「起手牌類別」中的一格（例如 AKs, AKo, 77）。
 * - pair: 對子（如 77），highRank == lowRank
 * - suited: 同花，highRank > lowRank
 * - offsuit: 不同花，highRank > lowRank
 *
 * 不變式：highRank.value >= lowRank.value；pair => 兩者相等。
 */
data class HandClass(
    val highRank: Rank,
    val lowRank: Rank,
    val suited: Boolean
) {
    init {
        require(highRank.value >= lowRank.value) {
            "highRank must be >= lowRank ($highRank vs $lowRank)"
        }
        if (highRank == lowRank) {
            require(!suited) { "Pair cannot be 'suited' (${highRank.shorthand}${lowRank.shorthand})" }
        }
    }

    val isPair: Boolean get() = highRank == lowRank

    /** 標準縮寫："AKs" / "AKo" / "77"。 */
    val shorthand: String = when {
        isPair -> "${highRank.shorthand}${lowRank.shorthand}"
        suited -> "${highRank.shorthand}${lowRank.shorthand}s"
        else -> "${highRank.shorthand}${lowRank.shorthand}o"
    }

    /**
     * 此類別實際對應的底牌組合數：
     * - 對子：C(4,2) = 6
     * - 同花：4
     * - 不同花：12
     */
    val combos: Int get() = when {
        isPair -> 6
        suited -> 4
        else -> 12
    }

    override fun toString(): String = shorthand

    companion object {
        /**
         * 從兩張具體底牌得出對應的 169 種類別。
         */
        fun from(c1: Card, c2: Card): HandClass {
            require(c1 != c2) { "Hole cards must differ: $c1, $c2" }
            val (hi, lo) = if (c1.rank.value >= c2.rank.value) c1 to c2 else c2 to c1
            return if (hi.rank == lo.rank) {
                HandClass(hi.rank, lo.rank, suited = false)
            } else {
                HandClass(hi.rank, lo.rank, suited = hi.suit == lo.suit)
            }
        }

        /** 解析 "AKs"、"AKo"、"77"。 */
        fun parse(s: String): HandClass {
            require(s.length in 2..3) { "Invalid hand class string: '$s'" }
            val hi = Rank.fromChar(s[0])
            val lo = Rank.fromChar(s[1])
            return if (hi == lo) {
                require(s.length == 2) { "Pair has no s/o suffix: '$s'" }
                HandClass(hi, lo, false)
            } else {
                require(s.length == 3) { "Non-pair needs s/o suffix: '$s'" }
                val suited = when (s[2].lowercaseChar()) {
                    's' -> true
                    'o' -> false
                    else -> throw IllegalArgumentException("Bad suffix in '$s'")
                }
                val (high, low) = if (hi.value >= lo.value) hi to lo else lo to hi
                HandClass(high, low, suited)
            }
        }

        /** 完整 169 種起手牌（順序：對子 + 同花/不同花的所有組合）。 */
        fun all169(): List<HandClass> = buildList(169) {
            val ranks = Rank.DESCENDING
            for (i in ranks.indices) {
                for (j in ranks.indices) {
                    val r1 = ranks[i]; val r2 = ranks[j]
                    when {
                        i == j -> add(HandClass(r1, r2, false))            // pair
                        i < j  -> add(HandClass(r1, r2, true))             // suited (上三角)
                        else   -> add(HandClass(r2, r1, false))            // offsuit (下三角)
                    }
                }
            }
        }
    }
}

/**
 * 玩家在某一手中實際拿到的兩張底牌。
 */
data class HoleCards(val first: Card, val second: Card) {
    init {
        require(first != second) { "Hole cards must differ: $first, $second" }
    }

    val handClass: HandClass by lazy { HandClass.from(first, second) }

    override fun toString(): String = "$first$second"
}
