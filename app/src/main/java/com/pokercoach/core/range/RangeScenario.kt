package com.pokercoach.core.range

import com.pokercoach.core.model.Position

/**
 * 翻前範圍情境鍵：辨識「英雄位置 + 面對的局面」。
 *
 * 內建情境（擴充版）：
 *   1) Rfi(pos)           — 率先開池
 *   2) BbVsRfi(raiser, sz)— BB 面對某位置開池
 *   3) SbVsRfi(raiser, sz)— SB 面對某位置開池
 */
sealed class RangeScenario {

    abstract val hero: Position
    abstract val displayName: String

    data class Rfi(override val hero: Position) : RangeScenario() {
        override val displayName: String = "${hero.displayName} RFI"
    }

    data class BbVsRfi(val raiser: Position, val sizeBb: Double = 2.5) : RangeScenario() {
        override val hero: Position = Position.BB
        override val displayName: String =
            "BB vs ${raiser.displayName} ${"%.1f".format(sizeBb)}bb"
    }

    data class SbVsRfi(val raiser: Position, val sizeBb: Double = 2.5) : RangeScenario() {
        override val hero: Position = Position.SB
        override val displayName: String =
            "SB vs ${raiser.displayName} ${"%.1f".format(sizeBb)}bb"
    }

    /** 中文顯示名稱（UI 用）。 */
    val displayNameZh: String get() = when (this) {
        is Rfi -> "${hero.displayName} 率先開池"
        is BbVsRfi -> "BB 防守 ${raiser.displayName}（${"%.1f".format(sizeBb)}bb）"
        is SbVsRfi -> "SB 防守 ${raiser.displayName}（${"%.1f".format(sizeBb)}bb）"
    }
}
