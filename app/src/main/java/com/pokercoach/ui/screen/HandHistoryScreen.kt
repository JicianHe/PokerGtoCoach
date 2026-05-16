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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.data.HandHistoryRepository
import com.pokercoach.data.HandRecord
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HandHistoryScreen(
    historyRepo: HandHistoryRepository,
    onBack: () -> Unit,
    onReplay: (Int) -> Unit = {}
) {
    val records by historyRepo.flow.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(24.dp)
    ) {
        Header(title = Strings.MENU_HISTORY, onBack = onBack)
        Spacer(Modifier.height(12.dp))

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("尚無歷史紀錄，去玩幾手吧～", color = HudTextDim, fontSize = 18.sp)
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(records) { index, r ->
                HandRow(r, onClick = { onReplay(index) })
            }
        }
    }
}

@Composable
private fun HandRow(r: HandRecord, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN) }
    fun nameOf(seat: Int): String =
        if (seat == r.heroSeat) Strings.NAME_YOU
        else r.playerNames[seat] ?: "座位 $seat"
    val winnersLabel = r.winners.joinToString("、") { nameOf(it) }
    val heroWon = r.winners.contains(r.heroSeat)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(r.handNumber) { detectTapGestures(onTap = { onClick() }) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${r.handNumber}", fontWeight = FontWeight.Bold, color = HudTextPrimary, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Text(fmt.format(Date(r.timestamp)), color = HudTextDim, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                Text("BTN=${nameOf(r.buttonSeat)}", color = HudTextDim, fontSize = 12.sp)
                if (heroWon) {
                    Spacer(Modifier.width(8.dp))
                    Text("🎉 你贏", color = HudGood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("${r.reason}　贏家：$winnersLabel", color = HudGood, fontSize = 14.sp)
            if (r.finalBoard.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("最終公牌：${r.finalBoard}", color = HudTextPrimary, fontSize = 13.sp)
            }
            if (r.actions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                r.actions.takeLast(8).forEach {
                    Text(it, color = HudTextDim, fontSize = 12.sp)
                }
                if (r.actions.size > 8) {
                    Text("…（共 ${r.actions.size} 個事件）", color = HudTextDim, fontSize = 12.sp)
                }
            }
        }
    }
}

