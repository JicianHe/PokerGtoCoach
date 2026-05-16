package com.pokercoach.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.ai.PokerAi
import com.pokercoach.core.ev.EvCalculator
import com.pokercoach.core.game.GameEvent
import com.pokercoach.core.game.Street
import com.pokercoach.core.game.TableState
import com.pokercoach.core.model.Action
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudBad
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.HudWarn
import com.pokercoach.viewmodel.GameViewModel

/**
 * 右側「策略導師」HUD 主面板。三大區塊：
 *
 *   1) Pre-decision panel：Hero 行動前，顯示 GTO 推薦 + 各行動 EV
 *      （翻前用 PreflopRangeManager；翻後 Phase 5 補上）
 *   2) Post-decision review：Hero 行動後暫停，比對選擇與 GTO，給文字評語
 *   3) Hand-end summary：手牌結束顯示贏家、勝因，並提供「下一手」按鈕
 *
 * 額外固定區塊：對手 AI 的最後決策推理（透明化 AI，輔助學習）。
 */
@Composable
fun StrategyHudPanel(
    state: TableState,
    heroSeat: Int,
    recommendation: EvCalculator.Recommendation?,
    pauseState: GameViewModel.PauseState,
    lastAiDecision: PokerAi.Decision?,
    onContinue: () -> Unit,
    onNextHand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderBar(state)

        when (pauseState) {
            is GameViewModel.PauseState.HandComplete -> {
                HandEndCard(pauseState.event, onNextHand)
            }
            is GameViewModel.PauseState.HeroReview -> {
                ReviewCard(pauseState, onContinue)
            }
            is GameViewModel.PauseState.None -> {
                if (state.actorSeat == heroSeat) {
                    PreDecisionCard(state, heroSeat, recommendation)
                } else {
                    WaitingCard(state, heroSeat)
                }
            }
        }

        if (lastAiDecision != null && pauseState !is GameViewModel.PauseState.HandComplete) {
            AiInsightCard(lastAiDecision)
        }
    }
}

// ============================================================
// Header
// ============================================================
@Composable
private fun HeaderBar(state: TableState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudPanel, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Text("STRATEGY COACH", color = HudAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "${state.street.name}  •  Pot ${"%.1f".format(state.pot)} bb",
                color = HudTextPrimary, fontSize = 14.sp
            )
        }
    }
}

