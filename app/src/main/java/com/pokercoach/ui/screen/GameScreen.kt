package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.game.GameEvent
import com.pokercoach.ui.hud.StrategyHudPanel
import com.pokercoach.ui.table.ActionBar
import com.pokercoach.ui.table.PokerTableLayout
import com.pokercoach.ui.theme.FeltDark
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
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
fun GameScreen(vm: GameViewModel) {
    val state by vm.table.collectAsState()
    val pause by vm.pause.collectAsState()
    val recommendation by vm.heroRecommendation.collectAsState()
    val aiDecision by vm.lastAiDecision.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(FeltDark)
    ) {
        // ===== Left column: Hand log =====
        Column(
            modifier = Modifier
                .weight(0.18f)
                .fillMaxHeight()
                .background(HudBg)
                .padding(12.dp)
        ) {
            Text(
                "Hand #${state.handNumber}",
                color = HudTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                state.street.name,
                color = HudTextDim, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            HandLog(events = state.log)
        }

        // ===== Mid column: Table + ActionBar =====
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            PokerTableLayout(
                state = state,
                heroSeat = vm.heroSeat,
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
                onNextHand = { vm.startNextHand() }
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
                Text("Hand complete — see review on the right →",
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
            // AI 行動中或玩家回顧中 → 顯示狀態提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(HudPanel, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val label = when {
                    pause is GameViewModel.PauseState.HeroReview -> "Reviewing your decision — tap Continue →"
                    state.actorSeat == null -> "Dealing..."
                    else -> "${state.players.first { it.seatIndex == state.actorSeat }.name} is thinking..."
                }
                Text(label, color = HudTextDim, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun HandLog(events: List<GameEvent>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(events.reversed()) { ev ->
            val text = when (ev) {
                is GameEvent.HandStarted -> "▶ Hand #${ev.handNumber} (BTN seat ${ev.buttonSeat})"
                is GameEvent.BlindsPosted -> "  SB=${ev.sb}bb seat${ev.sbSeat} • BB=${ev.bb}bb seat${ev.bbSeat}"
                is GameEvent.HoleCardsDealt -> "  Hole cards dealt"
                is GameEvent.StreetDealt -> "── ${ev.street} ${ev.newBoardCards.joinToString(" ")}"
                is GameEvent.ActionTaken -> "  seat${ev.seat}: ${ev.action}  (pot=${"%.1f".format(ev.potAfter)})"
                is GameEvent.HandEnded -> "★ Winners: ${ev.winners} (${ev.reason})"
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
