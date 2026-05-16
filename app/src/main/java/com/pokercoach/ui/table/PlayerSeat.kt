package com.pokercoach.ui.table

import androidx.compose.animation.animateColorAsState
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
import com.pokercoach.ui.theme.SeatActive
import com.pokercoach.ui.theme.SeatAllIn
import com.pokercoach.ui.theme.SeatFolded
import com.pokercoach.ui.theme.SeatHero
import com.pokercoach.ui.theme.SeatIdle

/**
 * 一個玩家座位的視覺呈現：頭像區（顯示名稱 + 位置）、底牌、籌碼。
 *
 * - isHero: true 時底牌正面顯示
 * - isActor: 高亮邊框（行動中）
 * - isButton: 顯示 D button 標記
 */
@Composable
fun PlayerSeat(
    player: Player,
    isHero: Boolean,
    isActor: Boolean,
    isButton: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            player.status == PlayerStatus.FOLDED -> SeatFolded
            player.status == PlayerStatus.ALL_IN -> SeatAllIn
            isActor -> SeatActive
            isHero -> SeatHero
            else -> SeatIdle
        },
        animationSpec = tween(durationMillis = 220),
        label = "seatBorder"
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
                CardView(
                    card = player.holeCards?.first,
                    widthDp = 44,
                    faceDown = !isHero
                )
                CardView(
                    card = player.holeCards?.second,
                    widthDp = 44,
                    faceDown = !isHero
                )
            }
        }

        // 名字 + 位置 + D button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HudPanel, RoundedCornerShape(12.dp))
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = player.name,
                        color = if (isHero) SeatHero else HudTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (player.kind == PlayerKind.AI) {
                        Text("●", color = Color(0xFFAA88FF), fontSize = 10.sp)
                    }
                    if (isButton) ButtonMarker()
                }
                Text(
                    text = "${player.position.displayName}  •  ${"%.1f".format(player.stack)} bb",
                    color = HudTextDim,
                    fontSize = 12.sp
                )
                if (player.status == PlayerStatus.FOLDED) {
                    Text("FOLDED", color = SeatFolded, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else if (player.status == PlayerStatus.ALL_IN) {
                    Text("ALL-IN", color = SeatAllIn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
