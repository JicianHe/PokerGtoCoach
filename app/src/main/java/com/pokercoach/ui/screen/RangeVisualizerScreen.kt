package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position
import com.pokercoach.core.model.Rank
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import com.pokercoach.ui.theme.ActionCallGreen
import com.pokercoach.ui.theme.ActionFoldGray
import com.pokercoach.ui.theme.ActionRaisePink
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings

/**
 * 範圍視覺化：13×13 起手牌格，依當前情境上色。
 *   - 紅粉：加注主導
 *   - 綠：跟注主導
 *   - 灰：蓋牌
 *   - 漸層：混合策略（多色加權混合）
 */
@Composable
fun RangeVisualizerScreen(onBack: () -> Unit) {
    var scenario by remember { mutableStateOf<RangeScenario>(PreflopRangeManager.supportedScenarios().first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(24.dp)
    ) {
        Header(title = Strings.RANGE_TITLE, onBack = onBack)
        Spacer(Modifier.height(12.dp))

        // 情境選擇器
        Text(Strings.RANGE_SELECT, color = HudTextDim, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PreflopRangeManager.supportedScenarios()) { sc ->
                ScenarioChip(
                    label = sc.displayNameZh,
                    selected = sc == scenario,
                    onClick = { scenario = sc }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // 主表
            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                RangeGrid(scenario)
            }
            Spacer(Modifier.width(20.dp))
            // 圖例
            Column(modifier = Modifier.width(160.dp)) {
                LegendRow(ActionRaisePink, Strings.LEGEND_RAISE)
                LegendRow(ActionCallGreen, Strings.LEGEND_CALL)
                LegendRow(HudAccent, Strings.LEGEND_MIXED)
                LegendRow(ActionFoldGray, Strings.LEGEND_FOLD)
            }
        }
    }
}

@Composable
private fun ScenarioChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .height(36.dp)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) HudAccent else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (selected) 3.dp else 1.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else HudTextPrimary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun RangeGrid(scenario: RangeScenario) {
    val matrix = PreflopRangeManager.matrixFor(scenario)
    // ranks 由高到低
    val ranks: List<Rank> = Rank.values().reversed().toList()

    Column(modifier = Modifier.fillMaxSize()) {
        for (i in ranks.indices) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (j in ranks.indices) {
                    val r1 = ranks[i]
                    val r2 = ranks[j]
                    val hc = when {
                        i == j -> HandClass(r1, r1, suited = false)
                        i < j -> HandClass(r1, r2, suited = true)       // 上三角同花（r1 高在前）
                        else -> HandClass(r2, r1, suited = false)        // 下三角不同花
                    }
                    val s = matrix.get(hc)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(1.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorFor(s)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(hc.toString(), fontSize = 10.sp, color = HudTextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = HudTextPrimary, fontSize = 13.sp)
    }
}

private fun colorFor(s: MixedStrategy): Color {
    if (s.fold >= 0.95) return ActionFoldGray
    if (s.raise >= 0.70) return ActionRaisePink
    if (s.call >= 0.70) return ActionCallGreen
    // 混合策略：依各動作頻率加權
    val r = ActionRaisePink
    val c = ActionCallGreen
    val f = ActionFoldGray
    return blend(
        listOf(
            r to s.raise.toFloat(),
            c to (s.call + s.check).toFloat(),
            f to s.fold.toFloat()
        )
    )
}

private fun blend(parts: List<Pair<Color, Float>>): Color {
    var r = 0f; var g = 0f; var b = 0f; var w = 0f
    for ((c, weight) in parts) {
        if (weight <= 0f) continue
        r += c.red * weight; g += c.green * weight; b += c.blue * weight; w += weight
    }
    if (w <= 0f) return ActionFoldGray
    return Color(r / w, g / w, b / w, 1f)
}
