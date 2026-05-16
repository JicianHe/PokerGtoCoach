package com.pokercoach.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.model.Card
import com.pokercoach.core.model.Suit
import com.pokercoach.ui.theme.CardBack
import com.pokercoach.ui.theme.CardBlack
import com.pokercoach.ui.theme.CardFaceBg
import com.pokercoach.ui.theme.CardRed

/**
 * 一張撲克牌的視覺呈現。可顯示正面或背面。
 *
 * 大小由 [widthDp] 控制；高度依 2.5:3.5 標準撲克牌比例自動計算。
 */
@Composable
fun CardView(
    card: Card?,
    widthDp: Int = 56,
    faceDown: Boolean = false,
    modifier: Modifier = Modifier
) {
    val heightDp = (widthDp * 1.4).toInt()
    Box(
        modifier = modifier
            .size(widthDp.dp, heightDp.dp)
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .background(if (faceDown || card == null) CardBack else CardFaceBg, RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
    ) {
        AnimatedVisibility(
            visible = !faceDown && card != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut()
        ) {
            if (card != null) CardFace(card, widthDp)
        }
    }
}

@Composable
private fun CardFace(card: Card, widthDp: Int) {
    val color = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) CardRed else CardBlack
    val rankFontSize = (widthDp * 0.42).sp
    val suitFontSize = (widthDp * 0.46).sp

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = card.rank.shorthand.toString(),
            color = color,
            fontSize = rankFontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = rankFontSize
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = card.suit.symbol,
                color = color,
                fontSize = suitFontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = suitFontSize
            )
        }
    }
}
