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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.data.HandHistoryRepository
import com.pokercoach.data.SettingsRepository
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    statsRepo: StatsRepository,
    historyRepo: HandHistoryRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val s by settingsRepo.settingsFlow.collectAsState(initial = SettingsRepository.Settings())
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Header(title = Strings.MENU_SETTINGS, onBack = onBack)
        Spacer(Modifier.height(16.dp))

        ToggleRow(
            label = Strings.SETTINGS_SOUND,
            checked = s.soundOn,
            onChange = { v -> scope.launch { settingsRepo.setSound(v) } }
        )
        ToggleRow(
            label = Strings.SETTINGS_HAPTIC,
            checked = s.hapticOn,
            onChange = { v -> scope.launch { settingsRepo.setHaptic(v) } }
        )
        ToggleRow(
            label = Strings.SETTINGS_ANIMATIONS,
            checked = s.animationsOn,
            onChange = { v -> scope.launch { settingsRepo.setAnimations(v) } }
        )

        Spacer(Modifier.height(20.dp))
        Text(Strings.SETTINGS_AI_DIFFICULTY, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HudTextPrimary)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DifficultyChip(
                label = Strings.SETTINGS_DIFF_GTO,
                selected = s.difficulty == SettingsRepository.Difficulty.PURE_GTO,
                modifier = Modifier.weight(1f),
                onClick = { scope.launch { settingsRepo.setDifficulty(SettingsRepository.Difficulty.PURE_GTO) } }
            )
            DifficultyChip(
                label = Strings.SETTINGS_DIFF_MIXED,
                selected = s.difficulty == SettingsRepository.Difficulty.MIXED_PROFILES,
                modifier = Modifier.weight(1f),
                onClick = { scope.launch { settingsRepo.setDifficulty(SettingsRepository.Difficulty.MIXED_PROFILES) } }
            )
            DifficultyChip(
                label = Strings.SETTINGS_DIFF_FISH,
                selected = s.difficulty == SettingsRepository.Difficulty.FISH_POOL,
                modifier = Modifier.weight(1f),
                onClick = { scope.launch { settingsRepo.setDifficulty(SettingsRepository.Difficulty.FISH_POOL) } }
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("HUD 顯示", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HudTextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("挑你想看的資訊；其餘隱藏以減少干擾。", color = HudTextDim, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            label = "GTO 策略分佈",
            checked = s.hudShowGtoBars,
            onChange = { v -> scope.launch { settingsRepo.setHudShowGtoBars(v) } }
        )
        ToggleRow(
            label = "EV 明細",
            checked = s.hudShowEvBreakdown,
            onChange = { v -> scope.launch { settingsRepo.setHudShowEvBreakdown(v) } }
        )
        ToggleRow(
            label = "對手 AI 推理",
            checked = s.hudShowAiInsight,
            onChange = { v -> scope.launch { settingsRepo.setHudShowAiInsight(v) } }
        )
        ToggleRow(
            label = "翻後決策清單",
            checked = s.hudShowPostflopChecklist,
            onChange = { v -> scope.launch { settingsRepo.setHudShowPostflopChecklist(v) } }
        )
        ToggleRow(
            label = "底池賠率",
            checked = s.hudShowPotOdds,
            onChange = { v -> scope.launch { settingsRepo.setHudShowPotOdds(v) } }
        )

        Spacer(Modifier.height(36.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { confirmReset = true }) },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Strings.SETTINGS_RESET, fontSize = 16.sp, color = HudTextPrimary)
                Text("›", fontSize = 22.sp, color = HudTextDim)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(Strings.SETTINGS_RESET) },
            text = { Text(Strings.SETTINGS_RESET_CONFIRM) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        statsRepo.reset()
                        historyRepo.clear()
                    }
                    confirmReset = false
                }) { Text(Strings.CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(Strings.CANCEL) }
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 18.sp, color = HudTextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = HudAccent)
        )
    }
}

@Composable
private fun DifficultyChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(64.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) HudAccent else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (selected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else HudTextPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
