package com.pokercoach.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.model.Card
import com.pokercoach.core.model.Suit
import com.pokercoach.ui.theme.CardBackDeep
import com.pokercoach.ui.theme.CardBackPattern
import com.pokercoach.ui.theme.CardBorder
import com.pokercoach.ui.theme.CardFaceCream
import com.pokercoach.ui.theme.CardSuitBlue
import com.pokercoach.ui.theme.CardSuitPink
import com.pokercoach.ui.theme.CardSuitRed
import com.pokercoach.ui.theme.CardSuitYellow

/**
 * 原創可愛航空風撲克牌。四花色用主題符號取代傳統 ♠♥♦♣，
 * 但內部仍對應 SPADES/HEARTS/DIAMONDS/CLUBS 以保持遊戲邏輯一致。
 *
 *   SPADES   → 飛機（Plane，藍）
 *   HEARTS   → 愛心（Heart，紅）
 *   DIAMONDS → 星星（Star，黃）
 *   CLUBS    → 雲朵（Cloud，粉）
 *
 * 全部用 Canvas Path 繪製，無任何外部圖片依賴，零侵權風險。
 */

@Composable
fun CuteCardView(
    card: Card?,
    widthDp: Int = 56,
    faceDown: Boolean = false,
    modifier: Modifier = Modifier
) {
    val heightDp = (widthDp * 1.42).toInt()
    Box(
        modifier = modifier
            .size(widthDp.dp, heightDp.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (faceDown || card == null) {
                    Brush.linearGradient(listOf(CardBackPattern, CardBackDeep))
                } else {
                    Brush.linearGradient(listOf(CardFaceCream, Color(0xFFFFF6E5)))
                }
            )
            .border(1.5.dp, CardBorder, RoundedCornerShape(12.dp))
    ) {
        when {
            faceDown || card == null -> CardBackPattern(widthDp)
            else -> CardFaceContent(card, widthDp, heightDp)
        }
    }
}

@Composable
private fun CardFaceContent(card: Card, widthDp: Int, heightDp: Int) {
    val suitColor = suitColor(card.suit)
    val rankFontSize = (widthDp * 0.38).sp
    val miniSuitSize = (widthDp * 0.20f).dp
    val centerSuitSize = (widthDp * 0.48f).dp

    // 左上：點數 + 小花色
    Column(
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = card.rank.shorthand.toString(),
            color = suitColor,
            fontSize = rankFontSize,
            fontWeight = FontWeight.Black,
            lineHeight = rankFontSize
        )
        Box(modifier = Modifier.size(miniSuitSize)) {
            SuitGlyph(card.suit, modifier = Modifier.fillMaxSize())
        }
    }

    // 中央大花色
    Box(
        modifier = Modifier.fillMaxSize().padding(top = (heightDp * 0.18).dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(centerSuitSize)) {
            SuitGlyph(card.suit, modifier = Modifier.fillMaxSize())
        }
    }

    // 右下：倒轉的點數 + 小花色
    Box(
        modifier = Modifier.fillMaxSize().padding(end = 6.dp, bottom = 4.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(miniSuitSize)) {
                SuitGlyph(card.suit, modifier = Modifier.fillMaxSize(), rotated = true)
            }
            Text(
                text = card.rank.shorthand.toString(),
                color = suitColor,
                fontSize = rankFontSize * 0.95f,
                fontWeight = FontWeight.Black,
                lineHeight = rankFontSize,
                modifier = Modifier.graphicsRotated()
            )
        }
    }
}

/** 卡背：以航空圓窗主題（同心圓 + 雲朵點綴），馬卡龍配色。 */
@Composable
private fun CardBackPattern(widthDp: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 內框圓角矩形
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.84f, h * 0.84f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f)
        )

        // 中央圓窗
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.28f
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = r, center = Offset(cx, cy))
        drawCircle(color = Color.White.copy(alpha = 0.50f), radius = r * 0.7f, center = Offset(cx, cy))
        drawCircle(color = CardBackDeep, radius = r * 0.42f, center = Offset(cx, cy))

        // 雲朵點綴（左上、右下）
        drawCloud(Offset(w * 0.22f, h * 0.20f), w * 0.14f, Color.White.copy(alpha = 0.45f))
        drawCloud(Offset(w * 0.78f, h * 0.80f), w * 0.14f, Color.White.copy(alpha = 0.45f))
    }
}

// ============================================================
// 四種原創花色繪製
// ============================================================
@Composable
fun SuitGlyph(
    suit: Suit,
    modifier: Modifier = Modifier,
    rotated: Boolean = false
) {
    Canvas(modifier = modifier) {
        if (rotated) {
            rotate(180f) { drawSuitInternal(suit, size) }
        } else {
            drawSuitInternal(suit, size)
        }
    }
}

