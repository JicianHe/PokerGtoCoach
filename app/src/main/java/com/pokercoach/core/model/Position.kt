package com.pokercoach.core.model

/**
 * 6-Max 桌位置。order 為翻前行動順序（UTG 最早，BB 最後）。
 *
 * 翻後行動順序則由 SB → BB → UTG → ... 決定，不在此列舉內處理。
 */
enum class Position(val displayName: String, val order: Int, val isBlind: Boolean) {
    UTG("UTG", 0, false),
    HJ ("HJ",  1, false),
    CO ("CO",  2, false),
    BTN("BTN", 3, false),
    SB ("SB",  4, true),
    BB ("BB",  5, true);

    companion object {
        /** 翻前行動序列。 */
        val PREFLOP_ORDER: List<Position> = values().sortedBy { it.order }
    }
}
