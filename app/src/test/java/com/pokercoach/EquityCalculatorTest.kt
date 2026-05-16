package com.pokercoach

import com.pokercoach.core.equity.EquityCalculator
import com.pokercoach.core.model.Card
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.HoleCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * EquityCalculator 邊界與合理性檢查。
 *
 * 蒙地卡羅本質有方差，所以斷言用寬鬆區間，靠固定 seed 重現性。
 */
class EquityCalculatorTest {

    private fun cards(vararg s: String) = s.map { Card.parse(it) }
    private fun hole(a: String, b: String) = HoleCards(Card.parse(a), Card.parse(b))

    @Test fun aaPreflopVsRandomNear85Percent() {
        val r = EquityCalculator.heroVsRandom(
            hero = hole("As", "Ah"),
            iterations = 3000,
            random = Random(42L)
        )
        // 文獻值約 85%；給 ±5% 寬容。
        assertTrue("AA vs random should be high, got ${r.winRate}", r.winRate in 0.80..0.90)
    }

    @Test fun bottomHandLosesMost() {
        val r = EquityCalculator.heroVsRandom(
            hero = hole("7c", "2d"),
            iterations = 3000,
            random = Random(42L)
        )
        // 72o 對隨機手約 35% — 仍非零但顯著低於 50%。
        assertTrue("72o should be < 0.45, got ${r.winRate}", r.winRate < 0.45)
    }

    @Test fun equityBoundedInZeroOne() {
        val r = EquityCalculator.heroVsRandom(
            hero = hole("Tc", "Jd"),
            iterations = 500,
            random = Random(7L)
        )
        assertTrue(r.winRate in 0.0..1.0)
        assertTrue(r.ties in 0.0..1.0)
        assertEquals(500, r.iterations)
    }

    @Test fun setVsOverpairOnDryFlop() {
        // 88 on 8-3-2 rainbow vs AA range — set 應大幅領先
        val r = EquityCalculator.heroVsRange(
            hero = hole("8c", "8d"),
            villainRange = setOf(HandClass.parse("AA")),
            knownBoard = cards("8s", "3h", "2c"),
            iterations = 1500,
            random = Random(11L)
        )
        assertTrue("set should crush AA on dry, got ${r.winRate}", r.winRate > 0.85)
    }

    @Test fun versusEmptyRangeFallsBackToRandom() {
        val r = EquityCalculator.heroVsRange(
            hero = hole("Kh", "Kd"),
            villainRange = emptySet(),
            iterations = 500,
            random = Random(3L)
        )
        // KK vs random ~82%
        assertTrue(r.winRate in 0.75..0.90)
    }
}