private fun DrawScope.drawSuitInternal(suit: Suit, sz: Size) {
    when (suit) {
        Suit.HEARTS -> drawHeart(sz, CardSuitRed)
        Suit.SPADES -> drawPlane(sz, CardSuitBlue)
        Suit.DIAMONDS -> drawStar(sz, CardSuitYellow)
        Suit.CLUBS -> drawCloud2(sz, CardSuitPink)
    }
}

private fun DrawScope.drawHeart(sz: Size, color: Color) {
    val w = sz.width; val h = sz.height
    val path = Path().apply {
        // 心型：兩個半圓 + 下方尖角
        moveTo(w * 0.5f, h * 0.86f)
        cubicTo(
            -w * 0.10f, h * 0.55f,
            w * 0.15f, h * 0.05f,
            w * 0.5f, h * 0.32f
        )
        cubicTo(
            w * 0.85f, h * 0.05f,
            w * 1.10f, h * 0.55f,
            w * 0.5f, h * 0.86f
        )
        close()
    }
    drawPath(path = path, color = color)
    // 加一點高光
    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = w * 0.07f,
        center = Offset(w * 0.32f, h * 0.30f)
    )
}

private fun DrawScope.drawPlane(sz: Size, color: Color) {
    val w = sz.width; val h = sz.height
    // 機身（菱形）
    val body = Path().apply {
        moveTo(w * 0.5f, h * 0.10f)
        lineTo(w * 0.58f, h * 0.55f)
        lineTo(w * 0.5f, h * 0.90f)
        lineTo(w * 0.42f, h * 0.55f)
        close()
    }
    drawPath(body, color)
    // 主翼
    val wing = Path().apply {
        moveTo(w * 0.10f, h * 0.65f)
        lineTo(w * 0.90f, h * 0.65f)
        lineTo(w * 0.62f, h * 0.50f)
        lineTo(w * 0.38f, h * 0.50f)
        close()
    }
    drawPath(wing, color)
    // 尾翼
    val tail = Path().apply {
        moveTo(w * 0.32f, h * 0.88f)
        lineTo(w * 0.68f, h * 0.88f)
        lineTo(w * 0.57f, h * 0.78f)
        lineTo(w * 0.43f, h * 0.78f)
        close()
    }
    drawPath(tail, color)
    // 窗
    drawCircle(Color.White.copy(alpha = 0.7f), w * 0.06f, Offset(w * 0.5f, h * 0.32f))
}

private fun DrawScope.drawStar(sz: Size, color: Color) {
    val w = sz.width; val h = sz.height
    val cx = w / 2f; val cy = h / 2f
    val outerR = minOf(w, h) * 0.46f
    val innerR = outerR * 0.42f
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = -Math.PI / 2 + i * Math.PI / points
        val x = cx + r * Math.cos(angle).toFloat()
        val y = cy + r * Math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    // 高光
    drawCircle(Color.White.copy(alpha = 0.5f), outerR * 0.18f, Offset(cx - outerR * 0.2f, cy - outerR * 0.2f))
}

private fun DrawScope.drawCloud2(sz: Size, color: Color) {
    val w = sz.width; val h = sz.height
    // 三個圓組成的雲
    drawCircle(color, w * 0.22f, Offset(w * 0.30f, h * 0.55f))
    drawCircle(color, w * 0.28f, Offset(w * 0.55f, h * 0.50f))
    drawCircle(color, w * 0.20f, Offset(w * 0.75f, h * 0.60f))
    // 底邊
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.18f, h * 0.55f),
        size = Size(w * 0.68f, h * 0.20f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, h * 0.10f)
    )
}

private fun DrawScope.drawCloud(center: Offset, scale: Float, color: Color) {
    drawCircle(color, scale * 0.55f, Offset(center.x - scale * 0.5f, center.y))
    drawCircle(color, scale * 0.70f, center)
    drawCircle(color, scale * 0.50f, Offset(center.x + scale * 0.6f, center.y + scale * 0.1f))
}

internal fun suitColor(suit: Suit): Color = when (suit) {
    Suit.HEARTS -> CardSuitRed
    Suit.SPADES -> CardSuitBlue
    Suit.DIAMONDS -> CardSuitYellow
    Suit.CLUBS -> CardSuitPink
}

// 簡易 180° 旋轉 modifier（給點數倒立用）
private fun Modifier.graphicsRotated(): Modifier =
    this.graphicsLayer(rotationZ = 180f)
