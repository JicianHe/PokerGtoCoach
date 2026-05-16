package com.pokercoach.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pokercoach.core.model.Card
import com.pokercoach.ui.cards.CuteCardView

/**
 * 既有 CardView API 的可愛卡通版實作 — 內部轉發到 CuteCardView。
 * 保留此檔以避免大量改動既有引用點。
 */
@Composable
fun CardView(
    card: Card?,
    widthDp: Int = 56,
    faceDown: Boolean = false,
    modifier: Modifier = Modifier
) {
    CuteCardView(card = card, widthDp = widthDp, faceDown = faceDown, modifier = modifier)
}
