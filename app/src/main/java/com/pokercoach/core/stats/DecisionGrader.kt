package com.pokercoach.core.stats

import com.pokercoach.core.ev.EvCalculator
import com.pokercoach.core.model.Action
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.theme.VerdictLevel

/**
 * 將「玩家選的行動」與「GTO recommendation」比較，得出評價等級。
 *
 * 評級規則（根據混合策略中該行動的頻率）：
 *   - >= 50%：OPTIMAL
 *   - >= 20%：ACCEPTABLE
 *   - >=  5%：SUBOPTIMAL
 *   -  < 5%：BLUNDER
 */
object DecisionGrader {

    fun grade(chosen: Action.Kind, rec: EvCalculator.Recommendation): VerdictLevel {
        val freq = rec.strategy.frequencyOf(chosen)
        return when {
            freq >= 0.50 -> VerdictLevel.Optimal
            freq >= 0.20 -> VerdictLevel.Acceptable
            freq >= 0.05 -> VerdictLevel.Suboptimal
            else -> VerdictLevel.Blunder
        }
    }

    fun toBucket(v: VerdictLevel): StatsRepository.VerdictBucket = when (v) {
        VerdictLevel.Optimal -> StatsRepository.VerdictBucket.OPTIMAL
        VerdictLevel.Acceptable -> StatsRepository.VerdictBucket.ACCEPTABLE
        VerdictLevel.Suboptimal -> StatsRepository.VerdictBucket.SUBOPTIMAL
        VerdictLevel.Blunder -> StatsRepository.VerdictBucket.BLUNDER
        VerdictLevel.Unknown -> StatsRepository.VerdictBucket.UNKNOWN
    }
}
