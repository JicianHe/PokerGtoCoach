package com.pokercoach.ui.hud

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.ai.PokerAi
import com.pokercoach.core.ev.EvCalculator
import com.pokercoach.core.game.TableState
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.viewmodel.GameViewModel

/**
 * Phase 3 stub — full implementation arrives in Phase 4.
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
    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Strategy Coach (Phase 4)", color = HudTextDim, fontSize = 14.sp)
    }
}
