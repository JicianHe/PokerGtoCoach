package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.ui.theme.BgGradientBottom
import com.pokercoach.ui.theme.BgGradientMid
import com.pokercoach.ui.theme.BgGradientTop
import com.pokercoach.ui.theme.ChipMint
import com.pokercoach.ui.theme.ChipPeach
import com.pokercoach.ui.theme.ChipPink
import com.pokercoach.ui.theme.ChipPurple
import com.pokercoach.ui.theme.ChipSky
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings

/**
 * 主選單畫面：六大入口卡片。
 *
 * 設計：粉紅 → 薄荷漸層背景，標題「撲克 GTO 教練」+ 副標，
 * 下方 3×2 卡片網格（自由對戰／訓練／範圍／統計／歷史／設定）。
 */
@Composable
fun MainMenuScreen(
    onFreePlay: () -> Unit,
    onTrainer: () -> Unit,
    onRanges: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgGradientTop, BgGradientMid, BgGradientBottom)
                )
            )
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 標題
            Text(
                text = Strings.APP_NAME,
                color = HudTextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = Strings.MENU_SUBTITLE,
                color = HudTextDim,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(28.dp))

            // 第一排
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MenuTile(
                    title = Strings.MENU_FREEPLAY,
                    subtitle = "與五個 AI 對戰並接受 GTO 點評",
                    color = HudAccent,
                    modifier = Modifier.weight(1f),
                    onClick = onFreePlay
                )
                MenuTile(
                    title = Strings.MENU_TRAINER,
                    subtitle = "翻前隨機題 + 30 道翻後手寫題",
                    color = ChipMint,
                    modifier = Modifier.weight(1f),
                    onClick = onTrainer
                )
                MenuTile(
                    title = Strings.MENU_RANGES,
                    subtitle = "13×13 範圍圖視覺化",
                    color = ChipSky,
                    modifier = Modifier.weight(1f),
                    onClick = onRanges
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MenuTile(
                    title = Strings.MENU_STATS,
                    subtitle = "學習表現與位置別準確率",
                    color = ChipPeach,
                    modifier = Modifier.weight(1f),
                    onClick = onStats
                )
                MenuTile(
                    title = Strings.MENU_HISTORY,
                    subtitle = "最近 200 手回放與事件",
                    color = ChipPink,
                    modifier = Modifier.weight(1f),
                    onClick = onHistory
                )
                MenuTile(
                    title = Strings.MENU_SETTINGS,
                    subtitle = "音效／震動／AI 難度",
                    color = ChipPurple,
                    modifier = Modifier.weight(1f),
                    onClick = onSettings
                )
            }
        }
    }
}

@Composable
private fun MenuTile(
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(180.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color)
            )
            Column {
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = HudTextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = HudTextDim
                )
            }
        }
    }
}
