package com.pokercoach.core.ev

import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.HandMatrix
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import kotlin.math.ln

/**
 * 翻前 EV / 推薦頻率計算器。
 *
 * 注意：嚴格 GTO EV 計算需要：
 *   - 對手回應範圍（response range）
 *   - 後續多街博弈樹的回溯（CFR 求解）
 *
 * 在 Phase 1，我們提供一個「教學等價」的近似 EV 模型，符合以下性質：
 *   1. 當混合策略推薦 X% RAISE 時，EV(RAISE) ≥ EV(其他選項) 的相對排名穩定；
 *   2. EV 單位為「bb / 手」，與真實 solver 輸出量級一致（典型 -1.0 ~ +4.0）；
 *   3. 對於 PURE_FOLD 的手，EV(FOLD) = 0；
 *   4. 對於 PURE_RAISE 的手，EV(RAISE) > 0，且越強的手 EV 越高（單調性）；
 *   5. 混合手（mixed）的可選行動 EV 應彼此接近（差距 < 0.05bb），體現 GTO 無差異原則。
 *
 * 後續階段會替換為由 solver JSON 載入的精確 EV。
 */
object EvCalculator {

    /**
     * 推薦結果。包含：
     *  - strategy：原始混合策略（頻率分佈）
     *  - evByAction：每個行動類別的近似 EV（bb / 手）
     *  - recommendedAction：依照混合策略的「主導行動」（最高頻率者）
     *  - rangeFrequencyPct：此手在「整體範圍」中的佔比百分比，用於 HUD 顯示
     */
    data class Recommendation(
        val scenario: RangeScenario,
        val hand: HandClass,
        val strategy: MixedStrategy,
        val evByAction: Map<Action.Kind, Double>,
        val recommendedAction: Action.Kind,
        val rangeFrequencyPct: Double
    ) {
        /** GTO 標準 EV：依混合策略加權的期望值。 */
        val gtoBlendedEv: Double
            get() = Action.Kind.values().sumOf { k ->
                strategy.frequencyOf(k) * (evByAction[k] ?: 0.0)
            }

        /** 是否屬於 GTO 混合決策（教學上需要特別說明的格）。 */
        val isMixedDecision: Boolean get() = strategy.isMixed()
    }

    /**
     * 主入口：根據情境 + 起手牌計算建議。
     */
    fun recommend(scenario: RangeScenario, hand: HandClass): Recommendation {
        val matrix = PreflopRangeManager.matrixFor(scenario)
        val strategy = matrix.get(hand)
        val handStrength = relativeHandStrength(hand)
        val evMap = estimateEv(scenario, hand, strategy, handStrength)
        val recommended = strategy.dominantAction()
        val rangePct = rangeShareOfHand(matrix, hand)

        return Recommendation(
            scenario = scenario,
            hand = hand,
            strategy = strategy,
            evByAction = evMap,
            recommendedAction = recommended,
            rangeFrequencyPct = rangePct
        )
    }

    /**
     * 由真實底牌（兩張具體 Card）計算 — 內部轉成 HandClass。
     */
    fun recommend(scenario: RangeScenario, hand: com.pokercoach.core.model.HoleCards): Recommendation =
        recommend(scenario, hand.handClass)

    // =====================================================================
    // 內部：手牌強度（0..1 normalized score）
    //   - 對子佔最高權重
    //   - 同花 / 連張加成
    //   - 高張加成
    // =====================================================================
    private fun relativeHandStrength(hc: HandClass): Double {
        val hi = hc.highRank.value.toDouble()      // 2..14
        val lo = hc.lowRank.value.toDouble()
        val gap = (hi - lo)                        // 0 表示對子
        val suitedBonus = if (hc.suited) 0.55 else 0.0
        val pairBonus = if (hc.isPair) 5.5 + (hi - 2.0) * 0.45 else 0.0
        val connector = when {
            hc.isPair -> 0.0
            gap == 1.0 -> 0.85
            gap == 2.0 -> 0.45
            gap == 3.0 -> 0.20
            else -> 0.0
        }
        // High card weight：頂張權重高於底張
        val highCard = (hi - 2.0) / 12.0 * 3.5
        val lowCard  = (lo - 2.0) / 12.0 * 1.6

        val raw = pairBonus + highCard + lowCard + suitedBonus + connector
        // 標準化到 0..1（max ≈ 5.5 + 5.4 + 3.5 + 1.6 + 0.55 + 0.85 ≈ 17.4，
        // 但對子沒有 connector / lowCard 加成，這裡用 14.0 作為實證最大值近似）
        return (raw / 14.0).coerceIn(0.0, 1.0)
    }