// ============================================================
// Pre-decision (Hero is about to act)
// ============================================================
@Composable
private fun PreDecisionCard(
    state: TableState,
    heroSeat: Int,
    rec: EvCalculator.Recommendation?
) {
    HudCard(title = "BEFORE YOU ACT", accent = HudAccent) {
        val hero = state.players.first { it.seatIndex == heroSeat }
        val hole = hero.holeCards
        val toCall = state.toCall(heroSeat)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hole?.toString() ?: "??", color = HudTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("@ ${hero.position.displayName}", color = HudTextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "To call ${"%.1f".format(toCall)} bb  •  Pot odds ${potOddsString(state, heroSeat)}",
            color = HudTextDim, fontSize = 13.sp
        )

        Spacer(Modifier.height(10.dp))

        if (state.street == Street.PREFLOP && rec != null) {
            Text("GTO STRATEGY", color = HudAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            StrategyBar(rec.strategy)
            Spacer(Modifier.height(8.dp))
            EvBreakdown(rec.evByAction)
            Spacer(Modifier.height(8.dp))
            Text(
                "Hand share of overall range: ${"%.1f".format(rec.rangeFrequencyPct)}%",
                color = HudTextDim, fontSize = 12.sp
            )
            if (rec.isMixedDecision) {
                Spacer(Modifier.height(6.dp))
                MixedDecisionBadge()
            }
        } else if (state.street == Street.PREFLOP) {
            Text(
                "No solver range cached for this preflop spot.\nUsing heuristic; trust your fundamentals.",
                color = HudWarn, fontSize = 12.sp
            )
        } else {
            PostflopHeuristicHint(state, heroSeat)
        }
    }
}

@Composable
private fun PostflopHeuristicHint(state: TableState, heroSeat: Int) {
    val hero = state.players.first { it.seatIndex == heroSeat }
    val toCall = state.toCall(heroSeat)
    val potOdds = if (toCall <= 0) 0.0 else toCall / (state.pot + toCall)

    Text(
        "POSTFLOP CHECKLIST",
        color = HudAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
    )
    Spacer(Modifier.height(6.dp))
    BulletLine("Board texture: " + describeBoard(state.board))
    BulletLine("Position: ${hero.position.displayName} (in/out of position vs raiser)")
    if (toCall > 0) {
        BulletLine("Pot odds: ${"%.0f".format(potOdds * 100)}% → equity needed to call")
    } else {
        BulletLine("No bet to face: consider c-bet sizing 33–66% pot")
    }
    BulletLine("SPR (stack:pot) = ${"%.1f".format(hero.stack / state.pot.coerceAtLeast(0.5))}")
}

// ============================================================
// Hero post-action review
// ============================================================
@Composable
private fun ReviewCard(
    pause: GameViewModel.PauseState.HeroReview,
    onContinue: () -> Unit
) {
    HudCard(title = "GTO REVIEW", accent = HudWarn) {
        val rec = pause.recommendation
        val chosen = pause.action.kind
        val verdict = if (rec != null) judge(chosen, rec) else Verdict.Unknown

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You chose:", color = HudTextDim, fontSize = 13.sp)
            Text(pause.action.toString(), color = HudTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        if (rec != null) {
            Text("GTO baseline:", color = HudTextDim, fontSize = 13.sp)
            StrategyBar(rec.strategy)
            Spacer(Modifier.height(8.dp))
            EvBreakdown(rec.evByAction, highlight = chosen)
            Spacer(Modifier.height(10.dp))
        }

        VerdictBanner(verdict)
        if (rec != null) {
            Spacer(Modifier.height(6.dp))
            Text(verdictExplain(verdict, chosen, rec), color = HudTextPrimary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(14.dp))
        ContinueButton("CONTINUE  ›", onContinue)
    }
}

private enum class Verdict { Optimal, Acceptable, Suboptimal, Blunder, Unknown }

private fun judge(chosen: Action.Kind, rec: EvCalculator.Recommendation): Verdict {
    val freq = rec.strategy.frequencyOf(chosen)
    return when {
        freq >= 0.50 -> Verdict.Optimal
        freq >= 0.20 -> Verdict.Acceptable
        freq >= 0.05 -> Verdict.Suboptimal
        else         -> Verdict.Blunder
    }
}

private fun verdictExplain(v: Verdict, chosen: Action.Kind, rec: EvCalculator.Recommendation): String {
    val dom = rec.recommendedAction
    val pct = (rec.strategy.frequencyOf(chosen) * 100).toInt()
    return when (v) {
        Verdict.Optimal -> "Solid. GTO plays $chosen here ${pct}% of the time with ${rec.hand}."
        Verdict.Acceptable -> "Defensible mix. GTO chooses $chosen about ${pct}% of the time; the dominant line is $dom."
        Verdict.Suboptimal -> "Off-tree. GTO rarely picks $chosen here (${pct}%). Default: $dom."
        Verdict.Blunder -> "Significant leak. GTO almost never plays $chosen with ${rec.hand} in this spot. Lean toward $dom."
        Verdict.Unknown -> "No solver baseline; review your reasoning manually."
    }
}

@Composable
private fun VerdictBanner(v: Verdict) {
    val (label, color) = when (v) {
        Verdict.Optimal -> "✓ GTO OPTIMAL" to HudGood
        Verdict.Acceptable -> "≈ ACCEPTABLE MIX" to HudAccent
        Verdict.Suboptimal -> "△ SUBOPTIMAL" to HudWarn
        Verdict.Blunder -> "✗ MAJOR LEAK" to HudBad
        Verdict.Unknown -> "— NO BASELINE" to HudTextDim
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================
// Waiting on AI
// ============================================================
@Composable
private fun WaitingCard(state: TableState, heroSeat: Int) {
    HudCard(title = "WATCHING", accent = HudAccent) {
        val actor = state.actorSeat
        if (actor != null) {
            val p = state.player(actor)
            Text("${p.name} (${p.position.displayName}) is deciding...",
                color = HudTextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Watch their action and pattern; opponent profile may differ from GTO.",
                color = HudTextDim, fontSize = 12.sp
            )
        } else {
            Text("Dealing next street...", color = HudTextDim, fontSize = 13.sp)
        }
    }
}

// ============================================================
// Hand end summary
// ============================================================
@Composable
private fun HandEndCard(ev: GameEvent.HandEnded, onNextHand: () -> Unit) {
    HudCard(title = "HAND COMPLETE", accent = HudGood) {
        Text("Winners: ${ev.winners.joinToString { "seat $it" }}",
            color = HudTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Reason: ${ev.reason}", color = HudTextDim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        for ((seat, amt) in ev.amounts) {
            Text("seat $seat +${"%.1f".format(amt)} bb", color = HudGood, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        ContinueButton("DEAL NEXT HAND  ›", onNextHand)
    }
}

// ============================================================
// AI insight
// ============================================================
@Composable
private fun AiInsightCard(decision: PokerAi.Decision) {
    HudCard(title = "OPPONENT REASONING", accent = HudTextDim) {
        Text(decision.rationale, color = HudTextDim, fontSize = 12.sp)
        if (decision.baselineStrategy != null && decision.adjustedStrategy != null) {
            Spacer(Modifier.height(6.dp))
            Text("Baseline → Adjusted (exploit):", color = HudTextDim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            StrategyBar(decision.baselineStrategy, compact = true)
            Spacer(Modifier.height(2.dp))
            StrategyBar(decision.adjustedStrategy, compact = true)
        }
    }
}

// ============================================================
// Reusable pieces
// ============================================================
@Composable
private fun HudCard(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .background(HudPanel, RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun StrategyBar(s: MixedStrategy, compact: Boolean = false) {
    val height = if (compact) 12.dp else 22.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
    ) {
        if (s.raise > 0) Segment(s.raise, com.pokercoach.ui.theme.ActionRaise, "R", compact)
        if (s.call  > 0) Segment(s.call,  com.pokercoach.ui.theme.ActionCall,  "C", compact)
        if (s.check > 0) Segment(s.check, com.pokercoach.ui.theme.ActionCheck, "X", compact)
        if (s.fold  > 0) Segment(s.fold,  com.pokercoach.ui.theme.ActionFold,  "F", compact)
    }
}

@Composable
private fun RowScope.Segment(freq: Double, color: Color, label: String, compact: Boolean) {
    Box(
        modifier = Modifier
            .weight(freq.toFloat())
            .fillMaxHeight()
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (!compact && freq > 0.06) {
            Text("$label ${(freq * 100).toInt()}%",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EvBreakdown(evMap: Map<Action.Kind, Double>, highlight: Action.Kind? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (kind in listOf(Action.Kind.RAISE, Action.Kind.CALL, Action.Kind.CHECK, Action.Kind.FOLD)) {
            val ev = evMap[kind] ?: continue
            if (ev == 0.0 && kind == Action.Kind.CHECK) continue   // 跳過無意義的 CHECK
            val isHi = (kind == highlight)
            val color = when {
                isHi -> HudWarn
                ev > 0.0 -> HudGood
                ev < -0.05 -> HudBad
                else -> HudTextDim
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${kind.name.padEnd(6)}",
                    color = HudTextDim, fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    "EV ${if (ev >= 0) "+" else ""}${"%.2f".format(ev)} bb",
                    color = color, fontSize = 13.sp,
                    fontWeight = if (isHi) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MixedDecisionBadge() {
    Box(
        modifier = Modifier
            .background(HudWarn.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .border(1.dp, HudWarn, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("⚖ MIXED — any action in the strategy is GTO-valid",
            color = HudWarn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ContinueButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .background(HudAccent, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF0B1216), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("•", color = HudAccent, fontSize = 12.sp)
        Text(text, color = HudTextPrimary, fontSize = 12.sp)
    }
}

// ============================================================
// Utility
// ============================================================
private fun potOddsString(state: TableState, heroSeat: Int): String {
    val toCall = state.toCall(heroSeat)
    if (toCall <= 0) return "—"
    val pct = toCall / (state.pot + toCall) * 100.0
    return "${"%.0f".format(pct)}%"
}

private fun describeBoard(board: List<com.pokercoach.core.model.Card>): String {
    if (board.isEmpty()) return "no board"
    val suits = board.groupingBy { it.suit }.eachCount()
    val maxSuit = suits.values.max()
    val flushDraw = when (maxSuit) {
        5, 4 -> "monotone-leaning"
        3 -> "two-tone (flush-draw heavy)"
        else -> "rainbow-ish"
    }
    val ranks = board.map { it.rank.value }.sortedDescending()
    val connected = ranks.zipWithNext().count { (a, b) -> a - b <= 2 }
    val texture = when {
        connected >= 2 -> "wet/connected"
        connected == 1 -> "semi-connected"
        else -> "dry"
    }
    return "$flushDraw, $texture"
}

