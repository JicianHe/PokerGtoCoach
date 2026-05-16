package com.pokercoach.core.range

import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position

/**
 * 翻前 GTO 範圍管理器（擴充版）。
 *
 * 內建範圍：
 *  - UTG / HJ / CO / BTN / SB / BB 全部 RFI（率先開池）
 *  - BB vs UTG / HJ / CO / BTN / SB RFI 防守範圍
 *  - SB vs BTN RFI 防守（補強 vs steal）
 *
 * 數據來源：主流 GTO 求解器（PioSolver / GTOWizard）100bb 6-Max cash 公開範圍，
 * 四捨五入到 0.05 級距以保證可讀性與可演示性。
 *
 * 慣例：
 *  - 未列出的手 = PURE_FOLD
 *  - SB RFI 因為位置最差但有盲注折扣，整體頻率約 40%
 *  - 越早位範圍越窄、越偏 nuts；越後位範圍越寬、越平衡
 */
object PreflopRangeManager {

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
    // UTG RFI — 整體 ~14%，緊到不能再緊
    // =====================================================================
    private val UTG_RFI: Map<String, MixedStrategy> = buildMap {
        // 對子
        listOf("AA","KK","QQ","JJ","TT","99","88","77")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("66", raise(0.85))
        put("55", raise(0.50))

        // 同花 A 系
        listOf("AKs","AQs","AJs","ATs").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("A9s", raise(0.85))
        put("A5s", raise(0.70))  // 平衡 bluff
        put("A4s", raise(0.55))

        // 同花 K 系
        listOf("KQs","KJs","KTs").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K9s", raise(0.50))

        // 同花 Q 系
        put("QJs", MixedStrategy.PURE_RAISE)
        put("QTs", raise(0.85))

        // 同花連張
        put("JTs", MixedStrategy.PURE_RAISE)
        put("T9s", raise(0.70))
        put("98s", raise(0.40))

        // 不同花 A
        put("AKo", MixedStrategy.PURE_RAISE)
        put("AQo", raise(0.85))
        put("AJo", raise(0.40))

        put("KQo", raise(0.50))
    }

