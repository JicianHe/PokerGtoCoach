package com.pokercoach.core.ai

import com.pokercoach.core.eval.HandEvaluator
import com.pokercoach.core.game.Street
import com.pokercoach.core.game.TableState
import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.HoleCards
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 單機 AI 決策引擎。
 *
 * 翻前：
 *   - 先查 PreflopRangeManager 取得 GTO 混合策略
 *   - 再用 PsychProfile 偏移做剝削調整（fold/call/raise 頻率重分配）
 *   - 用 random 抽出最終純行動
 *
 * 翻後（Phase 1 未提供 postflop solver 數據，這裡用啟發式）：
 *   - 計算手牌強度（HandEvaluator on hero hole+board）
 *   - 結合 pot odds 與 PsychProfile 做 bet/call/check/fold 決策
 *   - bet size 採 33% / 66% / pot / 2x pot 四檔，依強度與心理選擇
 *
 * 註：本檔案完全自包含；後續 Phase 5 可替換為 MCTS / CFR 簡化版而不動 API。
 */
class PokerAi(
    private val profile: PsychProfile = PsychProfile.GTO,
    private val random: Random = Random.Default
) {
    /** 決策結果，附帶推理摘要（HUD 點評用）。 */
    data class Decision(
        val action: Action,
        val rationale: String,
        val baselineStrategy: MixedStrategy? = null,
        val adjustedStrategy: MixedStrategy? = null
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
            // 沒有 solver 數據的情境（如 facing 3-bet）→ 用啟發式 fallback
            heuristicPreflopStrategy(state, me.position, handClass)
        }

        val adjusted = applyPsychAdjustment(baseline, handClass)
        val kind = sampleAction(adjusted)
        val action = materializeAction(state, seat, kind, isPreflop = true)

        return Decision(
            action = action,
            rationale = buildString {
                append("Preflop ${me.position.displayName} with ${handClass}: ")
                if (scenario != null) append("scenario=${scenario.displayName}, ")
                append("baseline=${formatStrategy(baseline)} ")
                append("adjusted=${formatStrategy(adjusted)} ")
                append("→ ${action}")
            },
            baselineStrategy = baseline,
            adjustedStrategy = adjusted
        )
    }

    /** 推測當前翻前情境（目前只覆蓋 RFI 與 BB vs BTN RFI）。 */
    private fun inferPreflopScenario(state: TableState, seat: Int): RangeScenario? {
        val me = state.player(seat)
        val priorRaises = state.log.filterIsInstance<com.pokercoach.core.game.GameEvent.ActionTaken>()
            .filter { it.street == Street.PREFLOP && it.action is Action.Raise }

        return when {
            priorRaises.isEmpty() && state.currentBet <= state.minRaise + 1e-9 -> {
                RangeScenario.Rfi(me.position)
            }
            priorRaises.size == 1 && me.position == Position.BB -> {
                val raiserSeat = priorRaises.first().seat
                val raiserPos = state.player(raiserSeat).position
                if (raiserPos == Position.BTN) {
                    RangeScenario.BbVsRfi(Position.BTN, sizeBb = state.currentBet)
                } else null
            }
            else -> null
        }
    }

    /** 沒有 solver 範圍時的翻前 fallback：用 HandClass 強度近似。 */
    private fun heuristicPreflopStrategy(
        state: TableState,
        position: Position,
        hc: HandClass
    ): MixedStrategy {
        val strength = preflopStrength(hc)
        val toCall = state.currentBet - state.player(0).committedThisStreet  // 估算
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

        // looseness：把部分 fold 轉成 call
        if (profile.loosenessBias != 0.0 && fold > 0) {
            val shift = fold * profile.loosenessBias.coerceIn(-1.0, 1.0)
            if (shift > 0) {
                val moved = min(fold, shift)
                fold -= moved; call += moved
            } else {
                // 緊：把部分 call 轉成 fold
                val moved = min(call, -shift)
                call -= moved; fold += moved
            }
        }

        // aggression：把部分 call 轉成 raise
        if (profile.aggressionBias != 0.0) {
            if (profile.aggressionBias > 0 && call > 0) {
                val moved = call * profile.aggressionBias
                call -= moved; raise += moved
            } else if (profile.aggressionBias < 0 && raise > 0) {
                val moved = raise * (-profile.aggressionBias) * 0.5
                raise -= moved; call += moved
            }
        }

        // bluff：弱牌時把 fold 轉成 raise（限定強度 < 0.5 的手）
        if (profile.bluffBias > 0 && preflopStrength(hc) < 0.5 && fold > 0) {
            val moved = fold * profile.bluffBias * 0.3
            fold -= moved; raise += moved
        }

        // stickiness：減少 fold（不分強度）
        if (profile.stickiness > 0 && fold > 0) {
            val moved = fold * profile.stickiness * 0.4
            fold -= moved; call += moved
        }

        // 重新正規化
        val total = fold + call + raise + check
        if (total <= 0.0) return MixedStrategy.PURE_FOLD
        return MixedStrategy(
            fold = (fold / total).coerceIn(0.0, 1.0),
            check = (check / total).coerceIn(0.0, 1.0),
            call = (call / total).coerceIn(0.0, 1.0),
            raise = (raise / total).coerceIn(0.0, 1.0)
        ).let { normalize(it) }
    }

    /** 修正浮點誤差，使 sum=1.0。 */
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
        isPreflop: Boolean
    ): Action {
        val me = state.player(seat)
        val toCall = state.toCall(seat)

        return when (kind) {
            Action.Kind.FOLD -> {
                // 若無人下注，FOLD 不合法 → 改 CHECK
                if (toCall == 0.0) Action.Check else Action.Fold
            }
            Action.Kind.CHECK -> {
                if (toCall > 0.0) {
                    // 不能 check 時，依強度退化為 call 或 fold
                    if (me.stack >= toCall) Action.Call else Action.Fold
                } else Action.Check
            }
            Action.Kind.CALL -> {
                if (toCall <= 0.0) Action.Check
                else if (me.stack <= toCall) Action.Call  // all-in call
                else Action.Call
            }
            Action.Kind.RAISE -> {
                val size = chooseRaiseSize(state, seat, isPreflop)
                if (size <= state.currentBet || size > me.committedThisStreet + me.stack) {
                    // 無法合法加注 → fallback
                    if (toCall > 0) Action.Call else Action.Check
                } else Action.Raise(size)
            }
        }
    }

    private fun chooseRaiseSize(state: TableState, seat: Int, isPreflop: Boolean): Double {
        val me = state.player(seat)
        val maxTo = me.committedThisStreet + me.stack  // all-in to-amount

        return if (isPreflop) {
            // 翻前標準開池 2.5bb，3-bet 3x raiser，4-bet 2.2x raiser
            val priorRaises = state.log.filterIsInstance<com.pokercoach.core.game.GameEvent.ActionTaken>()
                .count { it.street == Street.PREFLOP && it.action is Action.Raise }
            val target = when (priorRaises) {
                0 -> 2.5
                1 -> state.currentBet * 3.0
                else -> state.currentBet * 2.2
            }
            min(target, maxTo)
        } else {
            // 翻後：依強度與街選擇 sizing
            val strength = postflopStrength(state, seat)
            val potFraction = when {
                strength > 0.85 -> 1.0
                strength > 0.65 -> 0.66
                strength > 0.40 -> 0.50            // 半詐唬區
                else -> 0.33                       // 純詐唬或 give-up
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
    // Postflop heuristic
    // =====================================================================
    private fun decidePostflop(state: TableState, seat: Int): Decision {
        val me = state.player(seat)
        val hole = me.holeCards!!
        val strength = postflopStrength(state, seat)
        val toCall = state.toCall(seat)
        val potOdds = if (toCall <= 0) 0.0 else toCall / (state.pot + toCall)

        // 基礎啟發式：依強度決定行動傾向
        var base = when {
            strength > 0.90 -> MixedStrategy(raise = 0.75, call = 0.25)  // 巨怪 → 多 bet/raise
            strength > 0.70 -> MixedStrategy(raise = 0.55, call = 0.40, fold = 0.05)
            strength > 0.50 -> if (toCall == 0.0) MixedStrategy(raise = 0.35, check = 0.65)
                              else MixedStrategy(call = 0.70, raise = 0.10, fold = 0.20)
            strength > 0.30 -> if (toCall == 0.0) MixedStrategy(check = 0.80, raise = 0.20)
                               else if (potOdds < 0.25) MixedStrategy(call = 0.55, fold = 0.45)
                               else MixedStrategy(fold = 0.70, call = 0.30)
            else -> if (toCall == 0.0) MixedStrategy(check = 0.85, raise = 0.15)  // 偶爾 stab
                    else MixedStrategy.PURE_FOLD
        }

        // 心理剝削調整（postflop 也用同一套機制，但語意對應後手）
        base = applyPsychAdjustment(base, hole.handClass)

        val kind = sampleAction(base)
        val action = materializeAction(state, seat, kind, isPreflop = false)

        return Decision(
            action = action,
            rationale = buildString {
                append("${state.street} strength=${"%.2f".format(strength)} ")
                append("toCall=${"%.2f".format(toCall)}bb potOdds=${"%.2f".format(potOdds)} ")
                append("→ ${formatStrategy(base)} → $action")
            },
            adjustedStrategy = base
        )
    }

    /**
     * 翻後手牌強度（0..1）：簡化版，僅依 made hand category。
     * Phase 5 可替換為 Monte Carlo equity vs random range。
     */
    private fun postflopStrength(state: TableState, seat: Int): Double {
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
                // 區分頂對 / 中對 / 底對
                val topBoardRank = state.board.maxOf { it.rank.value }
                val pairRank = score.kickers.first()
                when {
                    pairRank >= topBoardRank -> 0.55          // 頂對 or 超對
                    pairRank >= 10 -> 0.42
                    else -> 0.30
                }
            }
            HandEvaluator.Category.HIGH_CARD -> {
                // 看是否有 overcards / draw potential
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
        if (s.raise > 0.001) parts += "R${(s.raise * 100).toInt()}"
        if (s.call  > 0.001) parts += "C${(s.call  * 100).toInt()}"
        if (s.check > 0.001) parts += "X${(s.check * 100).toInt()}"
        if (s.fold  > 0.001) parts += "F${(s.fold  * 100).toInt()}"
        return parts.joinToString("/")
    }
}
