# Phase 10 — Winner UX、AI 表情、Range 搜尋、成就、重播、HUD 偏好、卡住修正

> 提交範圍：`73cc921` → `41fcc3f`（`origin/main`）
> 日期：2026-05-17

---

## TL;DR

本回合完成 6 大主題，全部已編譯通過、單元測試 35/35 綠燈、Debug APK 可產出，並推送至 GitHub：

1. **贏家顯示修正**（不再出現「座位 0 贏」）
2. **AI 表情與動畫**（座位情緒、贏家光暈、攤牌亮牌）
3. **範圍視覺化重做**（手牌搜尋、點擊看 strategy/EV）
4. **成就系統**（15 個成就、自動解鎖、進度條）
5. **手歷重播**（逐步播放、自動播、控制條）
6. **HUD 顯示偏好**（5 個開關控制資訊密度）
7. **🐛 修正「發牌中...」永久卡住**（hand-end race condition）

---

## 1. 贏家顯示修正

### 問題
原本 `HandEnded` 事件只在 HUD/HandLog/HandHistory 中以「座位 0 贏」這種冷冰冰的數字顯示，使用者看不出自己（英雄）有沒有贏。

### 解法
- 新增 `Strings.winnersByName(seats, players, heroSeat)` 工具：英雄座位顯示「你」，其他顯示玩家名稱。
- `HandLog`、`StrategyHudPanel.HandEndCard`、`HandHistoryScreen.HandRow` 三處 signature 全改為接 `players` + `heroSeat`，內部 `nameOf()` 統一處理。
- 英雄贏時：HUD 卡片顯示「🎉 你贏」+ 金色背景；HandHistory 列顯示「🎉 你贏」標籤。
- 桌面上贏家座位：**金色邊框 + 👑 + 漸層光暈**（`SeatWinnerGold #FFD86B`、`SeatWinnerGlow #FFF1B3`）。

### 受影響檔
- `ui/theme/Strings.kt`、`ui/theme/Color.kt`
- `ui/hud/StrategyHudPanel.kt`、`ui/screen/GameScreen.kt`、`ui/screen/HandHistoryScreen.kt`

---

## 2. AI 表情與動畫

### 新增
- `SeatMood` enum：`NEUTRAL / THINKING / HAPPY / SAD / AGGRESSIVE / NERVOUS`，以 emoji 顯示於座位卡。
- `PlayerSeat.kt` 重寫：
  - `isWinner`：金邊 + 👑 + 漸層光暈 + 脈動動畫
  - `revealHoleCards`：攤牌時對未棄牌的對手自動翻牌
  - `mood`：根據最近一次行動 + status 計算情緒
  - 行動者（actor）pulse 動畫（infinite transition）
- `PokerTableLayout.SeatRing` 內部 `lastActionBySeat` 推導 → `computeMood()`。
- `GameScreen` 用 `remember(state.log)` 從最後 `HandEnded` 算出 `winningSeats` 傳給桌面。

### 關鍵決策
- Mood 是**純函式**從 state 推導（不存任何 AI 情緒持久狀態），UI 完全 stateless。

---

## 3. 範圍視覺化重做

### 新增
- `OutlinedTextField` 手牌搜尋：輸入 `AA` / `AKs` / `72o`（大寫、最多 4 字）。
- `parseHand()` parser：用 `Rank.fromChar` + `runCatching` 防護；無後綴時退回 offsuit。
- 點擊 13×13 範圍格 → 右側 `HandDetailCard` 顯示 `StratBar`（raise / call / check / fold % + combos 數）。
- 選中的格用 `HudGood` 綠邊高亮。

### 新 Strings
`RANGE_SEARCH_HINT / SELECTED / CLEAR / RAISE / CALL / FOLD / CHECK / COMBOS`

---

## 4. 成就系統

### 架構
- `core/achievements/AchievementRegistry.kt`：15 個成就，每個含 emoji、zh 標題/描述、`progress(stats): Float`、`isUnlocked(stats): Boolean`。
- `LearningStats` 擴充欄位：
  - `heroHandsWon`
  - `currentGoodStreak / bestGoodStreak`
  - `trainerCorrect / trainerCurrentStreak / trainerBestStreak`
  - `unlockedAchievements: Set<String>`
