package com.pokercoach.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pokercoach.core.stats.DecisionGrader
import com.pokercoach.ui.theme.ActionCallGreen
import com.pokercoach.ui.theme.ActionCheckBlue
import com.pokercoach.ui.theme.ActionFoldGray
import com.pokercoach.ui.theme.ActionRaisePink
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudBad
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.HudWarn
import com.pokercoach.ui.theme.Strings
import com.pokercoach.ui.theme.VerdictLevel
import com.pokercoach.viewmodel.GameViewModel

/**
 * 右側「策略導師」HUD 主面板（全中文化）。
 *
 * 三大區塊：
 *   1) 行動前分析（PreDecisionCard）— 顯示 GTO 策略 + 各行動 EV + 範圍佔比
 *   2) 行動後點評（ReviewCard）       — 比對玩家選擇與 GTO 基準
 *   3) 手牌結算（HandEndCard）        — 顯示贏家、勝因、彩金分配
 *
 * 附加：對手 AI 推理透明化卡片。
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
                    WaitingCard(state)
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
            Text(
                Strings.HUD_TITLE,
                color = HudAccent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${Strings.street(state.street)}  •  ${Strings.POT} ${"%.1f".format(state.pot)} ${Strings.BB_UNIT}",
                color = HudTextPrimary, fontSize = 14.sp
            )
        }
    }
}

// ============================================================
// 行動前分析
// ============================================================
@Composable
private fun PreDecisionCard(
    state: TableState,
    heroSeat: Int,
    rec: EvCalculator.Recommendation?
) {
    HudCard(title = Strings.HUD_BEFORE, accent = HudAccent) {
        val hero = state.players.first { it.seatIndex == heroSeat }
        val hole = hero.holeCards
        val toCall = state.toCall(heroSeat)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hole?.toString() ?: "??", color = HudTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("@ ${Strings.position(hero.position)}", color = HudTextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${Strings.HUD_TO_CALL} ${"%.1f".format(toCall)} ${Strings.BB_UNIT}  •  ${Strings.HUD_POT_ODDS} ${potOddsString(state, heroSeat)}",
            color = HudTextDim, fontSize = 13.sp
        )

        Spacer(Modifier.height(10.dp))

        if (state.street == Street.PREFLOP && rec != null) {
            Text(
                Strings.HUD_GTO_STRATEGY,
                color = HudAccent, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))
            StrategyBar(rec.strategy)
            Spacer(Modifier.height(8.dp))
            EvBreakdown(rec.evByAction)
            Spacer(Modifier.height(8.dp))
            Text(
                "${Strings.HUD_RANGE_SHARE}：${"%.1f".format(rec.rangeFrequencyPct)}%",
                color = HudTextDim, fontSize = 12.sp
            )
            if (rec.isMixedDecision) {
                Spacer(Modifier.height(6.dp))
                MixedDecisionBadge()
            }
        } else if (state.street == Street.PREFLOP) {
            Text(
                "此翻前情境無 solver 範圍快取，\n請依撲克基本面自行判斷。",
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
        Strings.HUD_POSTFLOP_CHECKLIST,
        color = HudAccent, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
    )
    Spacer(Modifier.height(6.dp))
    BulletLine("板面：${describeBoard(state.board)}")
    BulletLine("位置：${Strings.positionFull(hero.position)}")
    if (toCall > 0) {
        BulletLine("${Strings.HUD_POT_ODDS}：${"%.0f".format(potOdds * 100)}% → 所需勝率")
    } else {
        BulletLine("無下注待跟：可考慮 c-bet 33–66% 底池")
    }
    val spr = hero.stack / state.pot.coerceAtLeast(0.5)
    BulletLine("SPR（堆疊／底池）= ${"%.1f".format(spr)}")
}

// ============================================================
// 行動後點評
// ============================================================
@Composable
private fun ReviewCard(
    pause: GameViewModel.PauseState.HeroReview,
    onContinue: () -> Unit
) {
    HudCard(title = Strings.HUD_REVIEW, accent = HudWarn) {
        val rec = pause.recommendation
        val chosen = pause.action.kind
        val verdict = pause.verdict

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${Strings.HUD_YOUR_CHOICE}：", color = HudTextDim, fontSize = 13.sp)
            Text(Strings.actionLabel(pause.action), color = HudTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        if (rec != null) {
            Text("${Strings.HUD_BASELINE}：", color = HudTextDim, fontSize = 13.sp)
            StrategyBar(rec.strategy)
            Spacer(Modifier.height(8.dp))
            EvBreakdown(rec.evByAction, highlight = chosen)
            Spacer(Modifier.height(10.dp))
        }

        VerdictBanner(verdict)
        if (rec != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                Strings.verdictExplain(verdict, chosen, rec),
                color = HudTextPrimary, fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        ContinueButton(Strings.HUD_CONTINUE, onContinue)
    }
}

@Composable
private fun VerdictBanner(v: VerdictLevel) {
    val (label, color) = when (v) {
        VerdictLevel.Optimal     -> Strings.V_OPTIMAL    to HudGood
        VerdictLevel.Acceptable  -> Strings.V_ACCEPTABLE to HudAccent
        VerdictLevel.Suboptimal  -> Strings.V_SUBOPTIMAL to HudWarn
        VerdictLevel.Blunder     -> Strings.V_BLUNDER    to HudBad
        VerdictLevel.Unknown     -> Strings.V_UNKNOWN    to HudTextDim
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
// 觀察 AI 動作
// ============================================================
@Composable
private fun WaitingCard(state: TableState) {
    HudCard(title = Strings.HUD_WATCHING, accent = HudAccent) {
        val actor = state.actorSeat
        if (actor != null) {
            val p = state.player(actor)
            Text(
                "${p.name}（${Strings.position(p.position)}）思考中...",
                color = HudTextPrimary, fontSize = 14.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "觀察對手的行動模式；對手畫像可能偏離 GTO。",
                color = HudTextDim, fontSize = 12.sp
            )
        } else {
            Text("發下一條街中...", color = HudTextDim, fontSize = 13.sp)
        }
    }
}

// ============================================================
// 手牌結算
// ============================================================
@Composable
private fun HandEndCard(ev: GameEvent.HandEnded, onNextHand: () -> Unit) {
    HudCard(title = Strings.HUD_HAND_DONE, accent = HudGood) {
        Text(
            "贏家：${Strings.winners(ev.winners)}",
            color = HudTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text("勝因：${ev.reason}", color = HudTextDim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        for ((seat, amt) in ev.amounts) {
            Text("座位 $seat +${"%.1f".format(amt)} ${Strings.BB_UNIT}", color = HudGood, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        ContinueButton(Strings.HUD_NEXT_HAND, onNextHand)
    }
}

// ============================================================
// AI 推理透明化
// ============================================================
@Composable
private fun AiInsightCard(decision: PokerAi.Decision) {
    HudCard(title = Strings.HUD_OPPONENT, accent = HudTextDim) {
        Text(decision.rationale, color = HudTextDim, fontSize = 12.sp)
        if (decision.baselineStrategy != null && decision.adjustedStrategy != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${Strings.HUD_BASELINE} → ${Strings.HUD_ADJUSTED}：",
                color = HudTextDim, fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            StrategyBar(decision.baselineStrategy, compact = true)
            Spacer(Modifier.height(2.dp))
            StrategyBar(decision.adjustedStrategy, compact = true)
        }
    }
}

// ============================================================
// 共用元件
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
        if (s.raise > 0) Segment(s.raise, ActionRaisePink, "加", compact)
        if (s.call  > 0) Segment(s.call,  ActionCallGreen, "跟", compact)
        if (s.check > 0) Segment(s.check, ActionCheckBlue, "過", compact)
        if (s.fold  > 0) Segment(s.fold,  ActionFoldGray,  "蓋", compact)
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
            Text(
                "$label ${(freq * 100).toInt()}%",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EvBreakdown(evMap: Map<Action.Kind, Double>, highlight: Action.Kind? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(Strings.HUD_EV_BREAKDOWN, color = HudTextDim, fontSize = 11.sp)
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
                    Strings.actionLabelByKind(kind).padEnd(4),
                    color = HudTextDim, fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(
                    "  EV ${if (ev >= 0) "+" else ""}${"%.2f".format(ev)} ${Strings.BB_UNIT}",
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
        Text(
            Strings.HUD_MIXED_BADGE,
            color = HudWarn, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
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
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
    if (board.isEmpty()) return "（未發牌）"
    val suits = board.groupingBy { it.suit }.eachCount()
    val maxSuit = suits.values.max()
    val flushDraw = when (maxSuit) {
        in 4..5 -> "單花色傾向"
        3 -> "兩色（同花 draw 多）"
        else -> "近彩虹"
    }
    val ranks = board.map { it.rank.value }.sortedDescending()
    val connected = ranks.zipWithNext().count { (a, b) -> a - b <= 2 }
    val texture = when {
        connected >= 2 -> "潮濕／連張"
        connected == 1 -> "半連張"
        else -> "乾燥"
    }
    return "$flushDraw、$texture"
}
