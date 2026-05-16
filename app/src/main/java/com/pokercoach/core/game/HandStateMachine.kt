package com.pokercoach.core.game

import com.pokercoach.core.eval.HandEvaluator
import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HoleCards
import com.pokercoach.core.model.Position
import kotlin.random.Random

/**
 * 6-Max 無限注德州撲克單手牌狀態機。
 *
 * 設計原則：
 *  - **純函數轉移**：apply(action) 回傳新 TableState，不就地修改。
 *  - **AI 決策不在這裡做**：本類只判斷「合法性 + 狀態演進 + 結算」。
 *    AI 由 PokerAi.kt 計算 Action，再餵進來。
 *  - **下注單位 = bb（big blind）**：與 GTO 文獻、求解器一致。
 *
 * 配置（可由建構子注入，預設值適合 100bb 6-Max cash game）：
 *   - smallBlind = 0.5 bb, bigBlind = 1.0 bb
 *   - 起始 stack = 100 bb
 *   - minRaise 初始 = 1 bb（即至少加到 currentBet + bb）
 */
class HandStateMachine(
    private val config: Config = Config(),
    private val random: Random = Random.Default
) {
    data class Config(
        val smallBlind: Double = 0.5,
        val bigBlind: Double = 1.0,
        val startingStack: Double = 100.0
    )

    /**
     * 開新的一手牌。
     *
     * @param seatInfo 玩家種類 + 名稱（依 seatIndex 0..5）。長度必須 = 6。
     * @param buttonSeat 此手按鈕座位（用於決定 SB/BB 與行動順序）。
     */
    fun startHand(
        handNumber: Int,
        seatInfo: List<SeatInit>,
        buttonSeat: Int,
        existingStacks: Map<Int, Double>? = null
    ): TableState {
        require(seatInfo.size == 6) { "Must have 6 seats, got ${seatInfo.size}" }
        require(buttonSeat in 0..5)

        val positions = assignPositions(buttonSeat)
        val deck = Deck.shuffled(random)

        // 發兩張底牌（依翻前行動順序，但實際上座位順序也可，因為都從同個 deck）
        val players = (0..5).map { seat ->
            val info = seatInfo[seat]
            val hole = HoleCards(deck.draw(), deck.draw())
            Player(
                seatIndex = seat,
                name = info.name,
                kind = info.kind,
                position = positions.getValue(seat),
                stack = existingStacks?.get(seat) ?: config.startingStack,
                holeCards = hole,
                status = PlayerStatus.ACTIVE
            )
        }

        // 下盲注
        val sbSeat = players.first { it.position == Position.SB }.seatIndex
        val bbSeat = players.first { it.position == Position.BB }.seatIndex
        val withBlinds = players.map { p ->
            when (p.seatIndex) {
                sbSeat -> postBlind(p, config.smallBlind)
                bbSeat -> postBlind(p, config.bigBlind)
                else -> p
            }
        }

        // 翻前先行動者：UTG（6-Max 中 BTN+1=SB 之前的位置 = UTG）
        val firstActor = withBlinds.first { it.position == Position.UTG }.seatIndex

        val log = listOf(
            GameEvent.HandStarted(handNumber, buttonSeat),
            GameEvent.BlindsPosted(sbSeat, bbSeat, config.smallBlind, config.bigBlind),
            GameEvent.HoleCardsDealt(0)
        )

        return TableState(
            handNumber = handNumber,
            street = Street.PREFLOP,
            players = withBlinds,
            buttonSeat = buttonSeat,
            board = emptyList(),
            pot = config.smallBlind + config.bigBlind,
            currentBet = config.bigBlind,
            minRaise = config.bigBlind,         // 初始最小加注增量 = 1bb
            actorSeat = firstActor,
            lastAggressorSeat = bbSeat,         // 大盲被視為「上一個加注者」用於 closing action
            log = log
        )
    }

    /**
     * 套用一個行動。會校驗合法性（非法行動拋 IllegalStateException）。
     *
     * 回傳新的 TableState，可能：
     *  - 街內推進（換下一個 actor）
     *  - 街切換（清算 betting round → deal flop/turn/river）
     *  - 手牌結束（fold to one player 或 showdown）
     */
    fun apply(state: TableState, action: Action): TableState {
        val seat = state.actorSeat
            ?: error("No actor; hand may be over or awaiting street deal")
        val p = state.player(seat)
        check(p.canAct) { "Player at seat $seat cannot act (status=${p.status})" }

        val newPlayers = state.players.toMutableList()
        var newPot = state.pot
        var newCurrentBet = state.currentBet
        var newMinRaise = state.minRaise
        var newLastAggressor = state.lastAggressorSeat

        when (action) {
            is Action.Fold -> {
                newPlayers[seat] = p.copy(
                    status = PlayerStatus.FOLDED,
                    hasActedThisStreet = true
                )
            }
            is Action.Check -> {
                check(state.toCall(seat) == 0.0) { "Cannot CHECK when facing a bet" }
                newPlayers[seat] = p.copy(hasActedThisStreet = true)
            }
            is Action.Call -> {
                val toCall = state.toCall(seat)
                check(toCall > 0.0) { "Cannot CALL when toCall = 0 (use CHECK)" }
                val pay = minOf(toCall, p.stack)
                newPlayers[seat] = p.copy(
                    stack = p.stack - pay,
                    committedThisStreet = p.committedThisStreet + pay,
                    committedTotal = p.committedTotal + pay,
                    status = if (p.stack - pay <= 1e-9) PlayerStatus.ALL_IN else p.status,
                    hasActedThisStreet = true
                )
                newPot += pay
            }
            is Action.Raise -> {
                val raiseTo = action.amount
                val toCall = state.toCall(seat)
                val pay = raiseTo - p.committedThisStreet

                check(pay > 0.0) { "Raise amount $raiseTo not greater than committed ${p.committedThisStreet}" }
                check(pay <= p.stack + 1e-9) { "Raise $raiseTo exceeds stack ${p.stack}" }

                val increment = raiseTo - state.currentBet
                val isAllIn = (pay >= p.stack - 1e-9)
                check(increment >= newMinRaise - 1e-9 || isAllIn) {
                    "Raise increment $increment < minRaise $newMinRaise (and not all-in)"
                }

                newPlayers[seat] = p.copy(
                    stack = p.stack - pay,
                    committedThisStreet = raiseTo,
                    committedTotal = p.committedTotal + pay,
                    status = if (isAllIn) PlayerStatus.ALL_IN else p.status,
                    hasActedThisStreet = true
                )
                newPot += pay
                newCurrentBet = raiseTo
                newMinRaise = maxOf(newMinRaise, increment)
                newLastAggressor = seat

                // 加注後，所有其他「尚未 all-in/fold」玩家必須重新行動
                for (i in newPlayers.indices) {
                    if (i == seat) continue
                    val pp = newPlayers[i]
                    if (pp.status == PlayerStatus.ACTIVE) {
                        newPlayers[i] = pp.copy(hasActedThisStreet = false)
                    }
                }
            }
        }

        val intermediate = state.copy(
            players = newPlayers,
            pot = newPot,
            currentBet = newCurrentBet,
            minRaise = newMinRaise,
            lastAggressorSeat = newLastAggressor,
            actorSeat = null,
            log = state.log + GameEvent.ActionTaken(seat, state.street, action, newPot)
        )

        return advance(intermediate)
    }

    // ---------------------------------------------------------------------
    // 推進：找下一個 actor，或結算街、結算手牌
    // ---------------------------------------------------------------------
    private fun advance(state: TableState): TableState {
        // 1) 只剩一個 in-hand 玩家 → 直接結束
        val inHand = state.activePlayers
        if (inHand.size == 1) {
            return endHandUncontested(state, inHand.first().seatIndex)
        }

        // 2) 所有人都 all-in 或只剩一個 active 玩家可行動 → run out board
        val canAct = state.playersCanStillAct
        val needsToAct = canAct.filter {
            !it.hasActedThisStreet || it.committedThisStreet < state.currentBet
        }

        if (needsToAct.isEmpty()) {
            // 街結束
            return endStreet(state)
        }

        // 3) 找下一個 actor：從目前 actor 之後依座位順序找最近的 needsToAct
        val startSeat = state.log.filterIsInstance<GameEvent.ActionTaken>().lastOrNull()?.seat
            ?: actorBeforeStreetStart(state)
        val next = nextActor(state, startSeat)
        return state.copy(actorSeat = next)
    }

    private fun nextActor(state: TableState, fromSeat: Int): Int? {
        for (offset in 1..6) {
            val seat = (fromSeat + offset) % 6
            val p = state.player(seat)
            if (!p.canAct) continue
            val needsAct = !p.hasActedThisStreet || p.committedThisStreet < state.currentBet
            if (needsAct) return seat
        }
        return null
    }

    /** 街開始前的「虛擬 last actor」（用於 nextActor 的起點）。 */
    private fun actorBeforeStreetStart(state: TableState): Int {
        return when (state.street) {
            Street.PREFLOP -> state.players.first { it.position == Position.BB }.seatIndex
            else -> state.buttonSeat   // postflop: SB 先行動，BTN 是「上一個」
        }
    }

    private fun endStreet(state: TableState): TableState {
        // 結算當街：committedThisStreet 已併入 pot（在 apply 時即時加），這裡只需清歸 0
        val cleared = state.players.map {
            it.copy(committedThisStreet = 0.0, hasActedThisStreet = false)
        }
        val nextStreet = when (state.street) {
            Street.PREFLOP -> Street.FLOP
            Street.FLOP -> Street.TURN
            Street.TURN -> Street.RIVER
            Street.RIVER -> Street.SHOWDOWN
            Street.SHOWDOWN -> Street.SHOWDOWN
        }

        if (nextStreet == Street.SHOWDOWN) {
            return endHandShowdown(state.copy(players = cleared, street = Street.SHOWDOWN))
        }

        // 發公共牌：從未用過的牌中抽（為簡化，這裡用一個獨立 Deck 從未用過的牌中抽）
        val usedCards = state.players.flatMap {
            listOfNotNull(it.holeCards?.first, it.holeCards?.second)
        } + state.board
        val remainingDeck = com.pokercoach.core.model.Card.fullDeck()
            .filter { it !in usedCards }
            .toMutableList()
            .also { it.shuffle(random) }

        val newCards = when (nextStreet) {
            Street.FLOP -> List(3) { remainingDeck.removeAt(0) }
            Street.TURN, Street.RIVER -> listOf(remainingDeck.removeAt(0))
            else -> emptyList()
        }
        val newBoard = state.board + newCards

        // 翻後第一個 actor：SB 起，順時針找第一個 canAct 玩家
        val sbSeat = state.players.first { it.position == Position.SB }.seatIndex
        val firstPostflopActor = run {
            for (offset in 0..5) {
                val seat = (sbSeat + offset) % 6
                if (cleared.first { it.seatIndex == seat }.canAct) return@run seat
            }
            null
        }

        val newState = state.copy(
            street = nextStreet,
            players = cleared,
            board = newBoard,
            currentBet = 0.0,
            minRaise = config.bigBlind,
            actorSeat = firstPostflopActor,
            lastAggressorSeat = null,
            log = state.log + GameEvent.StreetDealt(nextStreet, newCards)
        )

        // 如果所有人都 all-in，沒人可動，遞迴 endStreet 直到 SHOWDOWN
        return if (firstPostflopActor == null) endStreet(newState) else newState
    }

    // ---------------------------------------------------------------------
    // 結算
    // ---------------------------------------------------------------------
    private fun endHandUncontested(state: TableState, winnerSeat: Int): TableState {
        val winner = state.player(winnerSeat)
        val payout = state.pot
        val newPlayers = state.players.map {
            if (it.seatIndex == winnerSeat) it.copy(stack = it.stack + payout) else it
        }
        return state.copy(
            players = newPlayers,
            pot = 0.0,
            actorSeat = null,
            street = Street.SHOWDOWN,
            log = state.log + GameEvent.HandEnded(
                winners = listOf(winnerSeat),
                amounts = mapOf(winnerSeat to payout),
                reason = "All opponents folded"
            )
        )
    }

    private fun endHandShowdown(state: TableState): TableState {
        // 簡化：不處理 side pot（Phase 5 補完）。比較所有未 fold 玩家的 7-card 最佳 5 張。
        val contenders = state.players.filter { it.isInHand }
        val scored = contenders.map { p ->
            val seven = listOf(p.holeCards!!.first, p.holeCards.second) + state.board
            p.seatIndex to HandEvaluator.evaluate(seven)
        }
        val bestValue = scored.maxOf { it.second.value }
        val winners = scored.filter { it.second.value == bestValue }.map { it.first }
        val share = state.pot / winners.size
        val newPlayers = state.players.map {
            if (it.seatIndex in winners) it.copy(stack = it.stack + share) else it
        }
        val amounts = winners.associateWith { share }
        return state.copy(
            players = newPlayers,
            pot = 0.0,
            actorSeat = null,
            street = Street.SHOWDOWN,
            log = state.log + GameEvent.HandEnded(
                winners = winners,
                amounts = amounts,
                reason = "Showdown"
            )
        )
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    private fun postBlind(p: Player, amount: Double): Player {
        val pay = minOf(amount, p.stack)
        return p.copy(
            stack = p.stack - pay,
            committedThisStreet = pay,
            committedTotal = pay,
            status = if (p.stack - pay <= 1e-9) PlayerStatus.ALL_IN else p.status,
            hasActedThisStreet = false  // 盲注玩家翻前最後行動
        )
    }

    /** 從 button seat 推導每座位的 Position（6-Max）。 */
    private fun assignPositions(buttonSeat: Int): Map<Int, Position> {
        // 順時針：BTN, SB, BB, UTG, HJ, CO
        val order = listOf(Position.BTN, Position.SB, Position.BB, Position.UTG, Position.HJ, Position.CO)
        return order.withIndex().associate { (i, pos) -> ((buttonSeat + i) % 6) to pos }
    }

    /** 給 startHand 用的座位初始化資料。 */
    data class SeatInit(val name: String, val kind: PlayerKind)
}
