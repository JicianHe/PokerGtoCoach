package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.data.LearningStats
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudAccent2
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudBad
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.HudWarn
import com.pokercoach.ui.theme.Strings

/**
 * 學習統計畫面：總覽 + 按位置 / 按街分項表現。
 */
@Composable
fun StatsScreen(
    statsRepo: StatsRepository,
    onBack: () -> Unit
) {
    val stats by statsRepo.flow.collectAsState(initial = LearningStats())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(32.dp)
    ) {
        Header(title = Strings.STATS_TITLE, onBack = onBack)
        Spacer(Modifier.height(16.dp))

        if (stats.totalDecisions == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Strings.STATS_NO_DATA, color = HudTextDim, fontSize = 20.sp)
            }
            return@Column
        }

        // 總覽列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(label = Strings.STATS_HANDS, value = stats.handsPlayed.toString(), color = HudAccent2, modifier = Modifier.weight(1f))
            StatCard(label = Strings.STATS_OPTIMAL_RATE, value = "${(stats.optimalRate * 100).toInt()}%", color = HudGood, modifier = Modifier.weight(1f))
            StatCard(label = Strings.STATS_BLUNDER_RATE, value = "${(stats.blunderRate * 100).toInt()}%", color = HudBad, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VerdictBar(
                label = "最佳", color = HudGood,
                value = stats.optimal, total = stats.totalDecisions,
                modifier = Modifier.weight(1f)
            )
            VerdictBar(
                label = "可接受", color = HudAccent,
                value = stats.acceptable, total = stats.totalDecisions,
                modifier = Modifier.weight(1f)
            )
            VerdictBar(
                label = "次佳", color = HudWarn,
                value = stats.suboptimal, total = stats.totalDecisions,
                modifier = Modifier.weight(1f)
            )
            VerdictBar(
                label = "失誤", color = HudBad,
                value = stats.blunder, total = stats.totalDecisions,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // 按位置
        Text(Strings.STATS_BY_POSITION, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HudTextPrimary)
        Spacer(Modifier.height(8.dp))
        stats.byPosition.entries.sortedByDescending { it.value.decisions }.forEach { (pos, s) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(pos, modifier = Modifier.width(60.dp), color = HudTextPrimary, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEEE5DA))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(s.rate.toFloat().coerceIn(0f, 1f))
                            .background(HudGood)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${(s.rate * 100).toInt()}% (${s.decisions})", color = HudTextDim, fontSize = 13.sp, modifier = Modifier.width(90.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = HudTextDim, fontSize = 14.sp)
            }
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = HudTextPrimary)
        }
    }
}

@Composable
private fun VerdictBar(label: String, color: Color, value: Int, total: Int, modifier: Modifier = Modifier) {
    val pct = if (total == 0) 0f else value.toFloat() / total
    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = HudTextDim, fontSize = 13.sp)
            Text("$value", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFEEE5DA))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pct)
                        .background(color)
                )
            }
        }
    }
}

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .size(width = 80.dp, height = 36.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { onBack() }) },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹ ${Strings.BACK}", color = HudTextPrimary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(20.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = HudTextPrimary)
    }
}
