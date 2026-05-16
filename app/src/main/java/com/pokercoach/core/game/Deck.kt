package com.pokercoach.core.game

import com.pokercoach.core.model.Card
import kotlin.random.Random

/**
 * 可重現的牌堆：吃 [Random] 作為亂源（測試用固定 seed，正式可用系統亂源）。
 *
 * 使用方式：
 *   val deck = Deck.shuffled(Random(42))
 *   val c1 = deck.draw()
 *   val rest = deck.drawMany(5)
 */
class Deck private constructor(
    private val cards: ArrayDeque<Card>
) {
    val remaining: Int get() = cards.size

    fun draw(): Card {
        check(cards.isNotEmpty()) { "Deck exhausted" }
        return cards.removeFirst()
    }

    fun drawMany(n: Int): List<Card> {
        require(n in 0..cards.size) { "Cannot draw $n from deck of size ${cards.size}" }
        return List(n) { cards.removeFirst() }
    }

    /** Burn one card (Texas Hold'em pre-flop/turn/river burning). */
    fun burn() { draw() }

    companion object {
        fun shuffled(random: Random = Random.Default): Deck {
            val list = Card.fullDeck().toMutableList()
            list.shuffle(random)
            return Deck(ArrayDeque(list))
        }
    }
}
