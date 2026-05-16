package com.pokercoach.core.range

import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Rank

/**
 * 翻前混合策略：對於某一手起手牌，行動選項的機率分佈。
 *
 * 不變式：所有頻率 >= 0；total ≈ 1.0（允許 1e-6 浮點誤差）。
 *
 * 注意：
 *  - CHECK 在 BB 面對「未加注、僅 limp 或可過牌」場景才有意義；
 *    在「面對 RFI」場景 CHECK = 0。
 *  - FOLD + CALL + RAISE + CHECK = 1.0
 */
data class MixedStrategy(
    val fold: Double = 0.0,
    val check: Double = 0.0,
    val call: Double = 0.0,
    val raise: Double = 0.0
) {
    init {
        require(fold  in 0.0..1.0) { "fold out of range: $fold" }
        require(check in 0.0..1.0) { "check out of range: $check" }
        require(call  in 0.0..1.0) { "call out of range: $call" }
        require(raise in 0.0..1.0) { "raise out of range: $raise" }
        val s = fold + check + call + raise
        require(kotlin.math.abs(s - 1.0) < 1e-6) {
            "MixedStrategy must sum to 1.0, got $s (f=$fold c=$check ca=$call r=$raise)"
        }
    }

    /** 取得指定行動類別的頻率（0..1）。 */
    fun frequencyOf(kind: Action.Kind): Double = when (kind) {
        Action.Kind.FOLD  -> fold
        Action.Kind.CHECK -> check
        Action.Kind.CALL  -> call
        Action.Kind.RAISE -> raise
    }

    /** 最高頻率的「純策略」推薦行動。 */
    fun dominantAction(): Action.Kind {
        var best = Action.Kind.FOLD; var bestF = -1.0
        for (k in Action.Kind.values()) {
            val f = frequencyOf(k)
            if (f > bestF) { bestF = f; best = k }
        }
        return best
    }

    /** 是否為混合決策（沒有任何選項頻率 >= 0.95）。 */
    fun isMixed(threshold: Double = 0.95): Boolean =
        fold < threshold && check < threshold && call < threshold && raise < threshold

    companion object {
        val PURE_FOLD  = MixedStrategy(fold = 1.0)
        val PURE_CALL  = MixedStrategy(call = 1.0)
        val PURE_RAISE = MixedStrategy(raise = 1.0)
        val PURE_CHECK = MixedStrategy(check = 1.0)
    }
}

/**
 * 13x13 翻前手牌矩陣（行 = 高張，列 = 低張，由 A→2 排序）。
 *
 * 慣例（與所有現代 GTO 求解器顯示一致）：
 *   row == col           → 對子（對角線）
 *   row <  col           → 同花（右上三角）
 *   row >  col           → 不同花（左下三角）
 *
 * 例如：row=A(0), col=K(1) → AKs；row=K(1), col=A(0) → AKo。
 */
class HandMatrix private constructor(
    private val cells: Array<Array<MixedStrategy>>
) {
    fun get(row: Int, col: Int): MixedStrategy = cells[row][col]

    fun get(handClass: HandClass): MixedStrategy {
        val (r, c) = indexOf(handClass)
        return cells[r][c]
    }

    /** 矩陣是否含有任何非零頻率的 RAISE / CALL（用於檢查是否屬於空範圍）。 */
    fun isEmpty(): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val s = cells[r][c]
            if (s.call > 0.0 || s.raise > 0.0 || s.check > 0.0) return false
        }
        return true
    }

    /**
     * 對所有 169 手做「組合數加權」後，計算每個行動類別的整體範圍頻率。
     * 回傳百分比和為 100.0（允許微小誤差）。
     */
    fun overallFrequencies(): Map<Action.Kind, Double> {
        var totalCombos = 0.0
        val acc = DoubleArray(Action.Kind.values().size)
        for (hc in HandClass.all169()) {
            val s = get(hc)
            val w = hc.combos.toDouble()
            totalCombos += w
            acc[Action.Kind.FOLD.ordinal]  += s.fold  * w
            acc[Action.Kind.CHECK.ordinal] += s.check * w
            acc[Action.Kind.CALL.ordinal]  += s.call  * w
            acc[Action.Kind.RAISE.ordinal] += s.raise * w
        }
        return Action.Kind.values().associateWith { (acc[it.ordinal] / totalCombos) * 100.0 }
    }

    companion object {
        const val SIZE = 13

        /** 由 169 手 → 矩陣 (row, col)。 */
        fun indexOf(hc: HandClass): Pair<Int, Int> {
            val ranks = Rank.DESCENDING
            val hiIdx = ranks.indexOf(hc.highRank)
            val loIdx = ranks.indexOf(hc.lowRank)
            return when {
                hc.isPair -> hiIdx to hiIdx
                hc.suited -> hiIdx to loIdx                // 上三角
                else      -> loIdx to hiIdx                // 下三角（交換）
            }
        }

        /** 由 (row, col) → HandClass。 */
        fun handClassAt(row: Int, col: Int): HandClass {
            val ranks = Rank.DESCENDING
            val a = ranks[row]; val b = ranks[col]
            return when {
                row == col -> HandClass(a, a, false)
                row <  col -> HandClass(a, b, true)        // 高張在 row，低張在 col
                else       -> HandClass(b, a, false)       // row 是低張 → 高張為 col
            }
        }

        /** 全 FOLD 的空矩陣，用作 builder 起點。 */
        fun allFold(): HandMatrix {
            val arr = Array(SIZE) { Array(SIZE) { MixedStrategy.PURE_FOLD } }
            return HandMatrix(arr)
        }

        /** 由 (HandClass → MixedStrategy) 對照表構造，未列出的手為 PURE_FOLD。 */
        fun fromMap(map: Map<HandClass, MixedStrategy>): HandMatrix {
            val arr = Array(SIZE) { Array(SIZE) { MixedStrategy.PURE_FOLD } }
            for ((hc, ms) in map) {
                val (r, c) = indexOf(hc)
                arr[r][c] = ms
            }
            return HandMatrix(arr)
        }
    }
}
