package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.game.GameEvent
import com.pokercoach.ui.hud.StrategyHudPanel
import com.pokercoach.ui.table.ActionBar
import com.pokercoach.ui.table.PokerTableLayout
import com.pokercoach.ui.theme.TableTopDeep
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings
import com.pokercoach.viewmodel.GameViewModel

/**
 * Tab S11 landscape 主畫面三欄佈局：
 *
 *   [ Left 18% ]  ●  Hand log / 動作歷史
 *   [ Mid 52%  ]  ●  撲克桌 + ActionBar
 *   [ Right 30% ] ●  Strategy Coach HUD（Phase 4 細節）
 *
 * 比例針對 2560x1600 平板 landscape 計算，每欄都用 `weight()`，
 * 在其他平板上也能優雅縮放。
 */
@Composable
fun GameScreen(vm: GameViewModel, onBack: () -> Unit = {}) {
    val state by vm.table.collectAsState()
    val pause by vm.pause.collectAsState()
    val recommendation by vm.heroRecommendation.collectAsState()
    val aiDecision by vm.lastAiDecision.collectAsState()
    val settings by vm.settings.collectAsState()
    val hudPrefs = remember(settings) { com.pokercoach.ui.hud.HudPreferences.from(settings) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TableTopDeep)
    ) {
        // ===== Left column: Hand log =====
        Column(
            modifier = Modifier
                .weight(0.18f)
                .fillMaxHeight()
                .background(HudBg)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹ ${Strings.BACK}",
                    color = HudTextDim, fontSize = 13.sp,
                    modifier = Modifier
                        .background(HudPanel, RoundedCornerShape(10.dp))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onBack() }) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "第 ${state.handNumber} 手",
                color = HudTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                Strings.street(state.street),
                color = HudTextDim, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            HandLog(events = state.log, players = state.players, heroSeat = vm.heroSeat)
        }

        // ===== Mid column: Table + ActionBar =====
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val winningSeats: Set<Int> = remember(state.log) {
                (state.log.lastOrNull() as? GameEvent.HandEnded)?.winners?.toSet() ?: emptySet()
            }
            PokerTableLayout(
                state = state,
                heroSeat = vm.heroSeat,
                winningSeats = winningSeats,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            BottomActionArea(
                vm = vm,
                state = state,
                pause = pause
            )
        }

        // ===== Right column: HUD =====
        Box(
            modifier = Modifier
                .weight(0.30f)
                .fillMaxHeight()
                .background(HudBg)
        ) {
            StrategyHudPanel(
                state = state,
                heroSeat = vm.heroSeat,
                recommendation = recommendation,
                pauseState = pause,
                lastAiDecision = aiDecision,
                onContinue = { vm.continueAfterReview() },
                onNextHand = { vm.startNextHand() },
                prefs = hudPrefs
            )
        }
    }
}

@Composable
private fun BottomActionArea(
    vm: GameViewModel,
    state: com.pokercoach.core.game.TableState,
    pause: GameViewModel.PauseState
) {
    when {
        pause is GameViewModel.PauseState.HandComplete -> {
            // 結算階段，行動列禁用（HUD 顯示繼續按鈕）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(HudPanel, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("本手結束 — 右側查看點評 →",
                    color = HudTextDim, fontSize = 16.sp)
            }
        }
        state.actorSeat == vm.heroSeat && pause is GameViewModel.PauseState.None -> {
            ActionBar(
                state = state,
                heroSeat = vm.heroSeat,
                onAction = { vm.onHeroAction(it) }
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(HudPanel, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val label = when {
                    pause is GameViewModel.PauseState.HeroReview -> "點評你的決策中 — 點繼續 →"
                    state.actorSeat == null -> "發牌中..."
                    else -> "${state.players.first { it.seatIndex == state.actorSeat }.name} 思考中..."
                }
                Text(label, color = HudTextDim, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun HandLog(
    events: List<GameEvent>,
    players: List<com.pokercoach.core.game.Player>,
    heroSeat: Int
) {
    fun nameOf(seat: Int): String =
        if (seat == heroSeat) Strings.NAME_YOU
        else players.firstOrNull { it.seatIndex == seat }?.name ?: "座位 $seat"

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(events.reversed()) { ev ->
            val text = when (ev) {
                is GameEvent.HandStarted -> "▶ 第 ${ev.handNumber} 手 (BTN ${nameOf(ev.buttonSeat)})"
                is GameEvent.BlindsPosted -> "  SB=${ev.sb}bb ${nameOf(ev.sbSeat)} • BB=${ev.bb}bb ${nameOf(ev.bbSeat)}"
                is GameEvent.HoleCardsDealt -> "  發底牌"
                is GameEvent.StreetDealt -> "── ${Strings.street(ev.street)} ${ev.newBoardCards.joinToString(" ")}"
                is GameEvent.ActionTaken -> "  ${nameOf(ev.seat)}: ${Strings.actionLabel(ev.action)}  (底池 ${"%.1f".format(ev.potAfter)})"
                is GameEvent.HandEnded -> "★ 贏家: ${Strings.winnersByName(ev.winners, players, heroSeat)} (${ev.reason})"
            }
            val color = when (ev) {
                is GameEvent.HandStarted, is GameEvent.HandEnded -> HudTextPrimary
                is GameEvent.StreetDealt -> Color(0xFF50C9C3)
                else -> HudTextDim
            }
            Text(text, color = color, fontSize = 12.sp)
        }
    }
}
