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
import com.pokercoach.core.game.GameEvent
import com.pokercoach.core.game.Street
import com.pokercoach.ui.common.CardView
import com.pokercoach.ui.common.ChipStack
import com.pokercoach.ui.theme.TableRail
import com.pokercoach.ui.theme.TableRailHi
import com.pokercoach.ui.theme.TableTopDeep
import com.pokercoach.ui.theme.TableTopMid
import com.pokercoach.ui.theme.TableTopMint
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
    winningSeats: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(16.dp)
            .shadow(20.dp, RoundedCornerShape(percent = 50))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(TableRailHi, TableRail)
                ),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(22.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(TableTopMint, TableTopMid, TableTopDeep)
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
            heroSeat = heroSeat,
            winningSeats = winningSeats
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
                text = "${com.pokercoach.ui.theme.Strings.POT}  ${"%.1f".format(state.pot)} ${com.pokercoach.ui.theme.Strings.BB_UNIT}",
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
private fun SeatRing(state: TableState, heroSeat: Int, winningSeats: Set<Int>) {
    // 攤牌時讓所有未蓋牌玩家亮牌
    val isShowdown = state.street == Street.SHOWDOWN
    // 取最近一次 ActionTaken 來推斷 mood
    val lastActionBySeat: Map<Int, GameEvent.ActionTaken> =
        state.log.filterIsInstance<GameEvent.ActionTaken>()
            .groupBy { it.seat }
            .mapValues { it.value.last() }

    Layout(
        content = {
            for (seat in 0..5) {
                val p = state.players.first { it.seatIndex == seat }
                val mood = computeMood(
                    isWinner = seat in winningSeats,
                    isFolded = p.status == com.pokercoach.core.game.PlayerStatus.FOLDED,
                    isAllIn = p.status == com.pokercoach.core.game.PlayerStatus.ALL_IN,
                    lastAction = lastActionBySeat[seat]
                )
                Box {
                    PlayerSeat(
                        player = p,
                        isHero = (seat == heroSeat),
                        isActor = (state.actorSeat == seat),
                        isButton = (state.buttonSeat == seat),
                        isWinner = (seat in winningSeats),
                        revealHoleCards = isShowdown && p.status != com.pokercoach.core.game.PlayerStatus.FOLDED,
                        mood = mood
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

private fun computeMood(
    isWinner: Boolean,
    isFolded: Boolean,
    isAllIn: Boolean,
    lastAction: GameEvent.ActionTaken?
): SeatMood {
    if (isWinner) return SeatMood.HAPPY
    if (isFolded) return SeatMood.SAD
    if (isAllIn) return SeatMood.NERVOUS
    return when (val a = lastAction?.action) {
        is com.pokercoach.core.model.Action.Raise ->
            if (a.amount >= 6.0) SeatMood.AGGRESSIVE else SeatMood.NEUTRAL
        is com.pokercoach.core.model.Action.Call -> SeatMood.THINKING
        is com.pokercoach.core.model.Action.Check -> SeatMood.NEUTRAL
        is com.pokercoach.core.model.Action.Fold -> SeatMood.SAD
        null -> SeatMood.NEUTRAL
    }
}
