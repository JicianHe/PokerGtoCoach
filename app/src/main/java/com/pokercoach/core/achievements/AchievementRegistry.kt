package com.pokercoach.core.achievements

import com.pokercoach.data.LearningStats

/**
 * 成就：純資料驅動 — 每個 Achievement 提供「解鎖判定函式」。
 *
 * UI 在統計頁顯示已解鎖/未解鎖；StatsRepository 在每次寫入時掃描並把 id
 * 加進 LearningStats.unlockedAchievements，永久持久化。
 *
 * 全部使用中文標題 + 描述；用 emoji 當圖示，不依賴外部資源。
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    /** 0..1 進度（用於還沒解鎖時顯示）。 */
    val progress: (LearningStats) -> Float,
    val isUnlocked: (LearningStats) -> Boolean
)

object AchievementRegistry {

    private fun ratio(cur: Int, target: Int): Float =
        (cur.toFloat() / target).coerceIn(0f, 1f)

    val ALL: List<Achievement> = listOf(
        Achievement(
            id = "first_hand",
            title = "踏出第一步",
            description = "完成第 1 手牌",
            icon = "🌱",
            progress = { ratio(it.handsPlayed, 1) },
            isUnlocked = { it.handsPlayed >= 1 }
        ),
        Achievement(
            id = "hands_10",
            title = "熱身完成",
            description = "完成 10 手牌",
            icon = "🔥",
            progress = { ratio(it.handsPlayed, 10) },
            isUnlocked = { it.handsPlayed >= 10 }
        ),
        Achievement(
            id = "hands_100",
            title = "百手大師",
            description = "完成 100 手牌",
            icon = "💯",
            progress = { ratio(it.handsPlayed, 100) },
            isUnlocked = { it.handsPlayed >= 100 }
        ),
        Achievement(
            id = "hands_500",
            title = "持之以恆",
            description = "完成 500 手牌",
            icon = "🏆",
            progress = { ratio(it.handsPlayed, 500) },
            isUnlocked = { it.handsPlayed >= 500 }
        ),
        Achievement(
            id = "first_win",
            title = "首勝",
            description = "贏下你的第一手",
            icon = "🎉",
            progress = { ratio(it.heroHandsWon, 1) },
            isUnlocked = { it.heroHandsWon >= 1 }
        ),
        Achievement(
            id = "wins_50",
            title = "穩定盈利",
            description = "累積 50 場勝利",
            icon = "💰",
            progress = { ratio(it.heroHandsWon, 50) },
            isUnlocked = { it.heroHandsWon >= 50 }
        ),
        Achievement(
            id = "streak_5",
            title = "連續好棒",
            description = "連續 5 次做出最佳/可接受決策",
            icon = "✨",
            progress = { ratio(it.bestGoodStreak, 5) },
            isUnlocked = { it.bestGoodStreak >= 5 }
        ),
        Achievement(
            id = "streak_20",
            title = "GTO 模範生",
            description = "連續 20 次做出最佳/可接受決策",
            icon = "🌟",
            progress = { ratio(it.bestGoodStreak, 20) },
            isUnlocked = { it.bestGoodStreak >= 20 }
        ),
        Achievement(
            id = "optimal_50pct",
            title = "上路了",
            description = "最佳決策率達 50%（至少 30 次決策）",
            icon = "📈",
            progress = { s ->
                if (s.totalDecisions < 30) ratio(s.totalDecisions, 30) * 0.5f
                else (s.optimalRate.toFloat() / 0.5f).coerceAtMost(1f)
            },
            isUnlocked = { it.totalDecisions >= 30 && it.optimalRate >= 0.5 }
        ),
        Achievement(
            id = "optimal_70pct",
            title = "策略嫻熟",
            description = "最佳決策率達 70%（至少 100 次決策）",
            icon = "🎯",
            progress = { s ->
                if (s.totalDecisions < 100) ratio(s.totalDecisions, 100) * 0.5f
                else (s.optimalRate.toFloat() / 0.7f).coerceAtMost(1f)
            },
            isUnlocked = { it.totalDecisions >= 100 && it.optimalRate >= 0.7 }
        ),
        Achievement(
            id = "low_blunder",
            title = "穩如老狗",
            description = "失誤率低於 5%（至少 100 次決策）",
            icon = "🛡",
            progress = { s ->
                if (s.totalDecisions < 100) ratio(s.totalDecisions, 100)
                else if (s.blunderRate < 0.05) 1f
                else (0.05f / s.blunderRate.toFloat()).coerceAtMost(1f)
            },
            isUnlocked = { it.totalDecisions >= 100 && it.blunderRate < 0.05 }
        ),
        Achievement(
            id = "trainer_first",
            title = "練習生",
            description = "Trainer 答對第一題",
            icon = "📝",
            progress = { ratio(it.trainerCorrect, 1) },
            isUnlocked = { it.trainerCorrect >= 1 }
        ),
        Achievement(
            id = "trainer_streak_10",
            title = "題庫殺手",
            description = "Trainer 連續答對 10 題",
            icon = "⚡",
            progress = { ratio(it.trainerBestStreak, 10) },
            isUnlocked = { it.trainerBestStreak >= 10 }
        ),
        Achievement(
            id = "trainer_streak_25",
            title = "深諳此道",
            description = "Trainer 連續答對 25 題",
            icon = "🧠",
            progress = { ratio(it.trainerBestStreak, 25) },
            isUnlocked = { it.trainerBestStreak >= 25 }
        ),
        Achievement(
            id = "all_positions",
            title = "全位點滿",
            description = "六個位置都至少做出 5 次決策",
            icon = "🪑",
            progress = { s ->
                val touched = s.byPosition.values.count { it.decisions >= 5 }
                ratio(touched, 6)
            },
            isUnlocked = { s ->
                s.byPosition.values.count { it.decisions >= 5 } >= 6
            }
        )
    )

    fun byId(id: String): Achievement? = ALL.firstOrNull { it.id == id }
}
