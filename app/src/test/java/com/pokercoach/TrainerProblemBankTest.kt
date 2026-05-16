package com.pokercoach

import com.pokercoach.core.trainer.TrainerProblemBank
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrainerProblemBankTest {

    @Test fun postflopBankHasContent() {
        assertTrue(
            "Expected at least 50 postflop problems",
            TrainerProblemBank.POSTFLOP_PROBLEMS.size >= 50
        )
    }

    @Test fun everyPostflopProblemIsWellFormed() {
        for (p in TrainerProblemBank.POSTFLOP_PROBLEMS) {
            assertTrue("Title empty in $p", p.title.isNotBlank())
            assertTrue("Hand empty in $p", p.heroHand.isNotBlank())
            assertTrue("Board empty in $p", p.board.isNotBlank())
            assertTrue("Pot non-positive in $p", p.potBb > 0.0)
            assertTrue("toCall negative in $p", p.toCallBb >= 0.0)
            assertTrue("Explanation empty in $p", p.explanation.isNotBlank())
            // Board 形式應為 3、4 或 5 張，以空白分隔
            val boardTokens = p.board.trim().split(Regex("\\s+"))
            assertTrue(
                "Board should be 3..5 cards in ${p.title}, got ${boardTokens.size}",
                boardTokens.size in 3..5
            )
            // Hand 形式應為 2 張，以空白分隔
            val handTokens = p.heroHand.trim().split(Regex("\\s+"))
            assertTrue(
                "Hand should be 2 cards in ${p.title}, got ${handTokens.size}",
                handTokens.size == 2
            )
        }
    }

    @Test fun titlesAreUnique() {
        val titles = TrainerProblemBank.POSTFLOP_PROBLEMS.map { it.title }
        assertTrue(
            "Titles must be unique; duplicates = ${titles.groupBy { it }.filter { it.value.size > 1 }.keys}",
            titles.size == titles.toSet().size
        )
    }

    @Test fun preflopProblemIsDeterministicGivenSeed() {
        val rng1 = Random(1234L)
        val rng2 = Random(1234L)
        val p1 = TrainerProblemBank.randomPreflopProblem(rng1)
        val p2 = TrainerProblemBank.randomPreflopProblem(rng2)
        assertNotNull(p1)
        assertTrue("Same seed should yield same hand", p1.hand == p2.hand)
        assertTrue("Same seed should yield same scenario", p1.scenario == p2.scenario)
    }
}
