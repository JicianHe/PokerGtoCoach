package com.pokercoach.core.range

import com.pokercoach.core.model.Position

/**
 * 翻前範圍情境鍵：辨識「英雄位置 + 面對的局面」。
 *
 * Phase 1 內建兩個情境（足以演示與支援 BTN vs BB 對局：）
 *   1) BTN RFI（BTN 率先開池，前面全部 fold）
 *   2) BB vs BTN_RFI（BB 面對 BTN 開池 2.5bb 的防守）
 *
 * 後續階段會擴充 3-bet / 4-bet / squeeze / cold-call 等情境。
 */
sealed class RangeScenario {

    /** 英雄座位（用於範圍歸屬與顯示）。 */
    abstract val hero: Position

    /** Range 名稱（用於 UI 標題、log）。 */
    abstract val displayName: String

    /** 率先開池（Raise-First-In）。前手全部 fold。 */
    data class Rfi(override val hero: Position) : RangeScenario() {
        override val displayName: String = "${hero.displayName} RFI"
    }

    /**
     * 面對單一開池者，於大盲位置防守（含 Call / 3-Bet / Fold）。
     * sizeBb 表示對手開池到多少 bb（典型 2.0 ~ 2.5）。
     */
    data class BbVsRfi(val raiser: Position, val sizeBb: Double = 2.5) : RangeScenario() {
        override val hero: Position = Position.BB
        override val displayName: String = "BB vs ${raiser.displayName} RFI ${sizeBb}bb"
    }
}
