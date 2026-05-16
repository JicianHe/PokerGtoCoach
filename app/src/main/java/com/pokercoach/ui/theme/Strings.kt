package com.pokercoach.ui.theme

/**
 * UI 字串集中管理（中文為主，英文專業術語保留）。
 *
 * 為什麼用 Kotlin object 而非 XML resources？
 *  - 含格式化參數的字串用 Kotlin 寫起來更安全（編譯期檢查）
 *  - 邏輯類字串（如 verdict 文案）需要根據資料動態組合
 *  - XML 仍保留 app_name 與選單標籤供系統使用
 *
 * 字串長度建議：HUD 卡片內每行 ≤ 22 個字元，避免換行錯亂。
 */
object Strings {

    // ===== 撲克動作 =====
    const val FOLD = "蓋牌"
    const val CHECK = "過牌"
    const val CALL = "跟注"
    const val RAISE = "加注"
    const val BET = "下注"
    const val ALL_IN = "全下"
    const val POT = "底池"
    const val BB_UNIT = "bb"

    // ===== 街 =====
    fun street(street: com.pokercoach.core.game.Street): String = when (street) {
        com.pokercoach.core.game.Street.PREFLOP -> "翻前"
        com.pokercoach.core.game.Street.FLOP -> "翻牌"
        com.pokercoach.core.game.Street.TURN -> "轉牌"
        com.pokercoach.core.game.Street.RIVER -> "河牌"
        com.pokercoach.core.game.Street.SHOWDOWN -> "攤牌"
    }

    // ===== 位置 =====
    fun position(p: com.pokercoach.core.model.Position): String = when (p) {
        com.pokercoach.core.model.Position.UTG -> "UTG"
        com.pokercoach.core.model.Position.HJ -> "HJ"
        com.pokercoach.core.model.Position.CO -> "CO"
        com.pokercoach.core.model.Position.BTN -> "BTN"
        com.pokercoach.core.model.Position.SB -> "SB"
        com.pokercoach.core.model.Position.BB -> "BB"
    }

    fun positionFull(p: com.pokercoach.core.model.Position): String = when (p) {
        com.pokercoach.core.model.Position.UTG -> "UTG 前位"
        com.pokercoach.core.model.Position.HJ -> "HJ 中位"
        com.pokercoach.core.model.Position.CO -> "CO 劫位"
        com.pokercoach.core.model.Position.BTN -> "BTN 按鈕"
        com.pokercoach.core.model.Position.SB -> "SB 小盲"
        com.pokercoach.core.model.Position.BB -> "BB 大盲"
    }

    // ===== HUD 標題 =====
    const val HUD_TITLE = "策略教練"
    const val HUD_BEFORE = "行動前分析"
    const val HUD_REVIEW = "GTO 點評"
    const val HUD_WATCHING = "觀察中"
    const val HUD_HAND_DONE = "本手結束"
    const val HUD_GTO_STRATEGY = "GTO 策略分佈"
    const val HUD_EV_BREAKDOWN = "各行動 EV（bb）"
    const val HUD_TO_CALL = "需跟注"
    const val HUD_POT_ODDS = "底池賠率"
    const val HUD_RANGE_SHARE = "在範圍佔比"
    const val HUD_BASELINE = "GTO 基準"
    const val HUD_ADJUSTED = "剝削調整後"
    const val HUD_OPPONENT = "對手推理"
    const val HUD_YOUR_CHOICE = "你選擇了"
    const val HUD_CONTINUE = "繼續 ›"
    const val HUD_NEXT_HAND = "下一手 ›"
    const val HUD_MIXED_BADGE = "⚖ 混合決策 — 此策略內任何選擇都符合 GTO"
    const val HUD_POSTFLOP_CHECKLIST = "翻後決策清單"

    // ===== 評價 =====
    const val V_OPTIMAL = "✓ 最佳決策"
    const val V_ACCEPTABLE = "≈ 可接受的混合"
    const val V_SUBOPTIMAL = "△ 次佳選擇"
    const val V_BLUNDER = "✗ 重大失誤"
    const val V_UNKNOWN = "— 無基準資料"

    // ===== 牌局事件 =====
    /** 以座位 index 顯示（fallback）。優先使用 winnersByName。 */
    fun winners(seats: List<Int>): String =
        seats.joinToString("、") { "座位 $it" }

    /**
     * 用玩家名稱列出贏家；英雄 (heroSeat) 顯示為「你」。
     *
     * @param seats 贏家座位
     * @param players 全桌玩家（用來查名稱）
     * @param heroSeat 英雄座位 index（通常 0）
     */
    fun winnersByName(
        seats: List<Int>,
        players: List<com.pokercoach.core.game.Player>,
        heroSeat: Int
    ): String = seats.joinToString("、") { seat ->
        if (seat == heroSeat) NAME_YOU
        else players.firstOrNull { it.seatIndex == seat }?.name ?: "座位 $seat"
    }

    const val REASON_FOLD = "其他玩家全部蓋牌"
    const val REASON_SHOWDOWN = "攤牌比牌"

