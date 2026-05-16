package com.pokercoach.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokercoach.core.ai.PokerAi
import com.pokercoach.core.ai.PsychProfile
import com.pokercoach.core.ev.EvCalculator
import com.pokercoach.core.game.GameEvent
import com.pokercoach.core.game.HandStateMachine
import com.pokercoach.core.game.PlayerKind
import com.pokercoach.core.game.Street
import com.pokercoach.core.game.TableState
import com.pokercoach.core.model.Action
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 主 ViewModel：協調狀態機 + AI + HUD 暫停教學機制。
 *
 * 教學流程：
 *   1) 輪到 Hero（玩家）→ UI 顯示 ActionBar，等待玩家點擊
 *   2) Hero 行動 → 進入「點評階段」（pause），HUD 顯示與 GTO 的偏差
 *   3) 玩家點「繼續」→ AI 依序自動行動，每個 AI 動作前有 700ms 思考延遲
 *   4) 街結束自動推進；手牌結束顯示結算，再點「下一手」開新局
 */
class GameViewModel(
    private val random: Random = Random(System.currentTimeMillis())
) : ViewModel() {

    private val machine = HandStateMachine(random = random)
    private val aiBySeat: MutableMap<Int, PokerAi> = mutableMapOf()

    private val _table = MutableStateFlow(initialTable())
    val table: StateFlow<TableState> = _table.asStateFlow()

    // Hero 永遠在 seat 0
    val heroSeat: Int = 0

    /** 教學暫停狀態。 */
    private val _pause = MutableStateFlow<PauseState>(PauseState.None)
    val pause: StateFlow<PauseState> = _pause.asStateFlow()

    /** AI 上一個決策的內部推理（HUD 顯示）。 */
    private val _lastAiDecision = MutableStateFlow<PokerAi.Decision?>(null)
    val lastAiDecision: StateFlow<PokerAi.Decision?> = _lastAiDecision.asStateFlow()

    /** Hero 翻前推薦（每次輪到 Hero 翻前時更新）。 */
    private val _heroRecommendation = MutableStateFlow<EvCalculator.Recommendation?>(null)
    val heroRecommendation: StateFlow<EvCalculator.Recommendation?> = _heroRecommendation.asStateFlow()

    private var handCounter = 1
    private var rotatingButton = 0

    init {
        setupAi()
        // 起手即驅動 AI（如果 hero 不是首位行動者）
        viewModelScope.launch { driveAi() }
        updateHeroRecommendationIfPreflop()
    }

    sealed class PauseState {
        object None : PauseState()
        data class HeroReview(val action: Action, val recommendation: EvCalculator.Recommendation?) : PauseState()
        data class HandComplete(val event: GameEvent.HandEnded) : PauseState()
    }

    // -----------------------------------------------------------------
    // Public actions
    // -----------------------------------------------------------------

    fun onHeroAction(action: Action) {
        val st = _table.value
        if (st.actorSeat != heroSeat) return

        // 暫停顯示點評（翻前才有 recommendation）
        val rec = _heroRecommendation.value
        applyAction(action)
        _pause.value = PauseState.HeroReview(action, rec)
    }

    fun continueAfterReview() {
        _pause.value = PauseState.None
        viewModelScope.launch { driveAi() }
    }

    fun startNextHand() {
        rotatingButton = (rotatingButton + 1) % 6
        handCounter += 1

        // 保留 stacks
        val stacks = _table.value.players.associate { it.seatIndex to it.stack }
        _table.value = machine.startHand(
            handNumber = handCounter,
            seatInfo = seatInits(),
            buttonSeat = rotatingButton,
            existingStacks = stacks
        )
        _pause.value = PauseState.None
        _lastAiDecision.value = null
        updateHeroRecommendationIfPreflop()
        viewModelScope.launch { driveAi() }
    }

    fun setAiProfile(seat: Int, profile: PsychProfile) {
        aiBySeat[seat] = PokerAi(profile = profile, random = random)
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private fun applyAction(action: Action) {
        val newState = machine.apply(_table.value, action)
        _table.value = newState
        checkHandEnded(newState)
    }

    private fun checkHandEnded(state: TableState) {
        val ended = state.log.lastOrNull() as? GameEvent.HandEnded ?: return
        _pause.value = PauseState.HandComplete(ended)
    }

    private suspend fun driveAi() {
        while (true) {
            val st = _table.value
            if (st.actorSeat == null) return
            if (st.actorSeat == heroSeat) {
                updateHeroRecommendationIfPreflop()
                return
            }
            if (_pause.value !is PauseState.None) return

            val actor = st.actorSeat!!
            val ai = aiBySeat.getValue(actor)
            // 思考延遲：營造節奏，並利用協程在 UI 主執行緒外運算
            delay(650L + (random.nextInt(0, 250)).toLong())

            val decision = ai.decide(st, actor)
            _lastAiDecision.value = decision
            applyAction(decision.action)
        }
    }

    private fun updateHeroRecommendationIfPreflop() {
        val st = _table.value
        if (st.street != Street.PREFLOP) {
            _heroRecommendation.value = null
            return
        }
        val hero = st.players.first { it.seatIndex == heroSeat }
        val hole = hero.holeCards ?: return
        val scenario = inferHeroScenario(st)
        if (scenario != null && PreflopRangeManager.isSupported(scenario)) {
            _heroRecommendation.value = EvCalculator.recommend(scenario, hole)
        } else {
            _heroRecommendation.value = null
        }
    }

    private fun inferHeroScenario(state: TableState): RangeScenario? {
        val hero = state.players.first { it.seatIndex == heroSeat }
        val priorRaises = state.log.filterIsInstance<GameEvent.ActionTaken>()
            .filter { it.street == Street.PREFLOP && it.action is Action.Raise }
        return when {
            priorRaises.isEmpty() -> RangeScenario.Rfi(hero.position)
            priorRaises.size == 1 && hero.position == com.pokercoach.core.model.Position.BB -> {
                val raiserPos = state.player(priorRaises.first().seat).position
                if (raiserPos == com.pokercoach.core.model.Position.BTN)
                    RangeScenario.BbVsRfi(com.pokercoach.core.model.Position.BTN, sizeBb = state.currentBet)
                else null
            }
            else -> null
        }
    }

    private fun setupAi() {
        // Hero (0) 不需要 AI；其餘 5 個座位給不同畫像，增加學習多樣性
        val presets = listOf(
            PsychProfile.GTO,
            PsychProfile.TAG,
            PsychProfile.LAG,
            PsychProfile.NIT,
            PsychProfile.STATION
        )
        for (seat in 1..5) {
            aiBySeat[seat] = PokerAi(profile = presets[seat - 1], random = random)
        }
    }

    private fun seatInits(): List<HandStateMachine.SeatInit> = listOf(
        HandStateMachine.SeatInit("YOU",   PlayerKind.HUMAN),
        HandStateMachine.SeatInit("GTO",   PlayerKind.AI),
        HandStateMachine.SeatInit("TAG",   PlayerKind.AI),
        HandStateMachine.SeatInit("LAG",   PlayerKind.AI),
        HandStateMachine.SeatInit("Nit",   PlayerKind.AI),
        HandStateMachine.SeatInit("Fish",  PlayerKind.AI)
    )

    private fun initialTable(): TableState =
        machine.startHand(
            handNumber = 1,
            seatInfo = seatInits(),
            buttonSeat = 0
        )
}
