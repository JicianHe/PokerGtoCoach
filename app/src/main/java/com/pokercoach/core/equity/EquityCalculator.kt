package com.pokercoach.core.equity

import com.pokercoach.core.eval.HandEvaluator
import com.pokercoach.core.model.Card
import com.pokercoach.core.model.HoleCards
import kotlin.random.Random

/**
 * 翻後手牌勝率（equity）蒙地卡羅模擬器。
 *
 * 用途：
 *  - HUD「決策前」面板顯示 hero 對對手隨機範圍的即時勝率
 *  - AI 翻後決策的精準依據（取代純啟發式）
 *  - 訓練模式驗證學員選擇
 *
 * 演算法：
 *  1. 從 52 張牌中扣除 hero 手牌與已知公共牌
 *  2. 隨機抽 2 張當對手底牌 + 補齊 5 張板面
 *  3. 比較最佳 5 張，勝 = 1，平 = 0.5
 *  4. 重複 N 次取平均
 *
 * 效能（Pixel 7 / Tab S11 等級）：
 *  - 1000 次 ~30ms（HUD 即時更新 OK）
 *  - 5000 次 ~150ms（訓練模式精算 OK）
 */
object EquityCalculator {

    data class Result(
        val winRate: Double,        // 0..1，含半平
        val ties: Double,           // 純平局比例
        val iterations: Int
    ) {
        val equity: Double get() = winRate
        fun toPercent(): String = "${"%.1f".format(winRate * 100)}%"
    }

    /**
     * vs 單一隨機對手手牌。
     */
    fun heroVsRandom(
        hero: HoleCards,
        knownBoard: List<Card> = emptyList(),
        iterations: Int = 1000,
        random: Random = Random.Default
    ): Result {
        val deadCards = setOf(hero.first, hero.second) + knownBoard
        val live = Card.fullDeck().filter { it !in deadCards }

        var wins = 0.0
        var ties = 0
        repeat(iterations) {
            val shuffled = live.shuffled(random)
            val villain = HoleCards(shuffled[0], shuffled[1])
            val needBoard = 5 - knownBoard.size
            val board = knownBoard + shuffled.subList(2, 2 + needBoard)

            val heroScore = HandEvaluator.evaluate(
                listOf(hero.first, hero.second) + board
            )
            val villainScore = HandEvaluator.evaluate(
                listOf(villain.first, villain.second) + board
            )
            when {
                heroScore > villainScore -> wins += 1.0
                heroScore < villainScore -> { /* loss */ }
                else -> { wins += 0.5; ties++ }
            }
        }
        return Result(
            winRate = wins / iterations,
            ties = ties.toDouble() / iterations,
            iterations = iterations
        )
    }

    /**
     * vs 對手「範圍」（限定 169 種類別的子集合，並用組合數加權）。
     */
    fun heroVsRange(
        hero: HoleCards,
        villainRange: Set<com.pokercoach.core.model.HandClass>,
        knownBoard: List<Card> = emptyList(),
        iterations: Int = 1000,
        random: Random = Random.Default
    ): Result {
        if (villainRange.isEmpty()) {
            return heroVsRandom(hero, knownBoard, iterations, random)
        }
        val deadCards = setOf(hero.first, hero.second) + knownBoard
        val live = Card.fullDeck().filter { it !in deadCards }

        // 預先列出對手所有可能的具體 hand 組合（card pair）
        val villainCombos: List<Pair<Card, Card>> = buildList {
            for (i in live.indices) for (j in i + 1 until live.size) {
                val c1 = live[i]; val c2 = live[j]
                val hc = com.pokercoach.core.model.HandClass.from(c1, c2)
                if (hc in villainRange) add(c1 to c2)
            }
        }
        if (villainCombos.isEmpty()) return heroVsRandom(hero, knownBoard, iterations, random)

        var wins = 0.0
        var ties = 0
        repeat(iterations) {
            val (vc1, vc2) = villainCombos.random(random)
            val remaining = live.filter { it != vc1 && it != vc2 }.shuffled(random)
            val needBoard = 5 - knownBoard.size
            val board = knownBoard + remaining.subList(0, needBoard)

            val heroScore = HandEvaluator.evaluate(
                listOf(hero.first, hero.second) + board
            )
            val villainScore = HandEvaluator.evaluate(
                listOf(vc1, vc2) + board
            )
            when {
                heroScore > villainScore -> wins += 1.0
                heroScore < villainScore -> { /* loss */ }
                else -> { wins += 0.5; ties++ }
            }
        }
        return Result(
            winRate = wins / iterations,
            ties = ties.toDouble() / iterations,
            iterations = iterations
        )
    }
}