    // ===== 狀態 =====
    const val STATUS_FOLDED = "已蓋牌"
    const val STATUS_ALL_IN = "全下"
    const val NAME_YOU = "你"

    // ===== 教學提示 =====
    val TIP_POT_ODDS = "底池賠率 = 需跟注 ÷ (底池 + 需跟注)。當勝率高於底池賠率時，跟注有 EV。"
    val TIP_POSITION = "位置越後資訊越多，可玩範圍越寬。BTN 是位置最好的座位。"
    val TIP_MIXED = "GTO 在某些手牌會推薦混合策略，目的是讓對手無法剝削你的可預測性。"
    val TIP_3BET_BLUFF = "弱同花 A（如 A5s、A4s）常作 3-bet bluff：阻擋對手強 A 並有翻後可玩性。"
    val TIP_SPR = "SPR（堆疊底池比）越低，越偏向 commit；SPR > 10 時，需謹慎評估翻後線。"
    val TIP_BOARD_TEXTURE = "乾燥板（彩虹、不連張）有利於範圍持有人；潮濕板（同花/順子可能）有利於範圍受方。"

    // ===== 訓練模式 =====
    const val TRAIN_TITLE = "情境訓練"
    const val TRAIN_CORRECT = "正確！"
    const val TRAIN_WRONG = "不太對哦"
    const val TRAIN_NEXT = "下一題 ›"
    const val TRAIN_SCORE = "得分"
    const val TRAIN_STREAK = "連勝"
    const val TRAIN_ACCURACY = "準確率"

    // ===== Range Viewer =====
    const val RANGE_TITLE = "範圍查看"
    const val RANGE_OVERALL_FREQ = "整體頻率"
    const val RANGE_SELECT = "選擇情境"
    const val RANGE_SEARCH_HINT = "輸入手牌，如 AKs / QQ / 72o"
    const val RANGE_SELECTED = "已選"
    const val RANGE_CLEAR = "清除"
    const val RANGE_RAISE = "加注"
    const val RANGE_CALL = "跟注"
    const val RANGE_FOLD = "蓋牌"
    const val RANGE_CHECK = "過牌"
    const val RANGE_COMBOS = "組合數"

    // ===== 統計 =====
    const val STATS_TITLE = "學習統計"
    const val STATS_HANDS = "已玩手數"
    const val STATS_OPTIMAL_RATE = "最佳率"
    const val STATS_BLUNDER_RATE = "失誤率"
    const val STATS_BY_POSITION = "依位置"
    const val STATS_NO_DATA = "尚無資料，多打幾手吧～"

    // ===== 主選單 =====
    const val MENU_FREEPLAY = "自由對戰"
    const val MENU_TRAINER = "情境訓練"
    const val MENU_RANGES = "範圍查看"
    const val MENU_STATS = "學習統計"
    const val MENU_SETTINGS = "設定"
    const val MENU_SUBTITLE = "讓 GTO 成為你的撲克本能"
    const val MENU_HISTORY = "手牌歷史"

    // ===== 應用 =====
    const val APP_NAME = "撲克 GTO 教練"
    const val BACK = "返回"

    // ===== 開場教學 =====
    const val ONBOARDING_TITLE = "歡迎來到 GTO 教練"
    const val ONBOARDING_SKIP = "略過"
    const val ONBOARDING_NEXT = "下一步 ›"
    const val ONBOARDING_DONE = "開始學習"

    data class OnboardingPage(val title: String, val body: String, val emoji: String)

    val ONBOARDING_PAGES: List<OnboardingPage> = listOf(
        OnboardingPage(
            emoji = "✈",
            title = "什麼是 GTO？",
            body = "Game Theory Optimal（賽局理論最優）——一套讓對手無法剝削你的均衡策略。" +
                    "這款教練會在每個決策點顯示 GTO 推薦頻率與 EV，幫你內化撲克思考方式。"
        ),
        OnboardingPage(
            emoji = "❤",
            title = "自由對戰 + 即時點評",
            body = "與五位 AI 對手（含 GTO 基準、TAG、LAG、Nit、跟注站）對戰。" +
                    "每次輪到你行動，HUD 會顯示 GTO 策略分佈、EV、底池賠率。" +
                    "你出手後立即給出評價（最佳／可接受／次佳／失誤）。"
        ),
        OnboardingPage(
            emoji = "★",
            title = "情境訓練 — 翻前 + 翻後",
            body = "翻前隨機題：169 種起手牌 × 多種情境，重複練到形成肌肉記憶。" +
                    "翻後 30+ 道精選題：頂對、暗三條、同花 draw、bluff catcher 等核心場景。"
        ),
        OnboardingPage(
            emoji = "☁",
            title = "範圍視覺化 + 學習統計",
            body = "13×13 範圍圖：直觀看每種起手牌的 GTO 加注/跟注/蓋牌頻率。" +
                    "統計頁追蹤你的最佳率、失誤率、依位置別表現，幫你找出弱項。"
        )
    )

