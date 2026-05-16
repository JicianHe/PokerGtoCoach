package com.pokercoach.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.ui.theme.ChipPurple
import com.pokercoach.ui.theme.ChipSky
import com.pokercoach.ui.theme.ChipMint
import com.pokercoach.ui.theme.ChipPink
import com.pokercoach.ui.theme.ChipPeach
import com.pokercoach.ui.theme.Strings

/**
 * 籌碼堆視覺化：依金額拆分為不同面值的籌碼疊起。
 *
 * 1bb = 1 white, 5bb = red, 25bb = green, 100bb = blue, 500bb = black.
 */
@Composable
fun ChipStack(
    amountBb: Double,
    modifier: Modifier = Modifier
) {
    if (amountBb <= 0.0) return
    val breakdown = chipBreakdown(amountBb)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for ((color, count) in breakdown) {
            ChipColumn(color = color, count = count.coerceAtMost(6))   // 最多疊 6 顆
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${formatBb(amountBb)} ${Strings.BB_UNIT}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChipColumn(color: Color, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy((-6).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .size(14.dp, 4.dp)
                    .background(color, CircleShape)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

private fun chipBreakdown(amount: Double): List<Pair<Color, Int>> {
    var remain = amount
    val list = mutableListOf<Pair<Color, Int>>()
    val denoms = listOf(
        500.0 to ChipPurple,
        100.0 to ChipSky,
        25.0  to ChipMint,
        5.0   to ChipPink,
        1.0   to ChipPeach
    )
    for ((d, c) in denoms) {
        val n = (remain / d).toInt()
        if (n > 0) {
            list += c to n
            remain -= n * d
        }
    }
    // 不足 1bb 的尾數也顯示一顆桃色籌碼
    if (list.isEmpty() && amount > 0) list += ChipPeach to 1
    return list
}

private fun formatBb(v: Double): String =
    if (v >= 10) "%.0f".format(v) else "%.1f".format(v)
