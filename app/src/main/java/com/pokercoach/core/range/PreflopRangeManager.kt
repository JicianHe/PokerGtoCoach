package com.pokercoach.core.range

import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position

/**
 * 翻前 GTO 範圍管理器。
 *
 * 設計目標：
 *  - 靜態內建若干標準情境的 GTO 混合策略矩陣（100bb 6-Max cash 為基準）。
 *  - 提供 O(1) 查詢：給定情境 + 起手牌類別 → MixedStrategy。
 *  - 範圍資料以 (HandClass.shorthand → MixedStrategy) 形式列出，未列出者 = PURE_FOLD。
 *
 * 數據說明：
 *  - 內建兩套範圍：BTN RFI、BB vs BTN RFI 2.5bb。
 *  - 數值為主流 GTO 求解器（PioSolver / GTO+ / WizardGTO）公開範圍的近似值，
 *    並已四捨五入到 0.05 級距以保持矩陣可讀性與可演示性。
 *  - 邊界混合手牌（如 A5s, KTo, 22）保留混合頻率以體現「真實 GTO 並非黑白分明」。
 *
 *  ※ 後續階段（Phase 5）可從 JSON 載入更精細的 solver 輸出，本階段以靜態內建為主。
 */
object PreflopRangeManager {

    // ---------------------------------------------------------------------
    // 內部：DSL helper
    // ---------------------------------------------------------------------
    private fun raise(p: Double): MixedStrategy =
        MixedStrategy(fold = 1.0 - p, raise = p)

    private fun callRaiseFold(call: Double, raise: Double): MixedStrategy {
        val fold = 1.0 - call - raise
        require(fold >= -1e-9) { "call+raise > 1: c=$call r=$raise" }
        return MixedStrategy(fold = fold.coerceAtLeast(0.0), call = call, raise = raise)
    }

    private fun call(p: Double): MixedStrategy =
        MixedStrategy(fold = 1.0 - p, call = p)