    // =====================================================================
    // HJ RFI — 整體 ~19%
    // =====================================================================
    private val HJ_RFI: Map<String, MixedStrategy> = buildMap {
        listOf("AA","KK","QQ","JJ","TT","99","88","77","66")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("55", raise(0.85))
        put("44", raise(0.60))
        put("33", raise(0.40))

        // 同花 A
        listOf("AKs","AQs","AJs","ATs","A9s","A8s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("A7s", raise(0.65))
        put("A5s", MixedStrategy.PURE_RAISE)  // bluff
        put("A4s", raise(0.85))
        put("A3s", raise(0.55))

        // K
        listOf("KQs","KJs","KTs","K9s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K8s", raise(0.50))

        // Q
        listOf("QJs","QTs","Q9s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        // J / T 連張
        listOf("JTs","J9s","T9s","T8s","98s","87s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("76s", raise(0.80))
        put("65s", raise(0.50))

        // 不同花
        put("AKo", MixedStrategy.PURE_RAISE)
        put("AQo", MixedStrategy.PURE_RAISE)
        put("AJo", raise(0.85))
        put("ATo", raise(0.50))
        put("KQo", raise(0.85))
        put("KJo", raise(0.45))
    }

    // =====================================================================
    // CO RFI — 整體 ~27%
    // =====================================================================
    private val CO_RFI: Map<String, MixedStrategy> = buildMap {
        listOf("AA","KK","QQ","JJ","TT","99","88","77","66","55","44")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("33", raise(0.85))
        put("22", raise(0.70))

        // A
        listOf("AKs","AQs","AJs","ATs","A9s","A8s","A7s","A6s","A5s","A4s","A3s","A2s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        // K
        listOf("KQs","KJs","KTs","K9s","K8s","K7s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K6s", raise(0.55))
        put("K5s", raise(0.40))

        // Q
        listOf("QJs","QTs","Q9s","Q8s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("Q7s", raise(0.45))

        // J
        listOf("JTs","J9s","J8s").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("J7s", raise(0.50))

        // T
        listOf("T9s","T8s","T7s").forEach { put(it, MixedStrategy.PURE_RAISE) }

        // 中小連張
        listOf("98s","97s","87s","86s","76s","65s","54s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("75s", raise(0.60))
        put("64s", raise(0.40))

        // 不同花
        put("AKo", MixedStrategy.PURE_RAISE)
        put("AQo", MixedStrategy.PURE_RAISE)
        put("AJo", MixedStrategy.PURE_RAISE)
        put("ATo", raise(0.80))
        put("A9o", raise(0.40))
        put("KQo", MixedStrategy.PURE_RAISE)
        put("KJo", raise(0.85))
        put("KTo", raise(0.40))
        put("QJo", raise(0.65))
        put("QTo", raise(0.35))
    }

    // =====================================================================
    // BTN RFI — 整體 ~48%（位置最強）
    // =====================================================================
    private val BTN_RFI: Map<String, MixedStrategy> = buildMap {
        listOf("AA","KK","QQ","JJ","TT","99","88","77","66","55","44","33","22")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        listOf("AKs","AQs","AJs","ATs","A9s","A8s","A7s","A6s","A5s","A4s","A3s","A2s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        listOf("KQs","KJs","KTs","K9s","K8s","K7s","K6s","K5s","K4s","K3s","K2s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        listOf("QJs","QTs","Q9s","Q8s","Q7s","Q6s","Q5s","Q4s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("Q3s", raise(0.70)); put("Q2s", raise(0.50))
        listOf("JTs","J9s","J8s","J7s","J6s").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("J5s", raise(0.75)); put("J4s", raise(0.40))
        listOf("T9s","T8s","T7s","T6s").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("T5s", raise(0.30))
        listOf("98s","97s","96s","87s","86s","76s","75s","65s","54s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("95s", raise(0.40)); put("85s", raise(0.30))
        put("74s", raise(0.20)); put("64s", raise(0.40))
        put("53s", raise(0.50)); put("43s", raise(0.40))

        listOf("AKo","AQo","AJo","ATo","A9o","A8o","A7o","A6o","A5o")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("A4o", raise(0.85)); put("A3o", raise(0.70)); put("A2o", raise(0.55))
        listOf("KQo","KJo","KTo").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K9o", raise(0.80)); put("K8o", raise(0.40)); put("K7o", raise(0.15))
        put("QJo", MixedStrategy.PURE_RAISE); put("QTo", raise(0.90)); put("Q9o", raise(0.45))
        put("JTo", raise(0.95)); put("J9o", raise(0.40))
        put("T9o", raise(0.55)); put("98o", raise(0.30)); put("87o", raise(0.15))
    }

    // =====================================================================
    // SB RFI — 整體 ~40%（位置最差但盲注折扣 + 對手只有 BB）
    //   策略：混合 limp + raise（這裡簡化為 raise-only 線）
    // =====================================================================
    private val SB_RFI: Map<String, MixedStrategy> = buildMap {
        listOf("AA","KK","QQ","JJ","TT","99","88","77","66","55","44","33","22")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        listOf("AKs","AQs","AJs","ATs","A9s","A8s","A7s","A6s","A5s","A4s","A3s","A2s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        listOf("KQs","KJs","KTs","K9s","K8s","K7s","K6s","K5s","K4s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K3s", raise(0.80)); put("K2s", raise(0.60))

        listOf("QJs","QTs","Q9s","Q8s","Q7s","Q6s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("Q5s", raise(0.70)); put("Q4s", raise(0.50))

        listOf("JTs","J9s","J8s","J7s").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("J6s", raise(0.65))
        listOf("T9s","T8s","T7s").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("T6s", raise(0.55))
        listOf("98s","97s","87s","86s","76s","65s","54s")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }

        // 不同花
        listOf("AKo","AQo","AJo","ATo","A9o","A8o","A7o","A6o","A5o","A4o","A3o","A2o")
            .forEach { put(it, MixedStrategy.PURE_RAISE) }
        listOf("KQo","KJo","KTo","K9o").forEach { put(it, MixedStrategy.PURE_RAISE) }
        put("K8o", raise(0.65)); put("K7o", raise(0.40))
        put("QJo", MixedStrategy.PURE_RAISE); put("QTo", MixedStrategy.PURE_RAISE)
        put("Q9o", raise(0.70)); put("Q8o", raise(0.35))
        put("JTo", MixedStrategy.PURE_RAISE); put("J9o", raise(0.65))
        put("T9o", raise(0.75)); put("T8o", raise(0.35))
        put("98o", raise(0.45))
    }

    // =====================================================================
    // BB vs BTN RFI 2.5bb — 整體防守 ~68%
    // =====================================================================
    private val BB_VS_BTN_RFI: Map<String, MixedStrategy> = buildMap {
        put("AA", MixedStrategy.PURE_RAISE)
        put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE)
        put("JJ", callRaiseFold(0.20, 0.80))
        put("TT", callRaiseFold(0.40, 0.60))
        put("99", callRaiseFold(0.75, 0.25))
        put("88", callRaiseFold(0.85, 0.15))
        listOf("77","66","55","44","33","22").forEach { put(it, call(1.0)) }

        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", callRaiseFold(0.10, 0.90))
        put("AJs", callRaiseFold(0.35, 0.65))
        put("ATs", callRaiseFold(0.60, 0.40))
        put("A9s", callRaiseFold(0.85, 0.15))
        listOf("A8s","A7s","A6s","A2s").forEach { put(it, call(1.0)) }
        put("A5s", callRaiseFold(0.35, 0.65))
        put("A4s", callRaiseFold(0.45, 0.55))
        put("A3s", callRaiseFold(0.70, 0.30))

        put("KQs", callRaiseFold(0.45, 0.55))
        put("KJs", callRaiseFold(0.70, 0.30))
        listOf("KTs","K9s","K8s","K7s").forEach { put(it, call(1.0)) }
        put("K6s", call(0.95)); put("K5s", callRaiseFold(0.60, 0.20))
        put("K4s", callRaiseFold(0.50, 0.15)); put("K3s", call(0.70)); put("K2s", call(0.60))

        put("QJs", callRaiseFold(0.70, 0.20))
        listOf("QTs","Q9s","Q8s").forEach { put(it, call(1.0)) }
        put("Q7s", call(0.90)); put("Q6s", call(0.80)); put("Q5s", call(0.70))
        put("Q4s", call(0.55)); put("Q3s", call(0.40)); put("Q2s", call(0.25))

        put("JTs", callRaiseFold(0.65, 0.20))
        listOf("J9s","J8s").forEach { put(it, call(1.0)) }
        put("J7s", call(0.90)); put("J6s", call(0.70)); put("J5s", call(0.50)); put("J4s", call(0.30))

        listOf("T9s","T8s").forEach { put(it, call(1.0)) }
        put("T7s", call(0.90)); put("T6s", call(0.65)); put("T5s", call(0.30))
        listOf("98s","87s","76s","65s").forEach { put(it, call(1.0)) }
        put("97s", call(0.95)); put("96s", call(0.75))
        put("86s", call(0.85)); put("85s", call(0.45))
        put("75s", call(0.80)); put("74s", call(0.35))
        put("64s", call(0.70))
        put("54s", callRaiseFold(0.55, 0.25))
        put("53s", call(0.60)); put("43s", call(0.55))

        put("AKo", callRaiseFold(0.20, 0.80))
        put("AQo", callRaiseFold(0.50, 0.50))
        put("AJo", callRaiseFold(0.80, 0.20))
        listOf("ATo","A4o","A3o","A2o").forEach { put(it, call(1.0)) }
        put("A9o", call(0.90)); put("A8o", call(0.75)); put("A7o", call(0.60)); put("A6o", call(0.45))
        put("A5o", callRaiseFold(0.45, 0.20))
        put("KQo", callRaiseFold(0.75, 0.20))
        put("KJo", call(1.0)); put("KTo", call(0.95))
        put("K9o", call(0.75)); put("K8o", call(0.45)); put("K7o", call(0.25))
        put("QJo", call(1.0)); put("QTo", call(0.95)); put("Q9o", call(0.65)); put("Q8o", call(0.35))
        put("JTo", call(1.0)); put("J9o", call(0.75)); put("J8o", call(0.40))
        put("T9o", call(0.90)); put("T8o", call(0.55))
        put("98o", call(0.70)); put("97o", call(0.35))
        put("87o", call(0.55)); put("76o", call(0.40)); put("65o", call(0.30))
    }

    // =====================================================================
    // BB vs CO RFI 2.5bb — 防守 ~58%（窄於 vs BTN）
    // =====================================================================
    private val BB_VS_CO_RFI: Map<String, MixedStrategy> = buildMap {
        put("AA", MixedStrategy.PURE_RAISE); put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE); put("JJ", callRaiseFold(0.15, 0.85))
        put("TT", callRaiseFold(0.30, 0.70)); put("99", callRaiseFold(0.65, 0.35))
        put("88", call(1.0)); put("77", call(1.0)); put("66", call(1.0))
        put("55", call(1.0)); put("44", call(0.90)); put("33", call(0.80)); put("22", call(0.70))

        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", callRaiseFold(0.05, 0.95))
        put("AJs", callRaiseFold(0.25, 0.75))
        put("ATs", callRaiseFold(0.55, 0.45))
        put("A9s", call(1.0)); put("A8s", call(1.0)); put("A7s", call(1.0))
        put("A5s", callRaiseFold(0.35, 0.55))
        put("A4s", callRaiseFold(0.55, 0.35))
        put("A3s", call(0.85)); put("A2s", call(0.75))

        put("KQs", callRaiseFold(0.45, 0.45))
        put("KJs", callRaiseFold(0.70, 0.20))
        put("KTs", call(1.0)); put("K9s", call(0.95))
        put("K8s", call(0.80)); put("K7s", call(0.55))

        put("QJs", call(1.0)); put("QTs", call(1.0))
        put("Q9s", call(0.85)); put("Q8s", call(0.55))
        put("JTs", callRaiseFold(0.65, 0.15))
        put("J9s", call(0.90)); put("J8s", call(0.65))
        put("T9s", call(1.0)); put("T8s", call(0.80))
        put("98s", call(0.95)); put("97s", call(0.55))
        put("87s", call(0.85)); put("76s", call(0.75))
        put("65s", call(0.65)); put("54s", call(0.45))

        put("AKo", callRaiseFold(0.15, 0.85))
        put("AQo", callRaiseFold(0.45, 0.45))
        put("AJo", call(0.95)); put("ATo", call(0.85))
        put("A9o", call(0.55)); put("A8o", call(0.35))
        put("KQo", call(1.0)); put("KJo", call(0.85))
        put("KTo", call(0.55)); put("K9o", call(0.30))
        put("QJo", call(0.80)); put("QTo", call(0.50))
        put("JTo", call(0.75)); put("T9o", call(0.45))
    }

    // =====================================================================
    // BB vs HJ RFI 2.5bb — 防守 ~52%
    // =====================================================================
    private val BB_VS_HJ_RFI: Map<String, MixedStrategy> = buildMap {
        put("AA", MixedStrategy.PURE_RAISE); put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE); put("JJ", MixedStrategy.PURE_RAISE)
        put("TT", callRaiseFold(0.25, 0.75)); put("99", callRaiseFold(0.55, 0.45))
        put("88", call(1.0)); put("77", call(1.0)); put("66", call(1.0))
        put("55", call(1.0)); put("44", call(0.85)); put("33", call(0.65)); put("22", call(0.55))

        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", MixedStrategy.PURE_RAISE)
        put("AJs", callRaiseFold(0.20, 0.80))
        put("ATs", call(1.0)); put("A9s", call(1.0))
        put("A5s", callRaiseFold(0.40, 0.45)); put("A4s", callRaiseFold(0.60, 0.30))

        put("KQs", call(1.0)); put("KJs", call(1.0)); put("KTs", call(0.95))
        put("K9s", call(0.70))
        put("QJs", call(1.0)); put("QTs", call(0.95))
        put("JTs", call(1.0)); put("T9s", call(0.85))
        put("98s", call(0.75)); put("87s", call(0.55))

        put("AKo", MixedStrategy.PURE_RAISE)
        put("AQo", callRaiseFold(0.30, 0.55))
        put("AJo", call(0.90)); put("ATo", call(0.60))
        put("KQo", call(0.95)); put("KJo", call(0.65)); put("KTo", call(0.35))
        put("QJo", call(0.55)); put("JTo", call(0.45))
    }

    // =====================================================================
    // BB vs UTG RFI 2.5bb — 防守 ~42%（最緊）
    // =====================================================================
    private val BB_VS_UTG_RFI: Map<String, MixedStrategy> = buildMap {
        put("AA", MixedStrategy.PURE_RAISE); put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE); put("JJ", MixedStrategy.PURE_RAISE)
        put("TT", MixedStrategy.PURE_RAISE)
        put("99", callRaiseFold(0.40, 0.50))
        put("88", call(1.0)); put("77", call(1.0)); put("66", call(0.95))
        put("55", call(0.85)); put("44", call(0.60)); put("33", call(0.40)); put("22", call(0.30))

        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", MixedStrategy.PURE_RAISE)
        put("AJs", call(1.0)); put("ATs", call(1.0)); put("A9s", call(0.85))
        put("A5s", callRaiseFold(0.40, 0.40)); put("A4s", call(0.70))

        put("KQs", call(1.0)); put("KJs", call(0.95)); put("KTs", call(0.80))
        put("QJs", call(0.95)); put("QTs", call(0.80))
        put("JTs", call(0.95)); put("T9s", call(0.75)); put("98s", call(0.55))

        put("AKo", MixedStrategy.PURE_RAISE)
        put("AQo", callRaiseFold(0.40, 0.45))
        put("AJo", call(0.75)); put("ATo", call(0.45))
        put("KQo", call(0.80)); put("KJo", call(0.45))
    }

    // =====================================================================
    // BB vs SB RFI 2.5bb — 防守 ~75%（最寬，因 SB 已用寬範圍開）
    // =====================================================================
    private val BB_VS_SB_RFI: Map<String, MixedStrategy> = buildMap {
        put("AA", MixedStrategy.PURE_RAISE); put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE); put("JJ", MixedStrategy.PURE_RAISE)
        put("TT", callRaiseFold(0.30, 0.70))
        put("99", callRaiseFold(0.55, 0.45))
        put("88", callRaiseFold(0.80, 0.20))
        listOf("77","66","55","44","33","22").forEach { put(it, call(1.0)) }

        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", callRaiseFold(0.20, 0.80))
        put("AJs", callRaiseFold(0.50, 0.50))
        put("ATs", call(1.0))
        listOf("A9s","A8s","A7s","A6s","A2s").forEach { put(it, call(1.0)) }
        put("A5s", callRaiseFold(0.40, 0.55))
        put("A4s", callRaiseFold(0.50, 0.45))
        put("A3s", call(0.95))

        listOf("KQs","KJs","KTs","K9s","K8s","K7s","K6s","K5s","K4s","K3s","K2s")
            .forEach { put(it, call(1.0)) }
        put("KQs", callRaiseFold(0.55, 0.45))

        listOf("QJs","QTs","Q9s","Q8s","Q7s","Q6s","Q5s","Q4s","Q3s")
            .forEach { put(it, call(1.0)) }
        put("Q2s", call(0.65))

        listOf("JTs","J9s","J8s","J7s","J6s","J5s","J4s").forEach { put(it, call(1.0)) }
        listOf("T9s","T8s","T7s","T6s","T5s").forEach { put(it, call(1.0)) }
        listOf("98s","97s","96s","87s","86s","76s","75s","65s","54s","53s","43s")
            .forEach { put(it, call(1.0)) }
        put("85s", call(0.85)); put("74s", call(0.75))

        // 不同花（BB vs SB heads-up 後位優勢）
        put("AKo", callRaiseFold(0.25, 0.75))
        put("AQo", callRaiseFold(0.55, 0.45))
        listOf("AJo","ATo","A9o","A8o","A7o","A6o","A5o","A4o","A3o","A2o")
            .forEach { put(it, call(1.0)) }
        put("KQo", callRaiseFold(0.75, 0.20))
        listOf("KJo","KTo","K9o","K8o","K7o","K6o","K5o").forEach { put(it, call(1.0)) }
        listOf("QJo","QTo","Q9o","Q8o","Q7o").forEach { put(it, call(1.0)) }
        listOf("JTo","J9o","J8o","J7o").forEach { put(it, call(1.0)) }
        listOf("T9o","T8o","T7o","98o","97o","87o","86o","76o","65o","54o")
            .forEach { put(it, call(1.0)) }
    }

    // =====================================================================
    // SB vs BTN RFI 2.5bb — 反向 squeeze 範圍
    // =====================================================================
    private val SB_VS_BTN_RFI: Map<String, MixedStrategy> = buildMap {
        put("AA", MixedStrategy.PURE_RAISE); put("KK", MixedStrategy.PURE_RAISE)
        put("QQ", MixedStrategy.PURE_RAISE)
        put("JJ", callRaiseFold(0.0, 1.0))
        put("TT", callRaiseFold(0.10, 0.90))
        put("99", callRaiseFold(0.55, 0.45))
        put("88", callRaiseFold(0.80, 0.15))
        listOf("77","66","55","44","33","22").forEach { put(it, call(0.90)) }

        put("AKs", MixedStrategy.PURE_RAISE)
        put("AQs", MixedStrategy.PURE_RAISE)
        put("AJs", callRaiseFold(0.20, 0.80))
        put("ATs", callRaiseFold(0.50, 0.50))
        put("A9s", call(0.85))
        put("A5s", callRaiseFold(0.30, 0.55))
        put("A4s", callRaiseFold(0.45, 0.40))

        put("KQs", callRaiseFold(0.30, 0.65))
        put("KJs", callRaiseFold(0.60, 0.30))
        put("KTs", call(0.95)); put("K9s", call(0.70))

        put("QJs", callRaiseFold(0.65, 0.20))
        put("QTs", call(0.95)); put("JTs", callRaiseFold(0.65, 0.20))
        put("T9s", call(0.85)); put("98s", call(0.75)); put("87s", call(0.55))
        put("65s", call(0.45))

        put("AKo", callRaiseFold(0.10, 0.90))
        put("AQo", callRaiseFold(0.35, 0.55))
        put("AJo", call(0.80)); put("ATo", call(0.55))
        put("KQo", call(0.90)); put("KJo", call(0.50))
    }

    // ---------------------------------------------------------------------
    private fun buildMatrix(raw: Map<String, MixedStrategy>): HandMatrix {
        val mapped: Map<HandClass, MixedStrategy> = raw.mapKeys { (s, _) -> HandClass.parse(s) }
        return HandMatrix.fromMap(mapped)
    }

    private val matrices: Map<RangeScenario, HandMatrix> by lazy {
        mapOf(
            RangeScenario.Rfi(Position.UTG) to buildMatrix(UTG_RFI),
            RangeScenario.Rfi(Position.HJ)  to buildMatrix(HJ_RFI),
            RangeScenario.Rfi(Position.CO)  to buildMatrix(CO_RFI),
            RangeScenario.Rfi(Position.BTN) to buildMatrix(BTN_RFI),
            RangeScenario.Rfi(Position.SB)  to buildMatrix(SB_RFI),
            RangeScenario.BbVsRfi(Position.UTG, 2.5) to buildMatrix(BB_VS_UTG_RFI),
            RangeScenario.BbVsRfi(Position.HJ,  2.5) to buildMatrix(BB_VS_HJ_RFI),
            RangeScenario.BbVsRfi(Position.CO,  2.5) to buildMatrix(BB_VS_CO_RFI),
            RangeScenario.BbVsRfi(Position.BTN, 2.5) to buildMatrix(BB_VS_BTN_RFI),
            RangeScenario.BbVsRfi(Position.SB,  2.5) to buildMatrix(BB_VS_SB_RFI),
            RangeScenario.SbVsRfi(Position.BTN, 2.5) to buildMatrix(SB_VS_BTN_RFI)
        )
    }

    fun isSupported(scenario: RangeScenario): Boolean = matrices.containsKey(scenario)
    fun matrixFor(scenario: RangeScenario): HandMatrix =
        matrices[scenario]
            ?: throw IllegalArgumentException("Range not built-in: ${scenario.displayName}")
    fun strategyFor(scenario: RangeScenario, hand: HandClass): MixedStrategy =
        matrixFor(scenario).get(hand)
    fun supportedScenarios(): List<RangeScenario> = matrices.keys.toList()
}
