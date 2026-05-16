package com.pokercoach.core.model

/**
 * 撲克花色。order 用於穩定排序 / 唯一鍵生成。
 */
enum class Suit(val symbol: String, val order: Int) {
    SPADES("✈", 0),     // 飛機（Plane）
    HEARTS("❤", 1),     // 愛心（Heart）
    DIAMONDS("★", 2),   // 星星（Star）
    CLUBS("☁", 3);      // 雲朵（Cloud）

    companion object {
        fun fromChar(c: Char): Suit = when (c.lowercaseChar()) {
            's' -> SPADES
            'h' -> HEARTS
            'd' -> DIAMONDS
            'c' -> CLUBS
            else -> throw IllegalArgumentException("Unknown suit char: $c")
        }
    }
}

/**
 * 點數：2..A，rank 值用於大小比較（2 = 2, A = 14）。
 * shorthand 用於 13x13 矩陣顯示與字串解析（例如 "AKs"）。
 */
enum class Rank(val value: Int, val shorthand: Char) {
    TWO(2, '2'),
    THREE(3, '3'),
    FOUR(4, '4'),
    FIVE(5, '5'),
    SIX(6, '6'),
    SEVEN(7, '7'),
    EIGHT(8, '8'),
    NINE(9, '9'),
    TEN(10, 'T'),
    JACK(11, 'J'),
    QUEEN(12, 'Q'),
    KING(13, 'K'),
    ACE(14, 'A');

    companion object {
        private val byChar: Map<Char, Rank> = values().associateBy { it.shorthand }

        fun fromChar(c: Char): Rank =
            byChar[c.uppercaseChar()]
                ?: throw IllegalArgumentException("Unknown rank char: $c")

        /** 由高到低排序（A 在最前），用於矩陣行/列索引。 */
        val DESCENDING: List<Rank> = values().sortedByDescending { it.value }
    }
}

/**
 * 一張具體的牌。equals/hashCode 自動由 data class 提供。
 *
 * 用 "As", "Td", "2c" 這樣的字串可解析。
 */
data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {

    /** 唯一索引 0..51，用於快速去重 / Bitmap deck 表示。 */
    val id: Int get() = (rank.value - 2) * 4 + suit.order

    override fun toString(): String = "${rank.shorthand}${suit.symbol}"

    fun toShortString(): String = "${rank.shorthand}${"shdc"[suit.order]}"

    override fun compareTo(other: Card): Int =
        compareValuesBy(this, other, { it.rank.value }, { it.suit.order })

    companion object {
        fun parse(s: String): Card {
            require(s.length == 2) { "Card string must be length 2, got '$s'" }
            return Card(Rank.fromChar(s[0]), Suit.fromChar(s[1]))
        }

        /** 完整 52 張牌組。 */
        fun fullDeck(): List<Card> = buildList(52) {
            for (r in Rank.values()) for (s in Suit.values()) add(Card(r, s))
        }
    }
}