- `StatsRepository` 新增 `recordHeroResult(heroWon)`、`recordTrainerAnswer(correct)`，內部 `saveAndUnlock()` 每次寫入時自動掃描註冊表並 union 解鎖集合（不需 migration）。

### 已收錄成就
`first_hand`、`hands_10/100/500`、`first_win`、`wins_50`、`streak_5/20`、`optimal_50pct/70pct`、`low_blunder`、`trainer_first`、`trainer_streak_10/25`、`all_positions`

### UI
- `StatsScreen` 整頁改為可垂直捲動。
- 新 `AchievementsSection`：2 欄 grid（`chunked` rows）；解鎖 → 金色背景 + ✓；未解鎖 → 灰階圖示 + 進度條。

### 連動
- `GameViewModel.checkHandEnded`：`statsRepo.recordHeroResult(heroWon)`
- `TrainerScreen.gradeAnswer`：`statsRepo.recordTrainerAnswer(correct)`

---

## 5. 手歷重播

### 路由
- `Routes.REPLAY = "replay/{handIndex}"` + `replay(i)` helper
- `AppNavHost` 註冊 `REPLAY` composable（`NavType.IntType`）
- `HandHistoryScreen` row 改為 clickable（`pointerInput` + `detectTapGestures`），帶 `onReplay: (Int) -> Unit`

### `HandRecord` 擴充（backward compatible defaults）
新增欄位：`playerNames: Map<Int,String>`、`heroSeat`、`heroHole`、`flop`、`turn`、`river`。
舊 JSON 反序列化仍可運作（缺欄位走 default empty）。
`fromLog()` 新增 `players` + `heroSeat` 參數，由 `GameViewModel.checkHandEnded` 提供。

### `ReplayScreen.kt`（新檔）
- 逐步播放 `HandRecord.actions` 列表
- `LaunchedEffect` 自動播放（1.2 秒/步）
- 動態 board：掃描已過步驟中的「── 翻牌/轉牌/河牌」標記，從 `record.flop/turn/river` 累積
- **左側**：SummaryCard + BoardCard + HeroHoleCard
- **右側**：ControlBar（◀ ⏵/⏸ ▶ ↻ + `step/total`）+ EventList（當前步用 `HudAccent2` 高亮）

### 決策
不重跑 state machine，純文本 replay → 簡單、無需重建 deck/holes、新舊紀錄都能播。

---

## 6. HUD 顯示偏好

### 資料層
`SettingsRepository.Settings` 加 5 個 bool（預設全 true）：
- `hudShowGtoBars`
- `hudShowEvBreakdown`
- `hudShowAiInsight`
- `hudShowPostflopChecklist`
- `hudShowPotOdds`

對應 5 個 setter；DataStore Preferences keys 同步新增。

### UI 層
- `ui/hud/HudPreferences.kt`（新）：DTO + `DEFAULT` + `from(settings)` factory；隔離 HUD 與 DataStore。
- `StrategyHudPanel` 加 `prefs: HudPreferences` 參數，每個區塊用 `if (prefs.show*)` 包覆：
  - GTO StrategyBar
  - EvBreakdown
  - AiInsightCard
  - PostflopHeuristicHint
  - Pot odds 那一行
- `GameScreen` 透過 `vm.settings.collectAsState()` → `HudPreferences.from(settings)` → 傳入 panel。

### 設定頁
`SettingsScreen` 整頁改可捲動；新增「HUD 顯示」區段，5 個 `ToggleRow` 對應 5 個 setter。

---

## 7. 🐛「發牌中...」永久卡住修正（`41fcc3f`）

### 症狀
有時做完一個決策後畫面顯示「發牌中...」永遠不動，要重啟 App。

### 根因（race condition）
英雄做了結束本手的決策（例：raise 對手全部 fold；river call 進攤牌）時，順序如下：

