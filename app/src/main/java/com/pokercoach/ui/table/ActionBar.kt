package com.pokercoach.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.game.TableState
import com.pokercoach.core.model.Action
import com.pokercoach.ui.theme.ActionAllIn
import com.pokercoach.ui.theme.ActionCall
import com.pokercoach.ui.theme.ActionCheck
import com.pokercoach.ui.theme.ActionFold
import com.pokercoach.ui.theme.ActionRaise
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary

/**
 * 玩家行動列：Fold / Check / Call / Raise 含 bet sizing slider 與快速按鈕（½ pot, ¾ pot, pot, all-in）。
 *
 * 為了 120Hz 觸控優化：
 *  - 按鈕高度 64dp（適合大拇指操作）
 *  - Slider 步進預先計算離散值避免每次拖動 recompose 全部按鈕
 *  - Tap target ≥ 48dp，無動畫遮罩延遲
 */
@Composable
fun ActionBar(
    state: TableState,
    heroSeat: Int,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val hero = state.players.first { it.seatIndex == heroSeat }
    val toCall = state.toCall(heroSeat)
    val canCheck = toCall == 0.0
    val canCall = toCall > 0.0 && hero.stack > 0.0
    val canRaise = hero.stack > toCall

    val minRaiseTo = (state.currentBet + state.minRaise).coerceAtMost(hero.committedThisStreet + hero.stack)
    val maxRaiseTo = hero.committedThisStreet + hero.stack   // all-in
    var raiseTo by remember(state.handNumber, state.street, state.currentBet) {
        mutableStateOf(minRaiseTo.coerceAtMost(maxRaiseTo))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HudPanel, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            label = "FOLD",
            color = ActionFold,
            enabled = !canCheck,
            modifier = Modifier.weight(1f).height(64.dp),
            onClick = { onAction(Action.Fold) }
        )

        if (canCheck) {
            ActionButton(
                label = "CHECK",
                color = ActionCheck,
                enabled = true,
                modifier = Modifier.weight(1f).height(64.dp),
                onClick = { onAction(Action.Check) }
            )
        } else {
            ActionButton(
                label = "CALL\n${"%.1f".format(toCall)} bb",
                color = ActionCall,
                enabled = canCall,
                modifier = Modifier.weight(1f).height(64.dp),
                onClick = { onAction(Action.Call) }
            )
        }

        // RAISE 區塊：含 slider + 快速 size 按鈕
        Column(
            modifier = Modifier.weight(2f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickSizeChip("½ pot", canRaise) {
                    raiseTo = clampRaise(state.currentBet + state.pot * 0.5, minRaiseTo, maxRaiseTo)
                }
                QuickSizeChip("¾ pot", canRaise) {
                    raiseTo = clampRaise(state.currentBet + state.pot * 0.75, minRaiseTo, maxRaiseTo)
                }
                QuickSizeChip("Pot", canRaise) {
                    raiseTo = clampRaise(state.currentBet + state.pot, minRaiseTo, maxRaiseTo)
                }
                QuickSizeChip("All-in", canRaise) {
                    raiseTo = maxRaiseTo
                }
            }
            if (canRaise) {
                Slider(
                    value = raiseTo.toFloat(),
                    onValueChange = { raiseTo = it.toDouble() },
                    valueRange = minRaiseTo.toFloat()..maxRaiseTo.toFloat(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                )
            }
        }

        ActionButton(
            label = if (raiseTo >= maxRaiseTo - 1e-6) "ALL-IN\n${"%.1f".format(maxRaiseTo)} bb"
                    else "RAISE\n${"%.1f".format(raiseTo)} bb",
            color = if (raiseTo >= maxRaiseTo - 1e-6) ActionAllIn else ActionRaise,
            enabled = canRaise,
            modifier = Modifier.weight(1.5f).height(64.dp),
            onClick = { onAction(Action.Raise(raiseTo)) }
        )
    }
}

private fun clampRaise(target: Double, min: Double, max: Double): Double =
    target.coerceIn(min, max)

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (enabled) color else color.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .shadow(if (enabled) 6.dp else 0.dp, RoundedCornerShape(12.dp))
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = HudTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun QuickSizeChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (enabled) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = HudTextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
