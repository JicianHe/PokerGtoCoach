package com.pokercoach

import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.HandMatrix
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflopRangeTest {

    @Test fun btnRfiHasAllPocketPairs() {
        val sc = RangeScenario.Rfi(Position.BTN)
        for (r in com.pokercoach.core.model.Rank.values()) {
            val hc = HandClass(r, r, false)
            val s = PreflopRangeManager.strategyFor(sc, hc)
            assertTrue("Pair $hc must raise 100%", s.raise >= 0.99)
        }
    }

    @Test fun bbVsBtnDefendIsHigh() {
        val sc = RangeScenario.BbVsRfi(Position.BTN, 2.5)
        val mat = PreflopRangeManager.matrixFor(sc)
        val freqs = mat.overallFrequencies()
        val defendPct = (freqs[com.pokercoach.core.model.Action.Kind.CALL] ?: 0.0) +
            (freqs[com.pokercoach.core.model.Action.Kind.RAISE] ?: 0.0)
        // BB 防守應在 55%~80% 之間（教學內建資料）
        assertTrue("Defend $defendPct% out of expected band", defendPct in 55.0..80.0)
    }

    @Test fun matrixRoundTrip() {
        for (hc in HandClass.all169()) {
            val (r, c) = HandMatrix.indexOf(hc)
            val back = HandMatrix.handClassAt(r, c)
            assertEquals(hc, back)
        }
    }

    @Test fun mixedStrategySumsToOne() {
        val sc = RangeScenario.BbVsRfi(Position.BTN, 2.5)
        for (hc in HandClass.all169()) {
            val s = PreflopRangeManager.strategyFor(sc, hc)
            val sum = s.fold + s.check + s.call + s.raise
            assertTrue("Hand $hc sum=$sum", kotlin.math.abs(sum - 1.0) < 1e-6)
        }
    }
}