1. `onHeroAction` → `applyAction(action)`
2. `applyAction` → `machine.apply` → `checkHandEnded`
3. `checkHandEnded` 偵測到 `HandEnded` 事件 → **正確地** `_pause.value = HandComplete`
4. 回到 `onHeroAction`，**下一行立刻** `_pause.value = HeroReview(...)` ← **覆蓋了 HandComplete**
5. UI 顯示 HeroReview 卡，使用者按「繼續」
6. `continueAfterReview` → `pause = None` → 啟動 `driveAi`
7. `driveAi` 看到 `actorSeat == null`（hand 已結） → return
8. 結果：`pause == None`、`actorSeat == null`、log 最後是 `HandEnded`
9. `GameScreen` 行動列 `else` 分支 → **顯示「發牌中...」** 永遠出不來

### 修正
**`onHeroAction`**：只有在 `_pause` 不是 `HandComplete` 才覆寫成 `HeroReview`。
```kotlin
applyAction(action)
if (_pause.value !is PauseState.HandComplete) {
    _pause.value = PauseState.HeroReview(action, rec, verdict)
}
```

**`driveAi`**：保險機制，若 `actorSeat == null` 且 log 最後是 `HandEnded` 但 pause 未同步，主動補上 `HandComplete`。
```kotlin
if (st.actorSeat == null) {
    val ended = st.log.lastOrNull() as? GameEvent.HandEnded
    if (ended != null && _pause.value !is PauseState.HandComplete) {
        _pause.value = PauseState.HandComplete(ended)
    }
    return
}
```

---

## 驗證
- ✅ `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL
- ✅ `./gradlew testDebugUnitTest` — 35/35 passed
- ✅ `./gradlew assembleDebug` — APK 產出
- ✅ `git push origin main` — 兩個 commit 已推送

## 本回合 Commits
- `adc8046` Phase 10: winner UX fix, seat moods, range search, achievements, replay, HUD prefs
- `41fcc3f` Fix '發牌中' freeze: don't overwrite HandComplete with HeroReview; driveAi safeguard

## 新增檔案
- `app/src/main/java/com/pokercoach/core/achievements/AchievementRegistry.kt`
- `app/src/main/java/com/pokercoach/ui/hud/HudPreferences.kt`
- `app/src/main/java/com/pokercoach/ui/screen/ReplayScreen.kt`
- `docs/PHASE10.md`（本檔）

---

## 之後可接著做（建議優先順序）

### A. 翻後系統強化
1. **翻後 GTO 範圍**：目前翻後只給啟發式提示（PostflopHeuristicHint）。可導入簡化 c-bet / check-raise 範圍表。
2. **Equity 計算器**：對任意 board 即時算英雄 vs 對手範圍的勝率。
3. **底牌類別分類**：top pair / overpair / draw / air 自動標註。

### B. 學習工具
4. **Trainer 進階模式**：翻後決策題、3-bet pot 題型。
5. **每日挑戰**：每天 10 題固定種子，記錄連勝。
6. **錯題本**：自動收錄 Bad/Blunder 決策，可重做。

### C. 統計與視覺化
7. **依位置統計圖表**：各位置勝率/EV 柱狀圖。
8. **手牌類別熱圖**：英雄拿到各手型的決策分佈與 EV。
9. **時段表現曲線**：近 50 / 100 / 500 手的 win rate 趨勢。

### D. 賽局深化
10. **賽事模式**：盲注升級、ICM 提示。
11. **多桌切換**：模擬同時兩桌。
12. **對手畫像**：依玩家風格（loose-aggressive 等）標記 + 對應建議。

### E. 體驗
13. **教學動畫**：首次進場引導加入動畫指引。
14. **手牌分享**：匯出單手為圖片 / 文字。
15. **語音播報**：可選的中文語音念出 GTO 建議。

> **下次接手提示**：所有 stats 連動 hooks 都已就位，新成就只要加進 `AchievementRegistry.ALL` 就會自動運作。HUD 新區塊要記得加進 `HudPreferences` 與 `SettingsScreen` 開關。
