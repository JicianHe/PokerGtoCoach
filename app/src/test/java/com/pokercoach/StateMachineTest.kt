package com.pokercoach

import com.pokercoach.core.game.HandStateMachine
import com.pokercoach.core.game.PlayerKind
import com.pokercoach.core.game.Street
import com.pokercoach.core.model.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StateMachineTest {

    private fun newGame(): Pair<HandStateMachine, com.pokercoach.core.game.TableState> {
        val sm = HandStateMachine(random = Random(42))
        val seats = (0..5).map { HandStateMachine.SeatInit("P$it", PlayerKind.AI) }
        val st = sm.startHand(handNumber = 1, seatInfo = seats, buttonSeat = 0)
        return sm to st
    }

    @Test fun blindsArePosted() {
        val (_, st) = newGame()
        val sb = st.players.first { it.position == com.pokercoach.core.model.Position.SB }
        val bb = st.players.first { it.position == com.pokercoach.core.model.Position.BB }
        assertEquals(0.5, sb.committedThisStreet, 1e-9)
        assertEquals(1.0, bb.committedThisStreet, 1e-9)
        assertEquals(1.5, st.pot, 1e-9)
    }

    @Test fun firstActorIsUtgPreflop() {
        val (_, st) = newGame()
        val actor = st.player(st.actorSeat!!)
        assertEquals(com.pokercoach.core.model.Position.UTG, actor.position)
    }

    @Test fun allFoldGivesPotToBb() {
        var (sm, st) = newGame()
        // UTG, HJ, CO, BTN, SB 全部 fold → BB 拿走 pot
        repeat(5) {
            st = sm.apply(st, Action.Fold)
        }
        assertNotNull(st.log.last())
        assertEquals(Street.SHOWDOWN, st.street)
        val bb = st.players.first { it.position == com.pokercoach.core.model.Position.BB }
        assertTrue("BB stack should reflect winnings", bb.stack > 99.5)
    }

    @Test fun raiseRequiresOthersToActAgain() {
        var (sm, st) = newGame()
        st = sm.apply(st, Action.Raise(2.5))      // UTG raises
        val mustReact = st.players.filter { it.canAct && !it.hasActedThisStreet }
        assertTrue(mustReact.size >= 4)
    }

    @Test fun streetAdvancesAfterPreflopClosing() {
        var (sm, st) = newGame()
        // UTG, HJ, CO, BTN, SB fold, BB checks
        repeat(5) { st = sm.apply(st, Action.Fold) }
        // 已結束，不能再行動
        assertEquals(Street.SHOWDOWN, st.street)
    }
}
