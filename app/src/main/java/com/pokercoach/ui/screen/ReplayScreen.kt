package com.pokercoach.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.data.HandHistoryRepository
import com.pokercoach.data.HandRecord
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudAccent2
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudPanel
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings
import kotlinx.coroutines.delay

/**
 * Replay 整局回放畫面。
 *
 * 概念：直接拿 HandRecord.actions（文字描述）做 step-by-step 顯示，
 * 同步推進公牌（依目前 step 對應到該街是否已發出）。
 *
 * 控制：◀ 上一步 / ▶ 下一步 / ⏵ 自動播放（1.2 秒一步）/ ⏸ 暫停 / ↻ 重置
 */
@Composable
fun ReplayScreen(
    historyRepo: HandHistoryRepository,
    handIndex: Int,
    onBack: () -> Unit
) {
    val records by historyRepo.flow.collectAsState(initial = emptyList())
    val record = records.getOrNull(handIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(24.dp)
    ) {
        Header(title = "回放 #${record?.handNumber ?: "?"}", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        if (record == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("找不到該手紀錄", color = HudTextDim, fontSize = 18.sp)
            }
            return@Column
        }

        ReplayBody(record)
    }
}

@Composable
private fun ReplayBody(record: HandRecord) {
    val totalSteps = record.actions.size
    var step by remember { mutableIntStateOf(0) }
    var autoPlay by remember { mutableStateOf(false) }

    // 自動播放
    LaunchedEffect(autoPlay, step, totalSteps) {
        if (autoPlay && step < totalSteps) {
            delay(1200)
            step = (step + 1).coerceAtMost(totalSteps)
        } else if (step >= totalSteps) {
            autoPlay = false
        }
    }

    // 推導目前可見公牌（看到 "── 翻牌" 後就顯示 flop，看到 "── 轉牌" 後加 turn ...）
    val visibleBoard = remember(step, record) {
        val lines = record.actions.take(step)
        buildString {
            if (lines.any { it.contains("── 翻牌") } && record.flop.isNotBlank()) append(record.flop)
            if (lines.any { it.contains("── 轉牌") } && record.turn.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(record.turn)
            }
            if (lines.any { it.contains("── 河牌") } && record.river.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(record.river)
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // 左：牌桌摘要
        Column(modifier = Modifier.weight(0.55f).padding(end = 12.dp)) {
            SummaryCard(record)
            Spacer(Modifier.height(10.dp))
            BoardCard(visibleBoard)
            Spacer(Modifier.height(10.dp))
            HeroHoleCard(record)
        }
        // 右：事件列表 + 控制
        Column(modifier = Modifier.weight(0.45f)) {
            ControlBar(
                step = step,
                total = totalSteps,
                autoPlay = autoPlay,
                onPrev = { step = (step - 1).coerceAtLeast(0); autoPlay = false },
                onNext = { step = (step + 1).coerceAtMost(totalSteps); autoPlay = false },
                onTogglePlay = { autoPlay = !autoPlay },
                onReset = { step = 0; autoPlay = false }
            )
            Spacer(Modifier.height(8.dp))
            EventList(record.actions, step)
        }
    }
}

@Composable
private fun SummaryCard(r: HandRecord) {
    fun nameOf(seat: Int): String =
        if (seat == r.heroSeat) Strings.NAME_YOU
        else r.playerNames[seat] ?: "座位 $seat"
    val winnersLabel = r.winners.joinToString("、") { nameOf(it) }
    val heroWon = r.winners.contains(r.heroSeat)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HudPanel),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("第 ${r.handNumber} 手", fontWeight = FontWeight.Bold, color = HudTextPrimary, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Text("BTN：${nameOf(r.buttonSeat)}", color = HudTextDim, fontSize = 12.sp)
                if (heroWon) {
                    Spacer(Modifier.width(12.dp))
                    Text("🎉 你贏", color = HudGood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${r.reason}  →  贏家：$winnersLabel", color = HudGood, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BoardCard(boardText: String) {
    Card(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("公牌", color = HudTextDim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = boardText.ifBlank { "（翻牌前）" },
                color = HudTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HeroHoleCard(r: HandRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text("你的底牌", color = HudTextDim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = r.heroHole.ifBlank { "（未紀錄）" },
                color = HudTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ControlBar(
    step: Int,
    total: Int,
    autoPlay: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CtrlBtn(text = "◀", enabled = step > 0, onClick = onPrev)
        CtrlBtn(text = if (autoPlay) "⏸" else "⏵", enabled = step < total, onClick = onTogglePlay)
        CtrlBtn(text = "▶", enabled = step < total, onClick = onNext)
        CtrlBtn(text = "↻", enabled = step > 0, onClick = onReset)
        Spacer(Modifier.width(8.dp))
        Text("$step / $total", color = HudTextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CtrlBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .background(
                color = if (enabled) HudAccent else Color(0xFFE5E5E5),
                shape = RoundedCornerShape(10.dp)
            )
            .pointerInput(text, enabled) {
                detectTapGestures(onTap = { if (enabled) onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else HudTextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EventList(actions: List<String>, currentStep: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HudPanel),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        LazyColumn(modifier = Modifier.padding(8.dp)) {
            itemsIndexed(actions) { index, line ->
                val isCurrent = index == currentStep - 1
                val isFuture = index >= currentStep
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            color = if (isCurrent) HudAccent2.copy(alpha = 0.25f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = if (isCurrent) 1.dp else 0.dp,
                            color = if (isCurrent) HudAccent2 else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        color = HudTextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = line,
                        color = if (isFuture) HudTextDim else HudTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
