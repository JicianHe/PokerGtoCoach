package com.pokercoach.core.game

import com.pokercoach.core.model.Card
import com.pokercoach.core.model.HoleCards
import com.pokercoach.core.model.Position

/** 撲克四街。 */
enum class Street { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }

/** 玩家種類（用於 UI 與決策路徑切換）。 */
enum class PlayerKind { HUMAN, AI }

/** 玩家狀態。 */
enum class PlayerStatus { ACTIVE, FOLDED, ALL_IN, SITTING_OUT }

/**
 * 一個座位的玩家狀態快照（不可變；每次 action 後 copy 出新版本）。
 *
 *  - stack：剩餘籌碼（bb）
 *  - committedThisStreet：當街已投入（bb）
 *  - committedTotal：本手總投入（bb）
 */
data class Player(
    val seatIndex: Int,
    val name: String,
    val kind: PlayerKind,
    val position: Position,
    val stack: Double,
    val holeCards: HoleCards? = null,
    val status: PlayerStatus = PlayerStatus.ACTIVE,
    val committedThisStreet: Double = 0.0,
    val committedTotal: Double = 0.0,
    val hasActedThisStreet: Boolean = false
) {
    val isInHand: Boolean
        get() = status == PlayerStatus.ACTIVE || status == PlayerStatus.ALL_IN

    val canAct: Boolean
        get() = status == PlayerStatus.ACTIVE && stack > 0.0
}

/**
 * 牌桌不可變快照。所有變動回傳新的 TableState（FP 風格，便於 Compose 訂閱 / undo）。
 */
data class TableState(
    val handNumber: Int,
    val street: Street,
    val players: List<Player>,
    val buttonSeat: Int,
    val board: List<Card>,
    val pot: Double,
    val currentBet: Double,            // 當街最高下注額（to-amount）
    val minRaise: Double,              // 最小加注增量（bb）
    val actorSeat: Int?,               // null 表示本街結束 / 牌局結束
    val lastAggressorSeat: Int?,       // 最後加注者；街換時清除
    val log: List<GameEvent> = emptyList()
) {
    val activePlayers: List<Player> get() = players.filter { it.isInHand }
    val playersCanStillAct: List<Player> get() = players.filter { it.canAct }

    fun player(seat: Int): Player = players.first { it.seatIndex == seat }

    fun toCall(seat: Int): Double {
        val p = player(seat)
        return (currentBet - p.committedThisStreet).coerceAtLeast(0.0)
    }

    fun isHandOver(): Boolean =
        activePlayers.size <= 1 || street == Street.SHOWDOWN
}

/** 牌局事件日誌（用於 Phase 4 HUD 點評）。 */
sealed class GameEvent {
    data class HandStarted(val handNumber: Int, val buttonSeat: Int) : GameEvent()
    data class BlindsPosted(val sbSeat: Int, val bbSeat: Int, val sb: Double, val bb: Double) : GameEvent()
    data class HoleCardsDealt(val seat: Int) : GameEvent()
    data class StreetDealt(val street: Street, val newBoardCards: List<Card>) : GameEvent()
    data class ActionTaken(
        val seat: Int,
        val street: Street,
        val action: com.pokercoach.core.model.Action,
        val potAfter: Double
    ) : GameEvent()
    data class HandEnded(
        val winners: List<Int>,
        val amounts: Map<Int, Double>,
        val reason: String
    ) : GameEvent()
}