    // ===== 設定 =====
    const val SETTINGS_SOUND = "音效"
    const val SETTINGS_HAPTIC = "震動回饋"
    const val SETTINGS_ANIMATIONS = "動畫"
    const val SETTINGS_AI_DIFFICULTY = "AI 難度"
    const val SETTINGS_DIFF_GTO = "純 GTO（最強）"
    const val SETTINGS_DIFF_MIXED = "混合畫像（推薦）"
    const val SETTINGS_DIFF_FISH = "魚池模式（輕鬆）"
    const val SETTINGS_RESET = "重置統計資料"
    const val SETTINGS_RESET_CONFIRM = "確定要清除所有學習統計嗎？此動作無法復原。"
    const val CONFIRM = "確認"
    const val CANCEL = "取消"

    // ===== 範圍圖例 =====
    const val LEGEND_RAISE = "加注"
    const val LEGEND_CALL = "跟注"
    const val LEGEND_MIXED = "混合"
    const val LEGEND_FOLD = "蓋牌"

    // ===== AI 推理片段（中文）=====
    fun aiPreflopRationale(
        position: com.pokercoach.core.model.Position,
        hand: com.pokercoach.core.model.HandClass,
        scenario: String?,
        adjustedSummary: String,
        action: com.pokercoach.core.model.Action
    ): String = buildString {
        append("【翻前】${position(position)} 持 $hand：")
        if (scenario != null) append("情境=$scenario，")
        append("策略 $adjustedSummary → 選擇 ${actionLabel(action)}")
    }

    fun aiPostflopRationale(
        street: com.pokercoach.core.game.Street,
        strength: Double,
        toCall: Double,
        potOdds: Double,
        summary: String,
        action: com.pokercoach.core.model.Action
    ): String = buildString {
        append("【${street(street)}】")
        append("牌力=${"%.0f".format(strength * 100)}% ")
        append("跟注=${"%.1f".format(toCall)}bb ")
        append("底池賠率=${"%.0f".format(potOdds * 100)}% → ")
        append("$summary → ${actionLabel(action)}")
    }

    fun actionLabel(a: com.pokercoach.core.model.Action): String = when (a) {
        is com.pokercoach.core.model.Action.Fold -> FOLD
        is com.pokercoach.core.model.Action.Check -> CHECK
        is com.pokercoach.core.model.Action.Call -> CALL
        is com.pokercoach.core.model.Action.Raise -> "$RAISE ${"%.1f".format(a.amount)}bb"
    }

    fun actionLabelByKind(k: com.pokercoach.core.model.Action.Kind): String = when (k) {
        com.pokercoach.core.model.Action.Kind.FOLD -> FOLD
        com.pokercoach.core.model.Action.Kind.CHECK -> CHECK
        com.pokercoach.core.model.Action.Kind.CALL -> CALL
        com.pokercoach.core.model.Action.Kind.RAISE -> RAISE
    }

    // ===== 對手畫像名稱 =====
    fun profileName(profile: com.pokercoach.core.ai.PsychProfile): String = when (profile.name) {
        "GTO Baseline" -> "GTO 基準"
        "Tight-Aggressive" -> "緊兇 TAG"
        "Loose-Aggressive" -> "鬆兇 LAG"
        "Nit" -> "石頭 Nit"
        "Calling Station" -> "跟注站"
        else -> profile.name
    }

    // ===== HUD 點評文案 =====
    /** 把混合策略寫成 "加注 60% / 跟注 40%" 這種摘要。 */
    fun explainStrategySummary(rec: com.pokercoach.core.ev.EvCalculator.Recommendation): String {
        val s = rec.strategy
        val parts = buildList {
            if (s.raise > 0) add("$RAISE ${(s.raise * 100).toInt()}%")
            if (s.call  > 0) add("$CALL ${(s.call * 100).toInt()}%")
            if (s.check > 0) add("$CHECK ${(s.check * 100).toInt()}%")
            if (s.fold  > 0) add("$FOLD ${(s.fold * 100).toInt()}%")
        }
        return parts.joinToString(" / ")
    }

    fun verdictExplain(
        verdict: VerdictLevel,
        chosen: com.pokercoach.core.model.Action.Kind,
        rec: com.pokercoach.core.ev.EvCalculator.Recommendation
    ): String {        val pct = (rec.strategy.frequencyOf(chosen) * 100).toInt()
        val dom = actionLabelByKind(rec.recommendedAction)
        val ch = actionLabelByKind(chosen)
        return when (verdict) {
            VerdictLevel.Optimal ->
                "穩。GTO 在此情境用 $ch 的頻率達 ${pct}%（${rec.hand}）。"
            VerdictLevel.Acceptable ->
                "可接受的混合選擇。GTO 選 $ch 約 ${pct}%，主流線是 $dom。"
            VerdictLevel.Suboptimal ->
                "偏離主流。GTO 鮮少選 $ch（僅 ${pct}%）。預設應選 $dom。"
            VerdictLevel.Blunder ->
                "明顯漏洞。GTO 幾乎不選 $ch，${rec.hand} 在此情境應 $dom。"
            VerdictLevel.Unknown ->
                "缺乏求解器基準，請憑撲克基本面自行檢視。"
        }
    }
}

enum class VerdictLevel { Optimal, Acceptable, Suboptimal, Blunder, Unknown }
