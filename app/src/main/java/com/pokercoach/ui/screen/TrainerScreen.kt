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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokercoach.core.game.Street
import com.pokercoach.core.model.Action
import com.pokercoach.core.trainer.TrainerProblemBank
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.theme.ActionCallGreen
import com.pokercoach.ui.theme.ActionCheckBlue
import com.pokercoach.ui.theme.ActionFoldGray
import com.pokercoach.ui.theme.ActionRaisePink
import com.pokercoach.ui.theme.HudAccent
import com.pokercoach.ui.theme.HudBad
import com.pokercoach.ui.theme.HudBg
import com.pokercoach.ui.theme.HudGood
import com.pokercoach.ui.theme.HudTextDim
import com.pokercoach.ui.theme.HudTextPrimary
import com.pokercoach.ui.theme.Strings
import com.pokercoach.ui.theme.VerdictLevel
import com.pokercoach.core.stats.DecisionGrader
import kotlinx.coroutines.launch

/**
 * 訓練畫面：兩種模式切換
 *   - PREFLOP：從 TrainerProblemBank.randomPreflopProblem() 隨機抽
 *   - POSTFLOP：循環走 TrainerProblemBank.POSTFLOP_PROBLEMS
 *
 * 答題後立即顯示正解與 GTO 推理，按「下一題」抽下一題。
 * 答對亦寫入 StatsRepository 作為學習紀錄。
 */
@Composable
fun TrainerScreen(
    statsRepo: StatsRepository,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf(TrainerMode.PREFLOP) }
    var score by remember { mutableIntStateOf(0) }
    var attempted by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBg)
            .padding(24.dp)
    ) {
        // Header + 切換 + 分數
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Header(title = Strings.TRAIN_TITLE, onBack = onBack)
            Spacer(Modifier.width(20.dp))
            ModeChip("翻前隨機", mode == TrainerMode.PREFLOP) { mode = TrainerMode.PREFLOP }
            Spacer(Modifier.width(8.dp))
            ModeChip("翻後場景", mode == TrainerMode.POSTFLOP) { mode = TrainerMode.POSTFLOP }
            Spacer(Modifier.weight(1f))
            ScoreBlock(score, attempted, streak)
        }
        Spacer(Modifier.height(20.dp))

        when (mode) {
            TrainerMode.PREFLOP -> PreflopTrainer(
                onResult = { correct ->
                    attempted += 1
                    if (correct) { score += 1; streak += 1 } else streak = 0
                }
            )
            TrainerMode.POSTFLOP -> PostflopTrainer(
                statsRepo = statsRepo,
                onResult = { correct ->
                    attempted += 1
                    if (correct) { score += 1; streak += 1 } else streak = 0
                }
            )
        }
    }
}

private enum class TrainerMode { PREFLOP, POSTFLOP }

@Composable
private fun ScoreBlock(score: Int, attempted: Int, streak: Int) {
    val acc = if (attempted == 0) 0 else (score * 100 / attempted)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScoreCell(Strings.TRAIN_SCORE, "$score / $attempted")
        Spacer(Modifier.width(8.dp))
        ScoreCell(Strings.TRAIN_ACCURACY, "$acc%")
        Spacer(Modifier.width(8.dp))
        ScoreCell(Strings.TRAIN_STREAK, "🔥 $streak")
    }
}

@Composable
private fun ScoreCell(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, color = HudTextDim, fontSize = 11.sp)
            Text(value, color = HudTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            Text(label, color = if (selected) Color.White else HudTextPrimary, fontSize = 13.sp)
        }
    }
}

// =====================================================================
// 翻前訓練
// =====================================================================
@Composable
private fun PreflopTrainer(onResult: (Boolean) -> Unit) {
    var problem by remember { mutableStateOf(TrainerProblemBank.randomPreflopProblem()) }
    var chosen by remember { mutableStateOf<Action.Kind?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 題幹卡
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(problem.scenario.displayNameZh, color = HudTextDim, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("你的手牌：${problem.hand}", color = HudTextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("該怎麼打？", color = HudTextPrimary, fontSize = 16.sp)
            }
        }

        // 四個答案
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnswerButton(Strings.FOLD, ActionFoldGray, chosen, Action.Kind.FOLD, problem.correctActions, modifier = Modifier.weight(1f)) {
                if (chosen == null) {
                    chosen = it
                    val ok = problem.correctActions.contains(it)
                    onResult(ok)
                }
            }
            AnswerButton(Strings.CHECK, ActionCheckBlue, chosen, Action.Kind.CHECK, problem.correctActions, modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; onResult(problem.correctActions.contains(it)) }
            }
            AnswerButton(Strings.CALL, ActionCallGreen, chosen, Action.Kind.CALL, problem.correctActions, modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; onResult(problem.correctActions.contains(it)) }
            }
            AnswerButton(Strings.RAISE, ActionRaisePink, chosen, Action.Kind.RAISE, problem.correctActions, modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; onResult(problem.correctActions.contains(it)) }
            }
        }

        // 解析
        if (chosen != null) {
            val rec = problem.recommendation
            val verdict = DecisionGrader.grade(chosen!!, rec)
            ExplanationCard(
                verdict = verdict,
                title = if (problem.correctActions.contains(chosen!!)) Strings.TRAIN_CORRECT else Strings.TRAIN_WRONG,
                body = Strings.verdictExplain(verdict, chosen!!, rec) +
                    "\n\nGTO 策略：${Strings.explainStrategySummary(rec)}",
                onNext = {
                    problem = TrainerProblemBank.randomPreflopProblem()
                    chosen = null
                }
            )
        }
    }
}

