package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.ui.theme.BgGradientBottom
import com.pokercoach.ui.theme.BgGradientMid
import com.pokercoach.ui.theme.BgGradientTop
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings

/**
 * 開場教學引導：4 頁滑動式介紹，首次啟動顯示。
 *
 * 設計：粉紅→薄荷漸層底，中央卡片含大 emoji + 標題 + 內文。
 * 底部進度圓點 + 略過/下一步/開始按鈕。
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = Strings.ONBOARDING_PAGES
    var index by remember { mutableIntStateOf(0) }
    val isLast = index == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgGradientTop, BgGradientMid, BgGradientBottom)
                )
            )
            .padding(horizontal = 64.dp, vertical = 40.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 頂部：標題 + 略過
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Strings.ONBOARDING_TITLE,
                    color = HudTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onFinish) {
                    Text(Strings.ONBOARDING_SKIP, color = HudTextDim, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            // 中央卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = HudPanel),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                val p = pages[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(p.emoji, fontSize = 86.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        p.title,
                        color = HudTextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        p.body,
                        color = HudTextDim,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 底部：進度點 + 按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 進度點
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.forEachIndexed { i, _ ->
                        val active = i == index
                        Box(
                            modifier = Modifier
                                .size(if (active) 14.dp else 10.dp)
                                .clip(CircleShape)
                                .background(if (active) HudAccent else HudTextDim.copy(alpha = 0.4f))
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (isLast) onFinish()
                        else index += 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HudAccent),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.height(54.dp).width(180.dp)
                ) {
                    Text(
                        if (isLast) Strings.ONBOARDING_DONE else Strings.ONBOARDING_NEXT,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
