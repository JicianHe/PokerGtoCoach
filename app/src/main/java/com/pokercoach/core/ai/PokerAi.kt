package com.pokercoach.core.ai

import com.pokercoach.core.equity.EquityCalculator
import com.pokercoach.core.eval.HandEvaluator
import com.pokercoach.core.game.Street
import com.pokercoach.core.game.TableState
import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import com.pokercoach.ui.theme.Strings
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 單機 AI 決策引擎（強化版）。
 *
 * 翻前：
 *   - 從 PreflopRangeManager 取 GTO 混合策略（覆蓋 11 個情境）
 *   - 用 PsychProfile 偏移做剝削調整
 *   - 抽出最終純行動
 *
 * 翻後：
 *   - 用 EquityCalculator Monte Carlo（300 iter）估算對隨機對手範圍的勝率
 *   - 結合 pot odds + PsychProfile 做 bet/call/check/fold 決策
 *   - bet size 33%/66%/pot/all-in 依強度選擇
 *
 * 所有推理輸出為繁體中文（透過 ui.theme.Strings 工具）。
 */
class PokerAi(
    private val profile: PsychProfile = PsychProfile.GTO,
    private val random: Random = Random.Default,
    /** 翻後 Monte Carlo 迭代次數。300 在 Tab S11 約 8ms，可即時。 */
    private val equityIterations: Int = 300
) {
    /** 決策結果，附帶推理摘要（HUD 點評用）。 */
    data class Decision(
        val action: Action,
        val rationale: String,
        val baselineStrategy: MixedStrategy? = null,
        val adjustedStrategy: MixedStrategy? = null,
        val equity: Double? = null
    )

    fun decide(state: TableState, seat: Int): Decision {
        return when (state.street) {
            Street.PREFLOP -> decidePreflop(state, seat)
            Street.FLOP, Street.TURN, Street.RIVER -> decidePostflop(state, seat)
            Street.SHOWDOWN -> error("decide() called at SHOWDOWN; no action needed")
        }
    }

    // =====================================================================
    // Preflop
    // =====================================================================
    private fun decidePreflop(state: TableState, seat: Int): Decision {
        val me = state.player(seat)
        val hole = me.holeCards ?: error("AI seat $seat has no hole cards")
        val handClass = hole.handClass
        val scenario = inferPreflopScenario(state, seat)

        val baseline: MixedStrategy = if (scenario != null && PreflopRangeManager.isSupported(scenario)) {
            PreflopRangeManager.strategyFor(scenario, handClass)
        } else {
            heuristicPreflopStrategy(state, me.position, handClass)
        }

        val adjusted = applyPsychAdjustment(baseline, handClass)
        val kind = sampleAction(adjusted)
        val action = materializeAction(state, seat, kind, isPreflop = true)

        return Decision(
            action = action,
            rationale = Strings.aiPreflopRationale(
                position = me.position,
                hand = handClass,
                scenario = scenario?.displayNameZh,
                adjustedSummary = formatStrategy(adjusted),
                action = action
            ),
            baselineStrategy = baseline,
            adjustedStrategy = adjusted
        )
    }

    /**
     * 推測翻前情境（擴充版：覆蓋所有 RFI 與 BB / SB defend）。
     */
    private fun inferPreflopScenario(state: TableState, seat: Int): RangeScenario? {
        val me = state.player(seat)
        val priorRaises = state.log.filterIsInstance<com.pokercoach.core.game.GameEvent.ActionTaken>()
            .filter { it.street == Street.PREFLOP && it.action is Action.Raise }

        return when {
            priorRaises.isEmpty() && state.currentBet <= state.minRaise + 1e-9 -> {
                RangeScenario.Rfi(me.position)
            }
            priorRaises.size == 1 -> {
                val raiserSeat = priorRaises.first().seat
                val raiserPos = state.player(raiserSeat).position
                val sizeBb = state.currentBet
                when (me.position) {
                    Position.BB -> RangeScenario.BbVsRfi(raiserPos, sizeBb = roundSize(sizeBb))
                    Position.SB -> if (raiserPos == Position.BTN)
                        RangeScenario.SbVsRfi(raiserPos, sizeBb = roundSize(sizeBb))
                    else null
                    else -> null
                }
            }
            else -> null
        }
    }

    /** 標準化下注尺寸到 0.5bb 倍數，避免浮點誤差打亂 scenario key。 */
    private fun roundSize(bb: Double): Double {
        // 強制吸附到 2.5（最常見 open size）
        if (kotlin.math.abs(bb - 2.5) < 0.4) return 2.5
        return (kotlin.math.round(bb * 2.0) / 2.0)
    }

    /** 沒有 solver 範圍時的翻前 fallback。 */
    private fun heuristicPreflopStrategy(
        state: TableState,
        position: Position,
        hc: HandClass
    ): MixedStrategy {
        val strength = preflopStrength(hc)
        val toCall = state.currentBet - state.player(0).committedThisStreet
        val potOdds = if (toCall <= 0) 0.0 else toCall / (state.pot + toCall)

        return when {
            strength > 0.85 -> MixedStrategy(raise = 0.85, call = 0.15)
            strength > 0.65 -> MixedStrategy(raise = 0.30, call = 0.60, fold = 0.10)
            strength > 0.45 && potOdds < 0.30 -> MixedStrategy(call = 0.55, fold = 0.45)
            strength > 0.30 && potOdds < 0.20 -> MixedStrategy(call = 0.30, fold = 0.70)
            else -> MixedStrategy.PURE_FOLD
        }
    }

    private fun preflopStrength(hc: HandClass): Double {
        val hi = hc.highRank.value.toDouble()
        val lo = hc.lowRank.value.toDouble()
        val pairBonus = if (hc.isPair) 5.5 + (hi - 2.0) * 0.45 else 0.0
        val suitedBonus = if (hc.suited) 0.55 else 0.0
        val connector = when {
            hc.isPair -> 0.0
            (hi - lo) == 1.0 -> 0.85
            (hi - lo) == 2.0 -> 0.45
            else -> 0.0
        }
        val highCard = (hi - 2.0) / 12.0 * 3.5
        val lowCard = (lo - 2.0) / 12.0 * 1.6
        val raw = pairBonus + highCard + lowCard + suitedBonus + connector
        return (raw / 14.0).coerceIn(0.0, 1.0)
    }

    // =====================================================================
    // 心理剝削調整
    // =====================================================================
    private fun applyPsychAdjustment(base: MixedStrategy, hc: HandClass): MixedStrategy {
        if (profile == PsychProfile.GTO) return base

        var fold = base.fold
        var call = base.call
        var raise = base.raise
        var check = base.check

        if (profile.loosenessBias != 0.0 && fold > 0) {
            val shift = fold * profile.loosenessBias.coerceIn(-1.0, 1.0)
            if (shift > 0) {
                val moved = min(fold, shift)
                fold -= moved; call += moved
            } else {
                val moved = min(call, -shift)
                call -= moved; fold += moved
            }
        }

        if (profile.aggressionBias != 0.0) {
            if (profile.aggressionBias > 0 && call > 0) {
                val moved = call * profile.aggressionBias
                call -= moved; raise += moved
            } else if (profile.aggressionBias < 0 && raise > 0) {
                val moved = raise * (-profile.aggressionBias) * 0.5
                raise -= moved; call += moved
            }
        }

        if (profile.bluffBias > 0 && preflopStrength(hc) < 0.5 && fold > 0) {
            val moved = fold * profile.bluffBias * 0.3
            fold -= moved; raise += moved
        }

        if (profile.stickiness > 0 && fold > 0) {
            val moved = fold * profile.stickiness * 0.4
            fold -= moved; call += moved
        }

        val total = fold + call + raise + check
        if (total <= 0.0) return MixedStrategy.PURE_FOLD
        return normalize(MixedStrategy(
            fold = (fold / total).coerceIn(0.0, 1.0),
            check = (check / total).coerceIn(0.0, 1.0),
            call = (call / total).coerceIn(0.0, 1.0),
            raise = (raise / total).coerceIn(0.0, 1.0)
        ))
    }

    private fun normalize(s: MixedStrategy): MixedStrategy {
        val sum = s.fold + s.check + s.call + s.raise
        if (kotlin.math.abs(sum - 1.0) < 1e-9) return s
        return MixedStrategy(
            fold = s.fold / sum,
            check = s.check / sum,
            call = s.call / sum,
            raise = s.raise / sum
        )
    }

    private fun sampleAction(s: MixedStrategy): Action.Kind {
        val r = random.nextDouble()
        var acc = 0.0
        acc += s.fold;  if (r < acc) return Action.Kind.FOLD
        acc += s.check; if (r < acc) return Action.Kind.CHECK
        acc += s.call;  if (r < acc) return Action.Kind.CALL
        return Action.Kind.RAISE
    }

    /** 把 Kind 轉成具體 Action（特別是 RAISE 要決定 size）。 */
    private fun materializeAction(
        state: TableState,
        seat: Int,
        kind: Action.Kind,
        isPreflop: Boolean,
        equity: Double? = null
    ): Action {
        val me = state.player(seat)
        val toCall = state.toCall(seat)

        return when (kind) {
            Action.Kind.FOLD -> {
                if (toCall == 0.0) Action.Check else Action.Fold
            }
            Action.Kind.CHECK -> {
                if (toCall > 0.0) {
                    if (me.stack >= toCall) Action.Call else Action.Fold
                } else Action.Check
            }
            Action.Kind.CALL -> {
                if (toCall <= 0.0) Action.Check
                else Action.Call
            }
            Action.Kind.RAISE -> {
                val size = chooseRaiseSize(state, seat, isPreflop, equity)
                if (size <= state.currentBet || size > me.committedThisStreet + me.stack) {
                    if (toCall > 0) Action.Call else Action.Check
                } else Action.Raise(size)
            }
        }
    }

    private fun chooseRaiseSize(state: TableState, seat: Int, isPreflop: Boolean, equity: Double?): Double {
        val me = state.player(seat)
        val maxTo = me.committedThisStreet + me.stack

        return if (isPreflop) {
            val priorRaises = state.log.filterIsInstance<com.pokercoach.core.game.GameEvent.ActionTaken>()
                .count { it.street == Street.PREFLOP && it.action is Action.Raise }
            val target = when (priorRaises) {
                0 -> 2.5
                1 -> state.currentBet * 3.0
                else -> state.currentBet * 2.2
            }
            min(target, maxTo)
        } else {
            val strength = equity ?: postflopStrengthFallback(state, seat)
            val potFraction = when {
                strength > 0.85 -> 1.0
                strength > 0.65 -> 0.66
                strength > 0.40 -> 0.50
                else -> 0.33
            }
            val target = if (state.currentBet == 0.0) {
                state.pot * potFraction
            } else {
                state.currentBet + state.pot * potFraction
            }
            min(max(target, state.currentBet + state.minRaise), maxTo)
        }
    }

    // =====================================================================
    // Postflop（Monte Carlo equity 驅動）
    // =====================================================================
    private fun decidePostflop(state: TableState, seat: Int): Decision {
        val me = state.player(seat)
        val hole = me.holeCards!!
        val equityResult = EquityCalculator.heroVsRandom(
            hero = hole,
            knownBoard = state.board,
            iterations = equityIterations,
            random = random
        )
        val strength = equityResult.equity
        val toCall = state.toCall(seat)
        val potOdds = if (toCall <= 0) 0.0 else toCall / (state.pot + toCall)

        // 依勝率 vs 底池賠率決定基礎傾向
        var base = when {
            strength > 0.85 -> MixedStrategy(raise = 0.75, call = 0.25)
            strength > 0.65 -> MixedStrategy(raise = 0.55, call = 0.40, fold = 0.05)
            strength > 0.50 -> if (toCall == 0.0) MixedStrategy(raise = 0.40, check = 0.60)
                else MixedStrategy(call = 0.70, raise = 0.15, fold = 0.15)
            strength > potOdds + 0.05 -> if (toCall == 0.0) MixedStrategy(check = 0.75, raise = 0.25)
                else MixedStrategy(call = 0.65, fold = 0.25, raise = 0.10)
            strength > potOdds - 0.05 -> if (toCall == 0.0) MixedStrategy(check = 0.85, raise = 0.15)
                else MixedStrategy(call = 0.50, fold = 0.50)
            else -> if (toCall == 0.0) MixedStrategy(check = 0.90, raise = 0.10)
                else MixedStrategy.PURE_FOLD
        }

        base = applyPsychAdjustment(base, hole.handClass)

        val kind = sampleAction(base)
        val action = materializeAction(state, seat, kind, isPreflop = false, equity = strength)

        return Decision(
            action = action,
            rationale = Strings.aiPostflopRationale(
                street = state.street,
                strength = strength,
                toCall = toCall,
                potOdds = potOdds,
                summary = formatStrategy(base),
                action = action
            ),
            adjustedStrategy = base,
            equity = strength
        )
    }

    /** 沒有時間做 Monte Carlo 時的退化版本（純牌型強度）。 */
    private fun postflopStrengthFallback(state: TableState, seat: Int): Double {
        val me = state.player(seat)
        val hole = me.holeCards!!
        val seven = listOf(hole.first, hole.second) + state.board
        if (seven.size < 5) return preflopStrength(hole.handClass)

        val score = HandEvaluator.evaluate(seven)
        return when (score.category) {
            HandEvaluator.Category.STRAIGHT_FLUSH  -> 1.00
            HandEvaluator.Category.FOUR_OF_A_KIND  -> 0.98
            HandEvaluator.Category.FULL_HOUSE      -> 0.93
            HandEvaluator.Category.FLUSH           -> 0.85
            HandEvaluator.Category.STRAIGHT        -> 0.80
            HandEvaluator.Category.THREE_OF_A_KIND -> 0.72
            HandEvaluator.Category.TWO_PAIR        -> 0.62
            HandEvaluator.Category.ONE_PAIR        -> {
                val topBoardRank = state.board.maxOfOrNull { it.rank.value } ?: 0
                val pairRank = score.kickers.first()
                when {
                    pairRank >= topBoardRank -> 0.55
                    pairRank >= 10 -> 0.42
                    else -> 0.30
                }
            }
            HandEvaluator.Category.HIGH_CARD -> {
                val highRank = score.kickers.first()
                if (highRank >= 13) 0.20 else 0.10
            }
        }
    }

    // =====================================================================
    // 工具
    // =====================================================================
    private fun formatStrategy(s: MixedStrategy): String {
        val parts = mutableListOf<String>()
        if (s.raise > 0.001) parts += "加${(s.raise * 100).toInt()}"
        if (s.call  > 0.001) parts += "跟${(s.call  * 100).toInt()}"
        if (s.check > 0.001) parts += "過${(s.check * 100).toInt()}"
        if (s.fold  > 0.001) parts += "蓋${(s.fold  * 100).toInt()}"
        return parts.joinToString("/")
    }
}
