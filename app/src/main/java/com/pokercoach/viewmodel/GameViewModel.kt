package com.pokercoach.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import com.pokercoach.core.stats.DecisionGrader
import com.pokercoach.data.HandHistoryRepository
import com.pokercoach.data.SettingsRepository
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.feedback.SoundManager
import com.pokercoach.ui.theme.Strings
import com.pokercoach.ui.theme.VerdictLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 主 ViewModel：協調狀態機 + AI + HUD 暫停教學 + 統計與歷史持久化。
 *
 * 教學流程：
 *   1) 輪到 Hero（玩家）→ UI 顯示 ActionBar
 *   2) Hero 行動 → grade + 記錄統計 → 進入 HeroReview pause
 *   3) 玩家點「繼續」→ AI 依序行動（每次有 650–900ms 思考延遲）
 *   4) 手結束 → 寫入 HandHistory，顯示 HandComplete pause
 */
class GameViewModel(
    private val settingsRepo: SettingsRepository,
    private val statsRepo: StatsRepository,
    private val historyRepo: HandHistoryRepository,
    private val soundManager: SoundManager,
    private val random: Random = Random(System.currentTimeMillis())
) : ViewModel() {

    private val machine = HandStateMachine(random = random)
    private val aiBySeat: MutableMap<Int, PokerAi> = mutableMapOf()

    private val _table = MutableStateFlow(initialTable())
    val table: StateFlow<TableState> = _table.asStateFlow()

    val heroSeat: Int = 0

    private val _pause = MutableStateFlow<PauseState>(PauseState.None)
    val pause: StateFlow<PauseState> = _pause.asStateFlow()

    private val _lastAiDecision = MutableStateFlow<PokerAi.Decision?>(null)
    val lastAiDecision: StateFlow<PokerAi.Decision?> = _lastAiDecision.asStateFlow()

    private val _heroRecommendation = MutableStateFlow<EvCalculator.Recommendation?>(null)
    val heroRecommendation: StateFlow<EvCalculator.Recommendation?> = _heroRecommendation.asStateFlow()

    /** 設定 Flow（音效/震動/動畫/難度），讓 UI 端可訂閱以套用。 */
    val settings: StateFlow<SettingsRepository.Settings> =
        settingsRepo.settingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.Settings()
        )

    private var handCounter = 1
    private var rotatingButton = 0

    init {
        setupAi(SettingsRepository.Difficulty.MIXED_PROFILES)
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                soundManager.enabled = s.soundOn
                setupAi(s.difficulty)
            }
        }
        viewModelScope.launch { driveAi() }
        updateHeroRecommendationIfPreflop()
    }

    sealed class PauseState {
        object None : PauseState()
        data class HeroReview(
            val action: Action,
            val recommendation: EvCalculator.Recommendation?,
            val verdict: VerdictLevel
        ) : PauseState()
        data class HandComplete(val event: GameEvent.HandEnded) : PauseState()
    }

    // -----------------------------------------------------------------
    // Public actions
    // -----------------------------------------------------------------

    fun onHeroAction(action: Action) {
        val st = _table.value
        if (st.actorSeat != heroSeat) return

        val rec = _heroRecommendation.value
        val verdict = if (rec != null) {
            DecisionGrader.grade(action.kind, rec)
        } else {
            VerdictLevel.Unknown
        }

        // 寫入統計
        val hero = st.player(heroSeat)
        viewModelScope.launch {
            statsRepo.recordDecision(
                verdict = DecisionGrader.toBucket(verdict),
                position = hero.position,
                street = st.street
            )
        }

        // 音效
        playSfxFor(action)

        applyAction(action)
        // 若該行動直接結束本手（例：對手只剩一人被迫 fold、或進入 showdown），
        // checkHandEnded 已將 pause 設為 HandComplete；此時不可覆蓋為 HeroReview，
        // 否則使用者按「繼續」後會卡在 actorSeat=null 永久顯示「發牌中...」。
        if (_pause.value !is PauseState.HandComplete) {
            _pause.value = PauseState.HeroReview(action, rec, verdict)
        }
    }

    fun continueAfterReview() {
        _pause.value = PauseState.None
        viewModelScope.launch { driveAi() }
    }

    fun startNextHand() {
        rotatingButton = (rotatingButton + 1) % 6
        handCounter += 1

        val stacks = _table.value.players.associate { it.seatIndex to it.stack }
        _table.value = machine.startHand(
            handNumber = handCounter,
            seatInfo = seatInits(),
            buttonSeat = rotatingButton,
            existingStacks = stacks
        )
        _pause.value = PauseState.None
        _lastAiDecision.value = null
        soundManager.play(SoundManager.Sfx.DEAL)
        updateHeroRecommendationIfPreflop()
        viewModelScope.launch { driveAi() }
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

        // 持久化：手數 + history + 英雄輸贏
        viewModelScope.launch {
            statsRepo.incrementHands()
            statsRepo.recordHeroResult(heroWon = ended.winners.contains(heroSeat))
            HandHistoryRepository.fromLog(
                handNumber = state.handNumber,
                buttonSeat = state.buttonSeat,
                log = state.log,
                finalBoard = state.board,
                players = state.players,
                heroSeat = heroSeat
            )?.let { historyRepo.append(it) }
        }
    }

    private suspend fun driveAi() {
        while (true) {
            val st = _table.value
            if (st.actorSeat == null) {
                // 保險：若狀態機已輸出 HandEnded 但 pause 尚未同步（race / 早期覆寫），
                // 在此補上 HandComplete，避免 UI 永久卡在「發牌中...」。
                val ended = st.log.lastOrNull() as? GameEvent.HandEnded
                if (ended != null && _pause.value !is PauseState.HandComplete) {
                    _pause.value = PauseState.HandComplete(ended)
                }
                return
            }
            if (st.actorSeat == heroSeat) {
                updateHeroRecommendationIfPreflop()
                return
            }
            if (_pause.value !is PauseState.None) return

            val actor = st.actorSeat!!
            val ai = aiBySeat.getValue(actor)
            delay(650L + (random.nextInt(0, 250)).toLong())

            val decision = ai.decide(st, actor)
            _lastAiDecision.value = decision
            playSfxFor(decision.action)
            applyAction(decision.action)
        }
    }

    private fun playSfxFor(action: Action) {
        when (action) {
            is Action.Call, is Action.Raise -> soundManager.play(SoundManager.Sfx.CHIP)
            is Action.Check, is Action.Fold -> soundManager.play(SoundManager.Sfx.BUTTON, 0.5f)
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
            priorRaises.size == 1 -> {
                val raiserPos = state.player(priorRaises.first().seat).position
                val sz = state.currentBet
                when (hero.position) {
                    Position.BB -> RangeScenario.BbVsRfi(raiserPos, sizeBb = sz)
                    Position.SB -> RangeScenario.SbVsRfi(raiserPos, sizeBb = sz)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun setupAi(difficulty: SettingsRepository.Difficulty) {
        val presets: List<PsychProfile> = when (difficulty) {
            SettingsRepository.Difficulty.PURE_GTO -> List(5) { PsychProfile.GTO }
            SettingsRepository.Difficulty.MIXED_PROFILES -> listOf(
                PsychProfile.GTO, PsychProfile.TAG, PsychProfile.LAG,
                PsychProfile.NIT, PsychProfile.STATION
            )
            SettingsRepository.Difficulty.FISH_POOL -> listOf(
                PsychProfile.STATION, PsychProfile.STATION, PsychProfile.LAG,
                PsychProfile.NIT, PsychProfile.STATION
            )
        }
        for (seat in 1..5) {
            aiBySeat[seat] = PokerAi(profile = presets[seat - 1], random = random)
        }
    }

    private fun seatInits(): List<HandStateMachine.SeatInit> = listOf(
        HandStateMachine.SeatInit(Strings.NAME_YOU, PlayerKind.HUMAN),
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

    // -----------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------
    class Factory(
        private val settingsRepo: SettingsRepository,
        private val statsRepo: StatsRepository,
        private val historyRepo: HandHistoryRepository,
        private val soundManager: SoundManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GameViewModel::class.java))
            return GameViewModel(settingsRepo, statsRepo, historyRepo, soundManager) as T
        }
    }
}