    // =====================================================================
    // 範圍 1：BTN RFI（率先開池到 2.5bb），100bb 深度，6-Max
    // 整體 RFI 頻率 ≈ 48~50%
    // =====================================================================
    private val BTN_RFI: Map<String, MixedStrategy> = buildMap {
        // ---- 對子：全部 100% RAISE ----
        listOf("AA","KK","QQ","JJ","TT","99","88","77","66","55","44","33","22")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        // ---- 同花 A 系（A2s+ 全 raise） ----
        listOf("AKs","AQs","AJs","ATs","A9s","A8s","A7s","A6s","A5s","A4s","A3s","A2s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        // ---- 同花 K 系（K2s+ 全 raise） ----
        listOf("KQs","KJs","KTs","K9s","K8s","K7s","K6s","K5s","K4s","K3s","K2s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        // ---- 同花 Q 系（Q4s+ 全 raise，Q3s/Q2s 混合） ----
        listOf("QJs","QTs","Q9s","Q8s","Q7s","Q6s","Q5s","Q4s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("Q3s", raise(0.70))
        put("Q2s", raise(0.50))

        // ---- 同花 J 系 ----
        listOf("JTs","J9s","J8s","J7s","J6s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("J5s", raise(0.75))
        put("J4s", raise(0.40))
        // J3s / J2s 通常 fold

        // ---- 同花 T 系 ----
        listOf("T9s","T8s","T7s","T6s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("T5s", raise(0.30))

        // ---- 同花中低連張 ----
        listOf("98s","97s","96s","87s","86s","76s","75s","65s","54s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("95s", raise(0.40))
        put("85s", raise(0.30))
        put("74s", raise(0.20))
        put("64s", raise(0.40))
        put("53s", raise(0.50))
        put("43s", raise(0.40))

        // ---- 不同花 A 系 ----
        listOf("AKo","AQo","AJo","ATo","A9o","A8o","A7o","A6o","A5o")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("A4o", raise(0.85))
        put("A3o", raise(0.70))
        put("A2o", raise(0.55))

        // ---- 不同花 K 系 ----
        listOf("KQo","KJo","KTo")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K9o", raise(0.80))
        put("K8o", raise(0.40))
        put("K7o", raise(0.15))

        // ---- 不同花 Q 系 ----
        put("QJo", MixedStrategy.PURE_RAISE)
        put("QTo", raise(0.90))
        put("Q9o", raise(0.45))

        // ---- 不同花 J/T 系 ----
        put("JTo", raise(0.95))
        put("J9o", raise(0.40))
        put("T9o", raise(0.55))
        put("98o", raise(0.30))
        put("87o", raise(0.15))
    }

    // =====================================================================
    // 範圍 2：BB vs BTN RFI 2.5bb（BB 防守：3-bet 或 cold call）
    // 整體防守頻率 ≈ 65~70%（盲注折扣 + 位置劣勢的平衡）
    // 3-bet sizing 假設約 11~12bb（線性 + 兩極化混合）
    // =====================================================================
    private val BB_VS_BTN_RFI: Map<String, MixedStrategy> = buildMap {
        // ===== 高對：純 3-bet 為主 =====
        put("AA", MixedStrategy.PURE_RAISE)
        put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE)
        put("JJ", callRaiseFold(call = 0.20, raise = 0.80))
        put("TT", callRaiseFold(call = 0.40, raise = 0.60))
        put("99", callRaiseFold(call = 0.75, raise = 0.25))
        put("88", callRaiseFold(call = 0.85, raise = 0.15))
        put("77", call(1.0))
        put("66", call(1.0))
        put("55", call(1.0))
        put("44", call(1.0))
        put("33", call(1.0))
        put("22", call(1.0))

        // ===== 同花 A 系：強 3-bet 為主，弱 A 用作平衡型 3-bet bluff =====
        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", callRaiseFold(call = 0.10, raise = 0.90))
        put("AJs", callRaiseFold(call = 0.35, raise = 0.65))
        put("ATs", callRaiseFold(call = 0.60, raise = 0.40))
        put("A9s", callRaiseFold(call = 0.85, raise = 0.15))
        put("A8s", call(1.0))
        put("A7s", call(1.0))
        put("A6s", call(1.0))
        put("A5s", callRaiseFold(call = 0.35, raise = 0.65)) // 經典 3-bet bluff
        put("A4s", callRaiseFold(call = 0.45, raise = 0.55))
        put("A3s", callRaiseFold(call = 0.70, raise = 0.30))
        put("A2s", call(1.0))

        // ===== 同花 K 系 =====
        put("KQs", callRaiseFold(call = 0.45, raise = 0.55))
        put("KJs", callRaiseFold(call = 0.70, raise = 0.30))
        put("KTs", call(1.0))
        put("K9s", call(1.0))
        put("K8s", call(1.0))
        put("K7s", call(1.0))
        put("K6s", call(0.95))
        put("K5s", callRaiseFold(call = 0.60, raise = 0.20))
        put("K4s", callRaiseFold(call = 0.50, raise = 0.15))
        put("K3s", call(0.70))
        put("K2s", call(0.60))

        // ===== 同花 Q 系 =====
        put("QJs", callRaiseFold(call = 0.70, raise = 0.20))
        put("QTs", call(1.0))
        put("Q9s", call(1.0))
        put("Q8s", call(1.0))
        put("Q7s", call(0.90))
        put("Q6s", call(0.80))
        put("Q5s", call(0.70))
        put("Q4s", call(0.55))
        put("Q3s", call(0.40))
        put("Q2s", call(0.25))

        // ===== 同花 J 系 =====
        put("JTs", callRaiseFold(call = 0.65, raise = 0.20))
        put("J9s", call(1.0))
        put("J8s", call(1.0))
        put("J7s", call(0.90))
        put("J6s", call(0.70))
        put("J5s", call(0.50))
        put("J4s", call(0.30))

        // ===== 同花 T 系 =====
        put("T9s", call(1.0))
        put("T8s", call(1.0))
        put("T7s", call(0.90))
        put("T6s", call(0.65))
        put("T5s", call(0.30))

        // ===== 同花中小連張 =====
        put("98s", call(1.0))
        put("97s", call(0.95))
        put("96s", call(0.75))
        put("87s", call(1.0))
        put("86s", call(0.85))
        put("85s", call(0.45))
        put("76s", call(1.0))
        put("75s", call(0.80))
        put("74s", call(0.35))
        put("65s", call(1.0))
        put("64s", call(0.70))
        put("54s", callRaiseFold(call = 0.55, raise = 0.25)) // 平衡型 3-bet bluff
        put("53s", call(0.60))
        put("43s", call(0.55))

        // ===== 不同花 A 系 =====
        put("AKo", callRaiseFold(call = 0.20, raise = 0.80))
        put("AQo", callRaiseFold(call = 0.50, raise = 0.50))
        put("AJo", callRaiseFold(call = 0.80, raise = 0.20))
        put("ATo", call(1.0))
        put("A9o", call(0.90))
        put("A8o", call(0.75))
        put("A7o", call(0.60))
        put("A6o", call(0.45))
        put("A5o", callRaiseFold(call = 0.45, raise = 0.20))
        put("A4o", call(0.50))
        put("A3o", call(0.40))
        put("A2o", call(0.35))

        // ===== 不同花 K 系 =====
        put("KQo", callRaiseFold(call = 0.75, raise = 0.20))
        put("KJo", call(1.0))
        put("KTo", call(0.95))
        put("K9o", call(0.75))
        put("K8o", call(0.45))
        put("K7o", call(0.25))

        // ===== 不同花 Q 系 =====
        put("QJo", call(1.0))
        put("QTo", call(0.95))
        put("Q9o", call(0.65))
        put("Q8o", call(0.35))

        // ===== 不同花 J/T/低位 =====
        put("JTo", call(1.0))
        put("J9o", call(0.75))
        put("J8o", call(0.40))
        put("T9o", call(0.90))
        put("T8o", call(0.55))
        put("98o", call(0.70))
        put("97o", call(0.35))
        put("87o", call(0.55))
        put("76o", call(0.40))
        put("65o", call(0.30))
    }

    // ---------------------------------------------------------------------
    // 構建為 HandMatrix（解析 shorthand → HandClass）
    // ---------------------------------------------------------------------
    private fun buildMatrix(raw: Map<String, MixedStrategy>): HandMatrix {
        val mapped: Map<HandClass, MixedStrategy> = raw.mapKeys { (s, _) -> HandClass.parse(s) }
        return HandMatrix.fromMap(mapped)
    }

    // 預先構建（lazy 避免初始化期失敗）
    private val matrices: Map<RangeScenario, HandMatrix> by lazy {
        mapOf(
            RangeScenario.Rfi(Position.BTN)                      to buildMatrix(BTN_RFI),
            RangeScenario.BbVsRfi(Position.BTN, sizeBb = 2.5)    to buildMatrix(BB_VS_BTN_RFI)
        )
    }

    // =====================================================================
    // Public API
    // =====================================================================

    /** 是否支援該情境。 */
    fun isSupported(scenario: RangeScenario): Boolean = matrices.containsKey(scenario)

    /** 取得情境下的完整 13x13 矩陣。 */
    fun matrixFor(scenario: RangeScenario): HandMatrix =
        matrices[scenario]
            ?: throw IllegalArgumentException("Range not built-in: ${scenario.displayName}")

    /** 取得某一手在某情境下的混合策略（不存在則為 PURE_FOLD）。 */
    fun strategyFor(scenario: RangeScenario, hand: HandClass): MixedStrategy =
        matrixFor(scenario).get(hand)

    /** 已支援的情境列表（用於 UI 選單）。 */
    fun supportedScenarios(): List<RangeScenario> = matrices.keys.toList()
}
