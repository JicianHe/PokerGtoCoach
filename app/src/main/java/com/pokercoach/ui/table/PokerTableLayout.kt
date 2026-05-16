package com.pokercoach.ui.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.game.TableState
import com.pokercoach.ui.common.CardView
import com.pokercoach.ui.common.ChipStack
import com.pokercoach.ui.theme.FeltDark
import com.pokercoach.ui.theme.FeltLight
import com.pokercoach.ui.theme.FeltMid
import com.pokercoach.ui.theme.FeltRail
import com.pokercoach.ui.theme.FeltRailHi
import com.pokercoach.ui.theme.HudTextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 6-Max 橢圓撲克桌。Hero 永遠位於桌面下方中央（座位索引固定為 0）。
 *
 * 其餘 5 個座位沿橢圓均勻分布（角度從 hero 起按順時針 60° 步進）。
 *
 * 桌面正中央顯示：底池 + 公共牌。
 *
 * 為了 120Hz 流暢度，所有重繪元件分離（Composable + remember key）。
 */
@Composable
fun PokerTableLayout(
    state: TableState,
    heroSeat: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .shadow(20.dp, RoundedCornerShape(percent = 50))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(FeltRailHi, FeltRail),
                    radiusX = 1f
                ),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(22.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(FeltLight, FeltMid, FeltDark)
                ),
                shape = RoundedCornerShape(percent = 50)
            )
            .border(2.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(percent = 50))
    ) {
        // 中央：公共牌 + 底池
        TableCenter(
            state = state,
            modifier = Modifier.align(Alignment.Center)
        )

        // 玩家座位（橢圓分布）
        SeatRing(
            state = state,
            heroSeat = heroSeat
        )
    }
}

@Composable
private fun TableCenter(state: TableState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pot
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Text(
                text = "POT  ${"%.1f".format(state.pot)} bb",
                color = HudTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // 公共牌
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until 5) {
                val c = state.board.getOrNull(i)
                CardView(card = c, widthDp = 64, faceDown = false)
            }
        }
        // Pot chip stack 視覺
        if (state.pot > 0) ChipStack(amountBb = state.pot)
    }
}

/**
 * 用自訂 Layout 把 6 個座位定位到橢圓邊上。
 * 座位順序：以 heroSeat 為「下方 90°」起點，順時針 60° 步進。
 */
@Composable
private fun SeatRing(state: TableState, heroSeat: Int) {
    Layout(
        content = {
            for (seat in 0..5) {
                val p = state.players.first { it.seatIndex == seat }
                Box {
                    PlayerSeat(
                        player = p,
                        isHero = (seat == heroSeat),
                        isActor = (state.actorSeat == seat),
                        isButton = (state.buttonSeat == seat)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val cx = w / 2
        val cy = h / 2
        // 橢圓半徑：留 padding 給座位卡片本身
        val rx = (w / 2 - 130).coerceAtLeast(100)
        val ry = (h / 2 - 90).coerceAtLeast(80)

        layout(w, h) {
            placeables.forEachIndexed { index, placeable ->
                // 順序：相對 hero 的偏移（hero=index 0 在下方）
                val seatOffset = (index - heroSeat + 6) % 6
                // 角度：下方 = π/2，順時針增加 60°（= π/3）
                val angle = PI / 2 + seatOffset * (PI / 3)
                val x = cx + (rx * cos(angle)).toInt() - placeable.width / 2
                val y = cy + (ry * sin(angle)).toInt() - placeable.height / 2
                placeable.place(IntOffset(x, y))
            }
        }
    }
}
