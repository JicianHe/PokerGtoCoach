package com.pokercoach.core.model

/**
 * 玩家行動類型。RAISE 含加注額度（含 ALL_IN 視為一種 RAISE 大小）。
 *
 * 用 sealed class 是為了讓 RAISE 攜帶大小，而 FOLD/CHECK/CALL 不需要任何參數，
 * 可同時保留 enum 風格的窮舉檢查（when 分支必須覆蓋全部子類）。
 */
sealed class Action {
    object Fold  : Action() { override fun toString() = "FOLD" }
    object Check : Action() { override fun toString() = "CHECK" }
    object Call  : Action() { override fun toString() = "CALL" }

    /**
     * RAISE：amount 是此次「加注到」的總額（to-amount，非增量），單位為大盲（bb）。
     * 例如翻前開池 2.5bb，amount = 2.5。
     */
    data class Raise(val amount: Double) : Action() {
        init { require(amount > 0.0) { "Raise amount must be positive: $amount" } }
        override fun toString(): String = "RAISE ${"%.2f".format(amount)}bb"
    }

    /** 行動類別標籤（不含金額），用於統計 / 範圍頻率索引。 */
    enum class Kind { FOLD, CHECK, CALL, RAISE }

    val kind: Kind
        get() = when (this) {
            is Fold  -> Kind.FOLD
            is Check -> Kind.CHECK
            is Call  -> Kind.CALL
            is Raise -> Kind.RAISE
        }
}
