package com.pokercoach

import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SB vs BTN RFI 範圍合理性檢查（撲克常識基準）：
 *  - SB 防守需 3-bet 為主（無法只跟注，因 OOP + BB 在後）
 *  - AA/KK/QQ 應 100% 加注（純策略）
 *  - 72o 必蓋
 *  - SB 的整體防守頻率 vs BTN 約 30-40%（嚴格緊）
 */
class SbVsRfiRangeTest {

    private val scenario = RangeScenario.SbVsRfi(Position.BTN, 2.5)

    @Test fun premiumPairsArePureRaise() {
        for (h in listOf("AA", "KK", "QQ")) {
            val s = PreflopRangeManager.strategyFor(scenario, HandClass.parse(h))
            assertTrue("$h should raise heavily, got raise=${s.raise}", s.raise >= 0.95)
        }
    }

    @Test fun trashHandsAreFold() {
        for (h in listOf("72o", "83o", "94o")) {
            val s = PreflopRangeManager.strategyFor(scenario, HandClass.parse(h))
            assertTrue("$h should fold, got fold=${s.fold}", s.fold >= 0.95)
        }
    }

    @Test fun sbDefendFrequencyIsTight() {
        // 169 格用 combos 加權；SB OOP 防守頻率應低於 50%
        var defended = 0.0
        var total = 0.0
        for (hand in HandClass.all169()) {
            val s = PreflopRangeManager.strategyFor(scenario, hand)
            val w = hand.combos.toDouble()
            total += w
            defended += w * (1.0 - s.fold)
        }
        val defendPct = defended / total
        assertTrue("SB defend pct should be < 0.55, got $defendPct", defendPct < 0.55)
        assertTrue("SB defend pct should be > 0.10, got $defendPct", defendPct > 0.10)
    }

    @Test fun scenarioIsBuiltIn() {
        assertTrue(PreflopRangeManager.isSupported(scenario))
    }
}
