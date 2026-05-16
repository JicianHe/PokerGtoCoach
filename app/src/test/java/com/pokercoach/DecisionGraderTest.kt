package com.pokercoach

import com.pokercoach.core.ev.EvCalculator
import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.RangeScenario
import com.pokercoach.core.model.Position
import com.pokercoach.core.stats.DecisionGrader
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.theme.VerdictLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DecisionGrader 閾值邊界測試。
 * 規則：>=50%=Optimal, >=20%=Acceptable, >=5%=Suboptimal, <5%=Blunder
 */
class DecisionGraderTest {

    private fun recWith(strategy: MixedStrategy): EvCalculator.Recommendation =
        EvCalculator.Recommendation(
            scenario = RangeScenario.Rfi(Position.BTN),
            hand = HandClass.parse("AKs"),
            strategy = strategy,
            evByAction = mapOf(
                Action.Kind.FOLD to 0.0,
                Action.Kind.CHECK to 0.0,
                Action.Kind.CALL to 0.0,
                Action.Kind.RAISE to 1.0
            ),
            recommendedAction = strategy.dominantAction(),
            rangeFrequencyPct = 0.0
        )

    @Test fun pureRaiseGradesRaiseOptimal() {
        val r = recWith(MixedStrategy(raise = 1.0))
        assertEquals(VerdictLevel.Optimal, DecisionGrader.grade(Action.Kind.RAISE, r))
        assertEquals(VerdictLevel.Blunder, DecisionGrader.grade(Action.Kind.FOLD, r))
    }

    @Test fun fiftyPercentBoundaryIsOptimal() {
        val r = recWith(MixedStrategy(raise = 0.50, fold = 0.50))
        assertEquals(VerdictLevel.Optimal, DecisionGrader.grade(Action.Kind.RAISE, r))
        assertEquals(VerdictLevel.Optimal, DecisionGrader.grade(Action.Kind.FOLD, r))
    }

    @Test fun twentyPercentBoundaryIsAcceptable() {
        val r = recWith(MixedStrategy(raise = 0.60, call = 0.20, fold = 0.20))
        assertEquals(VerdictLevel.Acceptable, DecisionGrader.grade(Action.Kind.CALL, r))
        assertEquals(VerdictLevel.Acceptable, DecisionGrader.grade(Action.Kind.FOLD, r))
    }

    @Test fun fivePercentBoundaryIsSuboptimal() {
        val r = recWith(MixedStrategy(raise = 0.90, call = 0.05, fold = 0.05))
        assertEquals(VerdictLevel.Suboptimal, DecisionGrader.grade(Action.Kind.CALL, r))
    }

    @Test fun belowFivePercentIsBlunder() {
        val r = recWith(MixedStrategy(raise = 0.97, fold = 0.03))
        assertEquals(VerdictLevel.Blunder, DecisionGrader.grade(Action.Kind.FOLD, r))
    }

    @Test fun bucketMappingIsConsistent() {
        assertEquals(StatsRepository.VerdictBucket.OPTIMAL,    DecisionGrader.toBucket(VerdictLevel.Optimal))
        assertEquals(StatsRepository.VerdictBucket.ACCEPTABLE, DecisionGrader.toBucket(VerdictLevel.Acceptable))
        assertEquals(StatsRepository.VerdictBucket.SUBOPTIMAL, DecisionGrader.toBucket(VerdictLevel.Suboptimal))
        assertEquals(StatsRepository.VerdictBucket.BLUNDER,    DecisionGrader.toBucket(VerdictLevel.Blunder))
        assertEquals(StatsRepository.VerdictBucket.UNKNOWN,    DecisionGrader.toBucket(VerdictLevel.Unknown))
    }
}