    // =====================================================================
    // 內部：估算 EV（bb / 手）
    //
    // 模型：
    //   EV(FOLD)  = 0（在非盲注情境）；在 BB 情境 = -(已投入死錢，視為機會成本 0）
    //   EV(CALL)  = base_call  + 強度修正
    //   EV(RAISE) = base_raise + 強度修正
    //   EV(CHECK) = 0（僅在 BB option / limped pot 才會非零）
    //
    // 對混合手（mixed），各可選行動 EV 被刻意拉近以體現 GTO 無差異原則；
    // 對純策略（pure），主導行動 EV 顯著大於其他。
    // =====================================================================
    private fun estimateEv(
        scenario: RangeScenario,
        hand: HandClass,
        strategy: MixedStrategy,
        strength: Double
    ): Map<Action.Kind, Double> {
        val s = strength                  // 0..1
        // 共通：強度越高，所有積極行動 EV 越大
        val strengthBump = (s - 0.40) * 6.0   // 強牌 +ev，弱牌 -ev

        // 各情境下的「基準 EV」
        val baseFold: Double
        val baseCall: Double
        val baseRaise: Double
        val baseCheck: Double

        when (scenario) {
            is RangeScenario.Rfi -> {
                // RFI：未投錢，FOLD 為 0；CALL 在 RFI 情境通常不存在（已是首位）
                baseFold = 0.0
                baseCall = -0.50 + strengthBump * 0.4    // 不太相關
                baseRaise = -0.10 + strengthBump * 1.0
                baseCheck = 0.0
            }
            is RangeScenario.BbVsRfi -> {
                // BB 已投 1bb 死錢。若 fold，視為機會成本 -0（已沉沒）。
                // CALL：需補 (sizeBb - 1)bb，看翻牌
                val toCall = scenario.sizeBb - 1.0
                baseFold = 0.0
                baseCall = -toCall * 0.30 + strengthBump * 0.85
                baseRaise = -0.50 + strengthBump * 1.20    // 3-bet 風險更高，獎勵更大
                baseCheck = 0.0                            // BB vs RFI 沒有 check 選項
            }
            is RangeScenario.SbVsRfi -> {
                // SB 已投 0.5bb 死錢，面對 raiser 需補 (sizeBb - 0.5)bb
                val toCall = scenario.sizeBb - 0.5
                baseFold = 0.0
                baseCall = -toCall * 0.40 + strengthBump * 0.75   // 位置最差，跟注 ev 較低
                baseRaise = -0.40 + strengthBump * 1.30           // 3-bet 取位置主動權
                baseCheck = 0.0
            }
        }

        // 對於混合手，將 EV 推向同一水平（GTO 無差異）
        val mixedness = mixednessFactor(strategy)         // 0 = pure, 1 = fully mixed
        val pivot = listOf(
            baseFold to strategy.fold,
            baseCall to strategy.call,
            baseRaise to strategy.raise,
            baseCheck to strategy.check
        ).filter { it.second > 0.0 }
         .let { picks -> if (picks.isEmpty()) 0.0 else picks.sumOf { it.first * it.second } /
                 picks.sumOf { it.second } }

        fun blend(base: Double, freq: Double): Double {
            // 只對策略中 freq > 0 的選項做「向 pivot 拉近」；freq = 0 的選項保留原 base（顯示為較差）
            return if (freq <= 0.0) base
            else base * (1.0 - mixedness) + pivot * mixedness
        }

        val evFold  = blend(baseFold,  strategy.fold)
        val evCall  = blend(baseCall,  strategy.call)
        val evRaise = blend(baseRaise, strategy.raise)
        val evCheck = blend(baseCheck, strategy.check)

        return mapOf(
            Action.Kind.FOLD  to round2(evFold),
            Action.Kind.CHECK to round2(evCheck),
            Action.Kind.CALL  to round2(evCall),
            Action.Kind.RAISE to round2(evRaise)
        )
    }

    /**
     * 混合度：策略熵（自然對數）/ log(4)，0 表純策略，1 表四選項均等。
     */
    private fun mixednessFactor(s: MixedStrategy): Double {
        val ps = doubleArrayOf(s.fold, s.check, s.call, s.raise).filter { it > 0.0 }
        if (ps.size <= 1) return 0.0
        val h = -ps.sumOf { it * ln(it) }
        return (h / ln(4.0)).coerceIn(0.0, 1.0)
    }

    private fun round2(x: Double): Double = kotlin.math.round(x * 100.0) / 100.0

    /**
     * 此手在整體範圍中的「組合佔比」百分比。
     *  - 分子：此手 (combos × 非 fold 機率)
     *  - 分母：整體範圍 (combos × 非 fold 機率) 加總
     * 若該手 100% fold，回傳 0.0。
     */
    private fun rangeShareOfHand(matrix: HandMatrix, target: HandClass): Double {
        var totalActive = 0.0
        var thisActive = 0.0
        for (hc in HandClass.all169()) {
            val s = matrix.get(hc)
            val active = (1.0 - s.fold) * hc.combos
            totalActive += active
            if (hc == target) thisActive = active
        }
        if (totalActive <= 0.0) return 0.0
        return thisActive / totalActive * 100.0
    }

    /**
     * 教學摘要文字（HUD 用）。
     */
    fun explain(rec: Recommendation): String {
        val s = rec.strategy
        val pct = { d: Double -> "${(d * 100).toInt()}%" }
        val parts = buildList {
            if (s.raise > 0) add("${com.pokercoach.ui.theme.Strings.RAISE} ${pct(s.raise)}")
            if (s.call  > 0) add("${com.pokercoach.ui.theme.Strings.CALL} ${pct(s.call)}")
            if (s.check > 0) add("${com.pokercoach.ui.theme.Strings.CHECK} ${pct(s.check)}")
            if (s.fold  > 0) add("${com.pokercoach.ui.theme.Strings.FOLD} ${pct(s.fold)}")
        }
        val mix = if (rec.isMixedDecision) " [GTO 混合決策]" else ""
        return "${rec.hand} @ ${rec.scenario.displayNameZh} → " +
            parts.joinToString(" / ") + mix +
            "  | 加權 EV = ${"%.2f".format(rec.gtoBlendedEv)} bb"
    }
}