// =====================================================================
// 翻後訓練
// =====================================================================
@Composable
private fun PostflopTrainer(statsRepo: StatsRepository, onResult: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var idx by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Action.Kind?>(null) }
    val problem = TrainerProblemBank.POSTFLOP_PROBLEMS[idx % TrainerProblemBank.POSTFLOP_PROBLEMS.size]

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(problem.title, color = HudTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(problem.scenario, color = HudTextDim, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text("位置：${Strings.position(problem.heroPosition)}   手牌：${problem.heroHand}", color = HudTextPrimary, fontSize = 16.sp)
                Text("公牌：${problem.board}", color = HudTextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("底池 ${"%.1f".format(problem.potBb)}bb　需跟 ${"%.1f".format(problem.toCallBb)}bb", color = HudAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnswerButton(Strings.FOLD,  ActionFoldGray,  chosen, Action.Kind.FOLD,  setOf(problem.correctAction), modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; handlePostflopResult(it, problem, scope, statsRepo, onResult) }
            }
            AnswerButton(Strings.CHECK, ActionCheckBlue, chosen, Action.Kind.CHECK, setOf(problem.correctAction), modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; handlePostflopResult(it, problem, scope, statsRepo, onResult) }
            }
            AnswerButton(Strings.CALL,  ActionCallGreen, chosen, Action.Kind.CALL,  setOf(problem.correctAction), modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; handlePostflopResult(it, problem, scope, statsRepo, onResult) }
            }
            AnswerButton(Strings.RAISE, ActionRaisePink, chosen, Action.Kind.RAISE, setOf(problem.correctAction), modifier = Modifier.weight(1f)) {
                if (chosen == null) { chosen = it; handlePostflopResult(it, problem, scope, statsRepo, onResult) }
            }
        }

        if (chosen != null) {
            val correct = chosen == problem.correctAction
            ExplanationCard(
                verdict = if (correct) VerdictLevel.Optimal else VerdictLevel.Blunder,
                title = if (correct) Strings.TRAIN_CORRECT else Strings.TRAIN_WRONG,
                body = "建議：${Strings.actionLabelByKind(problem.correctAction)}\n\n${problem.explanation}",
                onNext = {
                    idx += 1
                    chosen = null
                }
            )
        }
    }
}

private fun handlePostflopResult(
    chosen: Action.Kind,
    problem: TrainerProblemBank.PostflopProblem,
    scope: kotlinx.coroutines.CoroutineScope,
    statsRepo: StatsRepository,
    onResult: (Boolean) -> Unit
) {
    val correct = chosen == problem.correctAction
    onResult(correct)
    val verdict = if (correct) StatsRepository.VerdictBucket.OPTIMAL else StatsRepository.VerdictBucket.BLUNDER
    val street = inferStreetFromBoard(problem.board)
    scope.launch {
        statsRepo.recordDecision(verdict, problem.heroPosition, street)
    }
}

private fun inferStreetFromBoard(board: String): Street {
    val n = board.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
    return when (n) {
        3 -> Street.FLOP
        4 -> Street.TURN
        5 -> Street.RIVER
        else -> Street.FLOP
    }
}

// =====================================================================
// 共用元件
// =====================================================================
@Composable
private fun AnswerButton(
    label: String,
    color: Color,
    chosen: Action.Kind?,
    self: Action.Kind,
    correct: Set<Action.Kind>,
    modifier: Modifier = Modifier,
    onClick: (Action.Kind) -> Unit
) {
    val isAnswered = chosen != null
    val isCorrect = correct.contains(self)
    val border = when {
        !isAnswered -> Color.Transparent
        chosen == self && isCorrect -> HudGood
        chosen == self && !isCorrect -> HudBad
        isCorrect -> HudGood
        else -> Color.Transparent
    }
    Card(
        modifier = modifier
            .height(72.dp)
            .pointerInput(self) { detectTapGestures(onTap = { onClick(self) }) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(if (chosen == self) 6.dp else 2.dp),
        border = if (border == Color.Transparent) null else androidx.compose.foundation.BorderStroke(3.dp, border)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = HudTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExplanationCard(
    verdict: VerdictLevel,
    title: String,
    body: String,
    onNext: () -> Unit
) {
    val color = when (verdict) {
        VerdictLevel.Optimal, VerdictLevel.Acceptable -> HudGood
        VerdictLevel.Suboptimal -> HudAccent
        VerdictLevel.Blunder -> HudBad
        VerdictLevel.Unknown -> HudTextDim
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = HudTextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onNext() }) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = HudAccent),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Strings.TRAIN_NEXT, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
