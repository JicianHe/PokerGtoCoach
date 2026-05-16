package com.pokercoach

import com.pokercoach.core.eval.HandEvaluator
import com.pokercoach.core.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandEvaluatorTest {

    private fun cards(vararg s: String): List<Card> = s.map { Card.parse(it) }

    @Test fun straightFlushBeatsFourOfAKind() {
        val sf = HandEvaluator.evaluate(cards("9h","8h","7h","6h","5h","2c","Ad"))
        val quad = HandEvaluator.evaluate(cards("Ah","Ad","As","Ac","Kh","Qd","Jc"))
        assertEquals(HandEvaluator.Category.STRAIGHT_FLUSH, sf.category)
        assertEquals(HandEvaluator.Category.FOUR_OF_A_KIND, quad.category)
        assertTrue(sf > quad)
    }

    @Test fun wheelStraightIsHighFive() {
        val wheel = HandEvaluator.evaluate(cards("Ah","2d","3c","4s","5h","Kc","Qd"))
        assertEquals(HandEvaluator.Category.STRAIGHT, wheel.category)
        assertEquals(5, wheel.kickers.first())
    }

    @Test fun broadwayStraightBeatsWheel() {
        val broadway = HandEvaluator.evaluate(cards("Ah","Kd","Qc","Js","Th","2c","3d"))
        val wheel = HandEvaluator.evaluate(cards("Ah","2d","3c","4s","5h","Kc","Qd"))
        assertTrue(broadway > wheel)
    }

    @Test fun fullHouseBeatsFlush() {
        val fh = HandEvaluator.evaluate(cards("Ah","Ad","As","Kh","Kd","2c","3d"))
        val fl = HandEvaluator.evaluate(cards("Ah","Th","8h","6h","2h","Kd","Qc"))
        assertTrue(fh > fl)
    }

    @Test fun kickerDecidesPair() {
        val a = HandEvaluator.evaluate(cards("Ah","Ad","Kh","Qd","Jc","9s","2c"))
        val b = HandEvaluator.evaluate(cards("Ah","Ad","Kh","Qd","Tc","9s","2c"))
        assertTrue(a > b)
    }
}
