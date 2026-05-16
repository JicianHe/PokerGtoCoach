package com.pokercoach.ui.table

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.game.Player
import com.pokercoach.core.game.PlayerKind
import com.pokercoach.core.game.PlayerStatus
import com.pokercoach.ui.common.CardView
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.SeatActiveGlow
import com.pokercoach.ui.theme.SeatAllInOrange
import com.pokercoach.ui.theme.SeatFoldedGray
import com.pokercoach.ui.theme.SeatHeroBlue
import com.pokercoach.ui.theme.SeatIdleSoft
import com.pokercoach.ui.theme.SeatWinnerGlow
import com.pokercoach.ui.theme.SeatWinnerGold
import com.pokercoach.ui.theme.Strings

/**
 * 玩家心情，影響表情符號（PsychProfile 互動 + 行動結果）。
 */
enum class SeatMood { NEUTRAL, THINKING, HAPPY, SAD, AGGRESSIVE, NERVOUS }

/**
 * 一個玩家座位的視覺呈現。
 *
 * - isHero: 底牌正面顯示
 * - isActor: 高亮邊框 + 思考動畫
 * - isButton: D 標記
 * - isWinner: 金色閃爍邊框 + 👑（攤牌或一人剩餘時）
 * - revealHoleCards: 攤牌時顯示對手底牌
 * - mood: 表情
 */
@Composable
fun PlayerSeat(
    player: Player,
    isHero: Boolean,
    isActor: Boolean,
    isButton: Boolean,
    isWinner: Boolean = false,
    revealHoleCards: Boolean = false,
    mood: SeatMood = SeatMood.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isWinner -> SeatWinnerGold
            player.status == PlayerStatus.FOLDED -> SeatFoldedGray
            player.status == PlayerStatus.ALL_IN -> SeatAllInOrange
            isActor -> SeatActiveGlow
            isHero -> SeatHeroBlue
            else -> SeatIdleSoft
        },
        animationSpec = tween(durationMillis = 220),
        label = "seatBorder"
    )

    // 贏家金光脈衝 / 行動者呼吸
    val pulse by rememberInfiniteTransition(label = "seatPulse").animateFloat(
        initialValue = if (isWinner || isActor) 0.6f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier.width(168.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 底牌（兩張）
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (player.status == PlayerStatus.FOLDED) {
                Spacer(Modifier.height(64.dp))
            } else {
                val showFace = isHero || revealHoleCards
                CardView(card = player.holeCards?.first, widthDp = 44, faceDown = !showFace)
                CardView(card = player.holeCards?.second, widthDp = 44, faceDown = !showFace)
            }
        }

        // 名字 + 位置 + D button + 表情 + 王冠
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (isWinner)
                        Brush.verticalGradient(listOf(SeatWinnerGlow, HudPanel))
                    else Brush.verticalGradient(listOf(HudPanel, HudPanel)),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = if (isWinner) 3.dp else 2.dp,
                    color = borderColor.copy(alpha = if (isActor || isWinner) pulse else 1f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isWinner) Text("👑", fontSize = 14.sp)
                    Text(
                        text = player.name,
                        color = if (isHero) SeatHeroBlue else HudTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val emoji = moodEmoji(mood, isActor)
                    if (emoji.isNotEmpty()) {
                        Text(
                            emoji,
                            fontSize = 13.sp,
                            modifier = if (isActor) Modifier.scale(0.9f + pulse * 0.2f) else Modifier
                        )
                    }
                    if (player.kind == PlayerKind.AI) {
                        Text("●", color = Color(0xFFAA88FF), fontSize = 10.sp)
                    }
                    if (isButton) ButtonMarker()
                }
                Text(
                    text = "${Strings.position(player.position)}  •  ${"%.1f".format(player.stack)} ${Strings.BB_UNIT}",
                    color = HudTextDim,
                    fontSize = 12.sp
                )
                when {
                    player.status == PlayerStatus.FOLDED ->
                        Text(Strings.STATUS_FOLDED, color = SeatFoldedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    player.status == PlayerStatus.ALL_IN ->
                        Text(Strings.STATUS_ALL_IN, color = SeatAllInOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    isActor -> Text(
                        "思考中…",
                        color = HudTextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.alpha(pulse)
                    )
                }
            }
        }
    }
}

private fun moodEmoji(mood: SeatMood, isActor: Boolean): String = when (mood) {
    SeatMood.THINKING -> "🤔"
    SeatMood.HAPPY -> "😊"
    SeatMood.SAD -> "😞"
    SeatMood.AGGRESSIVE -> "😈"
    SeatMood.NERVOUS -> "😰"
    SeatMood.NEUTRAL -> if (isActor) "💭" else ""
}

@Composable
private fun ButtonMarker() {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(Color.White, CircleShape)
            .border(1.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("D", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
