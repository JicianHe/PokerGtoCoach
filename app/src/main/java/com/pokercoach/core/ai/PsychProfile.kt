package com.pokercoach.core.ai

/**
 * AI 對手的「心理畫像」：用於在 GTO 基線上做剝削調整。
 *
 * 所有值為 [-1.0, 1.0] 區間的偏移，0 = 純 GTO，越正越「鬆兇」，越負越「緊弱」。
 *
 * 教學目的：學員看到不同畫像的 AI 行為差異，學習如何反向剝削。
 */
data class PsychProfile(
    val name: String,
    /** VPIP 偏移：> 0 表示玩更多手；< 0 表示更緊。 */
    val loosenessBias: Double = 0.0,
    /** 攻擊性偏移：> 0 表示更多 raise/3-bet；< 0 偏被動。 */
    val aggressionBias: Double = 0.0,
    /** Bluff 頻率偏移：影響邊緣手做 bluff raise 的傾向。 */
    val bluffBias: Double = 0.0,
    /** Call-station 程度：> 0 不愛 fold 給壓力；< 0 容易蓋牌。 */
    val stickiness: Double = 0.0
) {
    init {
        require(loosenessBias  in -1.0..1.0)
        require(aggressionBias in -1.0..1.0)
        require(bluffBias      in -1.0..1.0)
        require(stickiness     in -1.0..1.0)
    }

    companion object {
        /** 純 GTO，零剝削。 */
        val GTO = PsychProfile("GTO Baseline")

        /** TAG：緊兇型常規牌手。 */
        val TAG = PsychProfile(
            name = "Tight-Aggressive",
            loosenessBias = -0.15,
            aggressionBias = 0.20,
            bluffBias = 0.05
        )

        /** LAG：鬆兇瘋子。 */
        val LAG = PsychProfile(
            name = "Loose-Aggressive",
            loosenessBias = 0.30,
            aggressionBias = 0.45,
            bluffBias = 0.35
        )

        /** Nit：超緊膽小鬼。 */
        val NIT = PsychProfile(
            name = "Nit",
            loosenessBias = -0.45,
            aggressionBias = -0.30,
            bluffBias = -0.50,
            stickiness = -0.20
        )

        /** Calling Station：愛跟注的魚。 */
        val STATION = PsychProfile(
            name = "Calling Station",
            loosenessBias = 0.40,
            aggressionBias = -0.40,
            bluffBias = -0.30,
            stickiness = 0.55
        )

        val PRESETS: List<PsychProfile> = listOf(GTO, TAG, LAG, NIT, STATION)
    }
}
