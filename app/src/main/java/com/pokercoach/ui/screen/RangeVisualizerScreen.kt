package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Rank
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import com.pokercoach.ui.theme.ActionCallGreen
import com.pokercoach.ui.theme.ActionFoldGray
import com.pokercoach.ui.theme.ActionRaisePink
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings

/**
 * 範圍視覺化：13×13 起手牌格，依當前情境上色。
 *
 * 互動：
 *   - 上方輸入框可打 "AKs"、"QQ"、"72o" 直接定位
 *   - 點選格子也會把該手牌設為「選定」
 *   - 右側面板顯示該手牌的混合策略明細（raise/call/check/fold % + combos）
 */
@Composable
fun RangeVisualizerScreen(onBack: () -> Unit) {
    var scenario by remember { mutableStateOf<RangeScenario>(PreflopRangeManager.supportedScenarios().first()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<HandClass?>(null) }

    // 解析輸入
    val parsedFromQuery = remember(query) { parseHand(query) }
    val highlight = selected ?: parsedFromQuery

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(24.dp)
    ) {
        Header(title = Strings.RANGE_TITLE, onBack = onBack)
        Spacer(Modifier.height(12.dp))

        // 情境
        Text(Strings.RANGE_SELECT, color = HudTextDim, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PreflopRangeManager.supportedScenarios()) { sc ->
                ScenarioChip(
                    label = sc.displayNameZh,
                    selected = sc == scenario,
                    onClick = { scenario = sc; selected = null }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 搜尋框
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.uppercase().take(4) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(Strings.RANGE_SEARCH_HINT, color = HudTextDim, fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                RangeGrid(
                    scenario = scenario,
                    highlight = highlight,
                    onCellClick = { hc ->
                        selected = if (selected == hc) null else hc
                        query = ""
                    }
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.width(200.dp)) {
                LegendRow(ActionRaisePink, Strings.LEGEND_RAISE)
                LegendRow(ActionCallGreen, Strings.LEGEND_CALL)
                LegendRow(HudAccent, Strings.LEGEND_MIXED)
                LegendRow(ActionFoldGray, Strings.LEGEND_FOLD)
                Spacer(Modifier.height(14.dp))
                if (highlight != null) {
                    HandDetailCard(
                        hand = highlight,
                        strategy = PreflopRangeManager.matrixFor(scenario).get(highlight),
                        onClear = { selected = null; query = "" }
                    )
                } else {
                    Text(
                        "點選格子或輸入手牌查看明細",
                        color = HudTextDim,
                        fontSize = 12.sp
                    )
                }
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
private fun RangeGrid(
    scenario: RangeScenario,
    highlight: HandClass?,
    onCellClick: (HandClass) -> Unit
) {
    val matrix = PreflopRangeManager.matrixFor(scenario)
    val ranks: List<Rank> = Rank.values().reversed().toList()

    Column(modifier = Modifier.fillMaxSize()) {
        for (i in ranks.indices) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (j in ranks.indices) {
                    val r1 = ranks[i]
                    val r2 = ranks[j]
                    val hc = when {
                        i == j -> HandClass(r1, r1, suited = false)
                        i < j -> HandClass(r1, r2, suited = true)
                        else -> HandClass(r2, r1, suited = false)
                    }
                    val s = matrix.get(hc)
                    val isHi = (highlight == hc)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(1.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorFor(s))
                            .border(
                                width = if (isHi) 2.dp else 0.dp,
                                color = if (isHi) HudGood else Color.Transparent,
                                shape = RoundedCornerShape(3.dp)
                            )
                            .pointerInput(hc) { detectTapGestures(onTap = { onCellClick(hc) }) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            hc.toString(),
                            fontSize = 10.sp,
                            color = HudTextPrimary,
                            fontWeight = if (isHi) FontWeight.ExtraBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HandDetailCard(hand: HandClass, strategy: MixedStrategy, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HudPanel),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hand.toString(),
                    color = HudTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(8.dp))
                Text("${Strings.RANGE_COMBOS} ${hand.combos}", color = HudTextDim, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    Strings.RANGE_CLEAR,
                    color = HudAccent,
                    fontSize = 11.sp,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClear() }) }
                )
            }
            Spacer(Modifier.height(8.dp))
            StratBar(Strings.RANGE_RAISE, strategy.raise, ActionRaisePink)
            StratBar(Strings.RANGE_CALL, strategy.call, ActionCallGreen)
            StratBar(Strings.RANGE_CHECK, strategy.check, ActionCallGreen.copy(alpha = 0.6f))
            StratBar(Strings.RANGE_FOLD, strategy.fold, ActionFoldGray)
        }
    }
}

@Composable
private fun StratBar(label: String, freq: Double, color: Color) {
    val pct = (freq * 100).toInt()
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = HudTextPrimary, fontSize = 11.sp, modifier = Modifier.width(36.dp))
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFEFEFEF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(freq.toFloat().coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(color)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("$pct%", color = HudTextDim, fontSize = 10.sp, modifier = Modifier.width(28.dp))
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

/**
 * 解析使用者輸入為 HandClass。
 * 支援：
 *   - "AA"、"77" 對子
 *   - "AKs"、"akS" 同花
 *   - "AKo"、"AK" 不同花（無後綴預設不同花）
 * 不合法回 null。
 */
internal fun parseHand(input: String): HandClass? {
    val s = input.trim().uppercase()
    if (s.length < 2) return null
    val r1 = runCatching { Rank.fromChar(s[0]) }.getOrNull() ?: return null
    val r2 = runCatching { Rank.fromChar(s[1]) }.getOrNull() ?: return null
    val (hi, lo) = if (r1.value >= r2.value) r1 to r2 else r2 to r1
    if (hi == lo) return HandClass(hi, lo, suited = false)
    return when (s.getOrNull(2)?.uppercaseChar()) {
        'S' -> HandClass(hi, lo, suited = true)
        'O', null -> HandClass(hi, lo, suited = false)
        else -> null
    }
}
