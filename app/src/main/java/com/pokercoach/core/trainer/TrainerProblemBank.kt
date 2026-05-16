package com.pokercoach.core.trainer

import com.pokercoach.core.ev.EvCalculator
import com.pokercoach.core.model.Action
import com.pokercoach.core.model.HandClass
import com.pokercoach.core.model.Position
import com.pokercoach.core.range.MixedStrategy
import com.pokercoach.core.range.PreflopRangeManager
import com.pokercoach.core.range.RangeScenario
import kotlin.random.Random

/**
 * 訓練模式題目來源。
 *
 * 兩大來源：
 *   1) 從 PreflopRangeManager 隨機抽情境 + HandClass 出題（無限題庫）
 *   2) 手寫翻後經典場景（C-Bet / 3-bet pot / 防守等，30 題）
 */
object TrainerProblemBank {

    data class PreflopProblem(
        val scenario: RangeScenario,
        val hand: HandClass,
        val recommendation: EvCalculator.Recommendation
    ) {
        val correctActions: Set<Action.Kind> = run {
            val s = recommendation.strategy
            buildSet {
                if (s.raise >= 0.20) add(Action.Kind.RAISE)
                if (s.call  >= 0.20) add(Action.Kind.CALL)
                if (s.check >= 0.20) add(Action.Kind.CHECK)
                if (s.fold  >= 0.20) add(Action.Kind.FOLD)
                // 確保至少一個答案（純策略也涵蓋）
                if (isEmpty()) add(recommendation.recommendedAction)
            }
        }
    }

    fun randomPreflopProblem(random: Random = Random.Default): PreflopProblem {
        val scenarios = PreflopRangeManager.supportedScenarios()
        // 過濾掉純 fold 太多的格子，避免出太多無趣的題
        repeat(20) {  // 最多嘗試 20 次
            val sc = scenarios.random(random)
            val hands = com.pokercoach.core.model.HandClass.all169()
            val hc = hands.random(random)
            val rec = EvCalculator.recommend(sc, hc)
            // 排除 100% fold 的格（沒教學價值）
            if (rec.strategy.fold < 0.95) {
                return PreflopProblem(sc, hc, rec)
            }
        }
        // 退路：硬出
        val sc = scenarios.random(random)
        val hc = com.pokercoach.core.model.HandClass.parse("AKo")
        return PreflopProblem(sc, hc, EvCalculator.recommend(sc, hc))
    }

    // =====================================================================
    // 翻後手寫題庫
    // =====================================================================
    data class PostflopProblem(
        val title: String,
        val scenario: String,
        val heroPosition: Position,
        val heroHand: String,        // e.g. "Ah Kh"
        val board: String,           // e.g. "Kd 7s 2c"
        val potBb: Double,
        val toCallBb: Double,
        val correctAction: Action.Kind,
        val explanation: String
    )

    val POSTFLOP_PROBLEMS: List<PostflopProblem> = listOf(
        PostflopProblem(
            title = "翻牌頂對 vs 跟注",
            scenario = "你在 BTN 開池，BB 跟注。翻牌乾燥彩虹，BB 過牌。",
            heroPosition = Position.BTN,
            heroHand = "Ah Kh", board = "Kd 7s 2c",
            potBb = 5.5, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "頂對頂 kicker 在乾燥彩虹板，建議 33% 小注下注以最大化價值，BB 範圍多為弱對 / 草花 draws。"
        ),
        PostflopProblem(
            title = "翻牌中對 vs c-bet",
            scenario = "BB 防守 BTN open，翻牌 BTN 過半 pot c-bet。",
            heroPosition = Position.BB,
            heroHand = "Tc 9c", board = "Td 5h 2s",
            potBb = 8.0, toCallBb = 4.0,
            correctAction = Action.Kind.CALL,
            explanation = "中對在乾燥板有充足勝率（>40%），對 1/2 pot 賠率（33% equity needed）為跟注。"
        ),
        PostflopProblem(
            title = "同花 draw 面對下注",
            scenario = "BB 防守，翻牌兩同花，BTN 半池下注。",
            heroPosition = Position.BB,
            heroHand = "Qh Jh", board = "9h 6h 2c",
            potBb = 6.5, toCallBb = 3.25,
            correctAction = Action.Kind.CALL,
            explanation = "同花 draw（9 outs ≈ 35% 兩街）加 overcards，賠率夠跟注；GTO 也常 raise 為半詐唬。"
        ),
        PostflopProblem(
            title = "空氣 vs c-bet",
            scenario = "BB 防守 BTN open，翻牌不擊中，BTN 半池下注。",
            heroPosition = Position.BB,
            heroHand = "7c 4d", board = "Ah Td 2s",
            potBb = 6.5, toCallBb = 3.25,
            correctAction = Action.Kind.FOLD,
            explanation = "毫無連結（無對、無 draw、無 backdoor），ace-high 板對 BTN 範圍極不利，蓋牌。"
        ),
        PostflopProblem(
            title = "暗三條慢打",
            scenario = "你 UTG open 88，BB 跟注。翻牌 8 高乾燥彩虹，BB 過牌。",
            heroPosition = Position.UTG,
            heroHand = "8c 8d", board = "8s 4h 2c",
            potBb = 6.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "強牌在範圍劣勢板做 range check 隱藏範圍上限，更能誘騙轉牌詐唬。"
        ),
        PostflopProblem(
            title = "頂兩對在濕板",
            scenario = "你 CO open，BTN cold call，翻牌兩同花。",
            heroPosition = Position.CO,
            heroHand = "Ad Kc", board = "Kh Qh 7d",
            potBb = 7.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "頂對頂 kicker 在濕板，必須以 66%+ pot bet 保護，避免被 draw 免費看牌。"
        ),
        PostflopProblem(
            title = "Overpair vs 3-bet pot",
            scenario = "你 UTG open，CO 3-bet，你跟注。翻牌低乾。",
            heroPosition = Position.UTG,
            heroHand = "Qh Qd", board = "8c 5d 2s",
            potBb = 18.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "3-bet 底池 OOP，乾低板 PFR 為 CO；GTO 建議大量過牌 / call down，避免把 QQ 變詐唬。"
        ),
        PostflopProblem(
            title = "Bluff catcher 河牌",
            scenario = "Heads-up 河牌，對手大注下注。",
            heroPosition = Position.BB,
            heroHand = "Ac 7c", board = "Kh 9d 5s 3h 2c",
            potBb = 30.0, toCallBb = 20.0,
            correctAction = Action.Kind.FOLD,
            explanation = "A-high 在大注河牌幾乎無 showdown value，對手 polarized 範圍中價值佔多，蓋牌。"
        ),
        PostflopProblem(
            title = "Set 在順子板",
            scenario = "你 BB 防守，翻牌中 set 但板面有順子。",
            heroPosition = Position.BB,
            heroHand = "5c 5d", board = "8d 7h 5s",
            potBb = 6.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Set 在連張板必須 donk / check-raise 保護，避免讓對手免費追順子。"
        ),
        PostflopProblem(
            title = "Open-ended straight draw",
            scenario = "BTN open，你 BB 跟注，翻牌中 OESD。",
            heroPosition = Position.BB,
            heroHand = "9h 8h", board = "7c 6d 2s",
            potBb = 6.5, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "OESD 有 ~31% equity 看下一張，過牌建構誘對手 c-bet 後 raise / call。"
        ),
        PostflopProblem(
            title = "轉牌完成同花 draw",
            scenario = "翻牌跟注同花 draw，轉牌補同花後對手繼續下注。",
            heroPosition = Position.BB,
            heroHand = "Qh Jh", board = "9h 6h 2c 4h",
            potBb = 14.0, toCallBb = 7.0,
            correctAction = Action.Kind.RAISE,
            explanation = "中堅同花在轉牌建議加注以取得價值並折疊對手部分強對 / 兩對。"
        ),
        PostflopProblem(
            title = "Donk lead 反應",
            scenario = "你 BTN open，BB 跟注。翻牌 BB 不依慣例下注 1/3 pot。",
            heroPosition = Position.BTN,
            heroHand = "As Kh", board = "Td 9c 8h",
            potBb = 6.0, toCallBb = 2.0,
            correctAction = Action.Kind.CALL,
            explanation = "AK 雙 over + gutshot，乾脆跟注；加注容易被 BB 的兩對 / set 重置。"
        ),
        PostflopProblem(
            title = "Cooler 應對",
            scenario = "翻牌中 set 但對手 raise 你的 c-bet。",
            heroPosition = Position.CO,
            heroHand = "9c 9d", board = "9s 8h 7d",
            potBb = 20.0, toCallBb = 12.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Set 不怕 cooler，3-bet shove 防止對手免費補順子 / 同花。"
        ),
        PostflopProblem(
            title = "薄價值河牌",
            scenario = "河牌你持兩對，對手過牌。",
            heroPosition = Position.BTN,
            heroHand = "Ah Td", board = "Kc Ts 4h 7s 2c",
            potBb = 12.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "兩對 1/3 pot 薄價值下注，KX / 弱對會跟注。"
        ),
        PostflopProblem(
            title = "Multiway 翻牌",
            scenario = "三人看翻牌，你持頂對 weak kicker。",
            heroPosition = Position.BTN,
            heroHand = "Kh 8d", board = "Ks 9c 7h",
            potBb = 12.0, toCallBb = 4.0,
            correctAction = Action.Kind.FOLD,
            explanation = "Multiway 中頂對弱 kicker 易被支配；面對下注 + 還有一人未動作，蓋牌。"
        ),
        PostflopProblem(
            title = "Flush draw 主動建構",
            scenario = "你 CO open，BB 跟注。翻牌兩同花，BB 過牌。",
            heroPosition = Position.CO,
            heroHand = "Ah 6h", board = "Th 8h 3c",
            potBb = 7.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Nut flush draw + overcard = 半詐唬最佳候選，66% pot 同時取折疊權與 implied odds。"
        ),
        PostflopProblem(
            title = "三條 vs 河牌 raise",
            scenario = "你河牌 set，對手 raise 你的 value bet。",
            heroPosition = Position.BTN,
            heroHand = "7c 7d", board = "Kh 7s 4d 2c 9h",
            potBb = 40.0, toCallBb = 30.0,
            correctAction = Action.Kind.CALL,
            explanation = "Set 在無同花 / 順子板對 raise 為強跟注牌；不 reraise 避免暴露範圍。"
        ),
        PostflopProblem(
            title = "暗三條 vs c-bet",
            scenario = "你 BB 防守 BTN open，翻牌中暗三條。",
            heroPosition = Position.BB,
            heroHand = "3h 3c", board = "9d 3s 2h",
            potBb = 6.5, toCallBb = 3.0,
            correctAction = Action.Kind.RAISE,
            explanation = "OOP set 必須 check-raise 建構 pot；slow play 浪費對手 over pair / pair+draw 的價值線。"
        ),
        PostflopProblem(
            title = "Air ball over pair vs raise",
            scenario = "你 CO open AKo，BTN 跟注。翻牌 c-bet 被 raise。",
            heroPosition = Position.CO,
            heroHand = "As Kd", board = "9h 6c 2s",
            potBb = 18.0, toCallBb = 10.0,
            correctAction = Action.Kind.FOLD,
            explanation = "翻牌完全 miss，面對 raise 範圍偏 value（top pair+, sets）。AK overs 蓋牌即可。"
        ),
        PostflopProblem(
            title = "Combo draw 全下價值",
            scenario = "你翻牌 OESD + flush draw，對手全下。",
            heroPosition = Position.BB,
            heroHand = "Th 9h", board = "Qh 8h 5s",
            potBb = 35.0, toCallBb = 25.0,
            correctAction = Action.Kind.CALL,
            explanation = "15 outs 約 54% equity vs over pair，盃中籌碼必須跟注。"
        ),
        PostflopProblem(
            title = "Range bet 機會",
            scenario = "你 BTN open，BB 跟注。翻牌乾燥 A 高板，BB 過牌。",
            heroPosition = Position.BTN,
            heroHand = "5h 5c", board = "As 7d 3c",
            potBb = 5.5, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "A 高乾板 BTN 範圍優勢明顯，小 c-bet 33% pot 全範圍下注，55 也跟著。"
        ),
        PostflopProblem(
            title = "Polarized 河牌賭桿",
            scenario = "你河牌 nuts，對手過牌。",
            heroPosition = Position.BTN,
            heroHand = "8s 7s", board = "Tc 9d 6c 3h Kd",
            potBb = 24.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Nuts 純 polarized 下大注（pot+），對手 KX top pair 會跟付。"
        ),
        PostflopProblem(
            title = "Probe 轉牌",
            scenario = "翻牌兩人都過，轉牌出現連張。",
            heroPosition = Position.BB,
            heroHand = "8h 7h", board = "Kd 2c 5s 6h",
            potBb = 5.5, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "轉牌 OESD + backdoor flush，主動 probe 1/2 pot 取主動權。"
        ),
        PostflopProblem(
            title = "防守對手 over bet",
            scenario = "河牌對手 over bet 1.5x pot，你有 second pair。",
            heroPosition = Position.BB,
            heroHand = "Qd Tc", board = "Ks Th 4d 2s 7c",
            potBb = 12.0, toCallBb = 18.0,
            correctAction = Action.Kind.FOLD,
            explanation = "對手 over bet 為 polarized 範圍（nuts + bluffs），second pair 太弱無法 bluff catch。"
        ),
        PostflopProblem(
            title = "防守對手小 bet",
            scenario = "河牌對手 1/4 pot 下注，你有 weak top pair。",
            heroPosition = Position.BB,
            heroHand = "Kc 6d", board = "Ks 7d 4h 2c 9s",
            potBb = 12.0, toCallBb = 3.0,
            correctAction = Action.Kind.CALL,
            explanation = "小 bet 賠率好（20% equity needed），weak top pair 跟注。"
        ),
        PostflopProblem(
            title = "Squeeze 後 c-bet",
            scenario = "翻前你 squeeze BTN open，opener 跟注。翻牌 A 高乾燥。",
            heroPosition = Position.BB,
            heroHand = "Ac Kd", board = "Ad 7h 2c",
            potBb = 22.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "AK on Axx 乾板，作為 PFR 大幅 range 優勢，1/3 pot c-bet 取最大 EV。"
        ),
        PostflopProblem(
            title = "OESD vs check-raise",
            scenario = "你翻牌 c-bet OESD 被 BB check-raise。",
            heroPosition = Position.BTN,
            heroHand = "Js Th", board = "9c 8d 2s",
            potBb = 14.0, toCallBb = 8.0,
            correctAction = Action.Kind.CALL,
            explanation = "OESD 8 outs ~32% + 對手 check-raise 範圍含 bluffs，跟注看轉牌。"
        ),
        PostflopProblem(
            title = "Set 在 monotone 板",
            scenario = "翻牌單花色板，你有 set 但無同花 draw。",
            heroPosition = Position.CO,
            heroHand = "9h 9c", board = "9s 7s 4s",
            potBb = 8.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Set on monotone 仍是極強牌，必須 1/2 pot bet 保護；緩打讓對手免費補同花。"
        ),
        PostflopProblem(
            title = "Top pair 在 4-bet pot",
            scenario = "翻前對手 4-bet 你 call。翻牌中 K 高。",
            heroPosition = Position.BTN,
            heroHand = "Kh Qd", board = "Kc 7h 3s",
            potBb = 40.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "4-bet 底池對手範圍含 AA / KK / AK，主動下注被 raise 進退失據；過牌讓對手 c-bet 後再應對。"
        ),
        PostflopProblem(
            title = "Bluff raise 候選",
            scenario = "翻牌對手 c-bet，你有空氣但 backdoor 雙 draw。",
            heroPosition = Position.BB,
            heroHand = "5h 4h", board = "Kh 8c 3d",
            potBb = 6.5, toCallBb = 3.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Backdoor flush + straight draw 是優質 bluff raise 候選，藍圖 equity 加上折疊權。"
        ),
        // ==== 補充題 31-60：涵蓋更多翻後場景 ====
        PostflopProblem(
            title = "Turn 第二街二次 barrel",
            scenario = "你翻牌 c-bet 被 call，轉牌補出 draw card。",
            heroPosition = Position.BTN,
            heroHand = "Ah Qh", board = "Qd 7c 3s 5h",
            potBb = 12.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Top pair top kicker 在不改變範圍對比的轉牌，價值下注以對抗對手中對與 draws。"
        ),
        PostflopProblem(
            title = "面對 turn check-raise",
            scenario = "你 turn barrel，對手 check-raise。",
            heroPosition = Position.CO,
            heroHand = "Ah Kc", board = "Kd 8h 4c 2d",
            potBb = 18.0, toCallBb = 12.0,
            correctAction = Action.Kind.CALL,
            explanation = "Top pair top kicker 雖被 check-raise，但無 draw 結構板對手範圍含 bluff，跟注看河牌。"
        ),
        PostflopProblem(
            title = "河牌 thin value",
            scenario = "河牌乾燥未變，你有中對。",
            heroPosition = Position.BTN,
            heroHand = "Jh Tc", board = "Jd 8s 3c 2h 5d",
            potBb = 14.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "中對 top kicker 對 BB call 範圍中的弱對與 underpair 可薄價值 1/3 pot。"
        ),
        PostflopProblem(
            title = "河牌過牌讓對手詐唬",
            scenario = "河牌補同花，你有 overpair 無同花。",
            heroPosition = Position.UTG,
            heroHand = "Qh Qd", board = "9h 7h 4c 2s Ah",
            potBb = 20.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "雙重危險牌（A + flush）, QQ 變 bluff catcher，過牌讓對手錯誤詐唬。"
        ),
        PostflopProblem(
            title = "Double barrel turn 補出 A",
            scenario = "你翻牌 c-bet 被 call，轉牌補 A。",
            heroPosition = Position.CO,
            heroHand = "Ad Kd", board = "Tc 7h 3c Ah",
            potBb = 11.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Range advantage card（A 出現有利 PFR 範圍），continued barrel 賺價值並 fold equity。"
        ),
        PostflopProblem(
            title = "Set on draw-heavy turn",
            scenario = "翻牌 set，轉牌補同花 draw。",
            heroPosition = Position.HJ,
            heroHand = "7c 7d", board = "Th 7s 4c Kh",
            potBb = 10.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Set 在濕板必須持續 sizing 來收 draw 價值並保護，2/3 pot+。"
        ),
        PostflopProblem(
            title = "弱頂對 vs OOP donk bet",
            scenario = "BB 在翻牌主動下注（donk）。",
            heroPosition = Position.BTN,
            heroHand = "Kh 9c", board = "9d 7s 5h",
            potBb = 6.5, toCallBb = 3.0,
            correctAction = Action.Kind.CALL,
            explanation = "弱頂對對 donk 範圍有充足勝率，但 raise 把對手弱對逐出反而虧損；call 控池。"
        ),
        PostflopProblem(
            title = "Overcards 河牌 hero call",
            scenario = "河牌對手大注，你只有兩張 overcards。",
            heroPosition = Position.BB,
            heroHand = "Ad Kc", board = "9h 8c 3d 2s 6h",
            potBb = 22.0, toCallBb = 18.0,
            correctAction = Action.Kind.FOLD,
            explanation = "Ace-high 在面對河牌大注（pot odds 需要 ~45%）幾乎不可能贏，乾淨 fold。"
        ),
        PostflopProblem(
            title = "Wet flop heads-up c-bet 選擇",
            scenario = "BB call 你的 open，翻牌兩同花+順子可能。",
            heroPosition = Position.CO,
            heroHand = "Ad Ah", board = "Th 9h 8c",
            potBb = 7.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Overpair 在濕板必須以 75-100% pot bet 保護，避免被 draws 免費補。"
        ),
        PostflopProblem(
            title = "Slowplay 翻牌堅果順",
            scenario = "你 BB defend，翻牌打出順子。",
            heroPosition = Position.BB,
            heroHand = "Jh Tc", board = "Qh 9s 8d",
            potBb = 5.5, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "OOP 堅果順 check-raise 給對手 c-bet 機會，建立翻牌 raise 範圍含 set+順。"
        ),
        PostflopProblem(
            title = "Flush draw + overpair",
            scenario = "你 IP raise 翻前，翻牌補同花 draw 還有 overpair。",
            heroPosition = Position.BTN,
            heroHand = "Kh Kc", board = "Th 7h 2c",
            potBb = 7.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Overpair + backdoor draws 在濕板 bet 70% pot 保護並收 draws 價值。"
        ),
        PostflopProblem(
            title = "Bottom pair multiway",
            scenario = "翻牌三人看牌，你打底對。",
            heroPosition = Position.BTN,
            heroHand = "8c 7c", board = "Kd 9h 7s",
            potBb = 9.0, toCallBb = 4.5,
            correctAction = Action.Kind.FOLD,
            explanation = "Multiway 底對勝率約 25%，無 draw 結構；面對 1/2 pot 需 33% equity，乾淨蓋。"
        ),
        PostflopProblem(
            title = "Gutshot + overcard",
            scenario = "翻牌補 gutshot + overcard。",
            heroPosition = Position.BB,
            heroHand = "Ah 8h", board = "9c 7s 4d",
            potBb = 7.5, toCallBb = 3.0,
            correctAction = Action.Kind.CALL,
            explanation = "Gutshot 4 outs + ace high overcards ≈ 18% equity；pot odds 需 28%，邊際但 IP 控池可跟。"
        ),
        PostflopProblem(
            title = "C-bet 範圍下注小尺度",
            scenario = "BTN open BB call，翻牌 K 高乾燥。",
            heroPosition = Position.BTN,
            heroHand = "5d 5c", board = "Kh 8d 3c",
            potBb = 5.5, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "K 高板有極大範圍優勢，PFR 用 25-33% pot bet 範圍下注賺薄價值。"
        ),
        PostflopProblem(
            title = "面對 polar 河牌下注",
            scenario = "河牌補 backdoor flush，對手下大注（polar）。",
            heroPosition = Position.BTN,
            heroHand = "Ah As", board = "Kd 8c 3h 5d 2c",
            potBb = 30.0, toCallBb = 25.0,
            correctAction = Action.Kind.CALL,
            explanation = "AA 是強 bluff catcher；對手 polar 下注（價值/詐唬），AA 勝過所有純詐唬。"
        ),
        PostflopProblem(
            title = "Turn semibluff raise",
            scenario = "你 BB defend，turn 補 OESD + flush draw。",
            heroPosition = Position.BB,
            heroHand = "9h 8h", board = "7s 6h 2c Kh",
            potBb = 8.0, toCallBb = 6.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Combo draw (15+ outs ~55%) 加 fold equity，turn check-raise 是高 EV 線。"
        ),
        PostflopProblem(
            title = "River value bet sizing",
            scenario = "你打出強牌，河牌乾燥未變。",
            heroPosition = Position.CO,
            heroHand = "Ad Ah", board = "Kd 8c 3h 2s 4d",
            potBb = 18.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Overpair 在不變的河牌可 thin value 1/3 pot，抽出對手中對與弱 K。"
        ),
        PostflopProblem(
            title = "Turn give-up 決策",
            scenario = "你翻牌 c-bet 被 call，turn 補對手 draw card。",
            heroPosition = Position.UTG,
            heroHand = "Ah Qd", board = "Jh 8s 3c Th",
            potBb = 11.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "Equity 下降（對手補 set/順子/同花 draw），give up 比 barrel 更省 bb。"
        ),
        PostflopProblem(
            title = "BvB SB open BB 3-bet pot 翻牌",
            scenario = "SB open BB 3-bet you call，翻牌中。",
            heroPosition = Position.SB,
            heroHand = "Ad Kd", board = "Qh 7c 3s",
            potBb = 22.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "3-bet 底池 OOP 對未擊中板大量 check，將決策權交給 IP。"
        ),
        PostflopProblem(
            title = "翻牌 nut flush draw",
            scenario = "你有堅果同花 draw + 兩 overcards。",
            heroPosition = Position.BB,
            heroHand = "Ah 5h", board = "Qh 7h 3c",
            potBb = 7.0, toCallBb = 3.5,
            correctAction = Action.Kind.RAISE,
            explanation = "Nut flush draw (~36% 兩街) + 2 overcards + fold equity = 高 EV check-raise。"
        ),
        PostflopProblem(
            title = "Set 翻牌 vs all-in 風險",
            scenario = "深堆 set 翻牌，對手 raise pot。",
            heroPosition = Position.BTN,
            heroHand = "5c 5d", board = "5s Qh Jc",
            potBb = 15.0, toCallBb = 10.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Set 在動態板要趁早把錢推進，3-bet shove 對抗 draws 與 over pairs。"
        ),
        PostflopProblem(
            title = "Turn check-raise blocker bet",
            scenario = "Turn 對手小 blocker bet，你有中對。",
            heroPosition = Position.BTN,
            heroHand = "Tc 9c", board = "Td 7s 4c 2h",
            potBb = 12.0, toCallBb = 3.0,
            correctAction = Action.Kind.RAISE,
            explanation = "小 blocker bet 範圍上限薄弱，中對 raise 為 thin value + fold equity。"
        ),
        PostflopProblem(
            title = "River 對手過牌讓你下注",
            scenario = "河牌對手 check，你有 second pair。",
            heroPosition = Position.BTN,
            heroHand = "Qh Jc", board = "Ad Jh 7c 2s 5d",
            potBb = 10.0, toCallBb = 0.0,
            correctAction = Action.Kind.CHECK,
            explanation = "Second pair on A-high river 應 check back 控池；下注只被更強跟，更弱蓋。"
        ),
        PostflopProblem(
            title = "Overpair 在 multiway",
            scenario = "三人翻牌，你 overpair。",
            heroPosition = Position.HJ,
            heroHand = "Jh Jd", board = "9c 7s 4h",
            potBb = 12.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Multiway overpair 必須 c-bet 保護，否則被多 draws 免費補；70% pot bet。"
        ),
        PostflopProblem(
            title = "Paired board 河牌反詐",
            scenario = "河牌補對，對手下大注。",
            heroPosition = Position.BB,
            heroHand = "Ah Kc", board = "Kd 8c 3h 8d 2c",
            potBb = 20.0, toCallBb = 16.0,
            correctAction = Action.Kind.CALL,
            explanation = "Top pair top kicker 雖未變動，補對讓對手 8x 變強；但 KK/AA blocker 在 hero，跟注。"
        ),
        PostflopProblem(
            title = "Squeeze 後翻牌乾燥",
            scenario = "你翻前 squeeze 成功，翻牌乾燥未擊中。",
            heroPosition = Position.SB,
            heroHand = "Ah Qd", board = "8c 5h 2s",
            potBb = 25.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Squeeze pot 範圍優勢明顯，翻牌 1/3 pot c-bet 高頻收 fold equity。"
        ),
        PostflopProblem(
            title = "Draw heavy 板 check-call",
            scenario = "翻牌極濕你有頂對中 kicker。",
            heroPosition = Position.BB,
            heroHand = "Kc 9h", board = "Kh Qh Th",
            potBb = 6.5, toCallBb = 4.5,
            correctAction = Action.Kind.CALL,
            explanation = "頂對在 monotone + connected 板僅勝過詐唬，raise 被 fold 出更強範圍；call 控池。"
        ),
        PostflopProblem(
            title = "ABC turn polarized bet",
            scenario = "Turn 補出對手 draw card，你有頂兩對。",
            heroPosition = Position.BTN,
            heroHand = "Ah Qd", board = "Qh 7s 3c Kd",
            potBb = 12.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "頂兩對在 turn 仍是強牌，pot-size bet 賺價值並折出弱對與 draws。"
        ),
        PostflopProblem(
            title = "Bluff catcher 河牌極化",
            scenario = "河牌乾燥對手 overbet。",
            heroPosition = Position.BTN,
            heroHand = "Ad Jh", board = "Jc 8s 3h 2c 7d",
            potBb = 18.0, toCallBb = 20.0,
            correctAction = Action.Kind.CALL,
            explanation = "Top pair top kicker，pot odds 需要 ~36% equity；對 polar overbet 範圍仍有 EV。"
        ),
        PostflopProblem(
            title = "Mid pair vs three barrel",
            scenario = "對手連續三街下注，你有中對。",
            heroPosition = Position.BTN,
            heroHand = "9h 9c", board = "Kd 7s 4h 2c 6d",
            potBb = 25.0, toCallBb = 20.0,
            correctAction = Action.Kind.FOLD,
            explanation = "中對對 triple barrel 範圍幾乎只勝詐唬，pot odds 33% 不夠；蓋牌。"
        ),
        PostflopProblem(
            title = "翻前 4-bet pot 翻牌 set",
            scenario = "你 4-bet AA call，翻牌補 set。",
            heroPosition = Position.CO,
            heroHand = "5c 5d", board = "5s Kh 2c",
            potBb = 45.0, toCallBb = 0.0,
            correctAction = Action.Kind.RAISE,
            explanation = "Set 在深堆 4-bet pot 必須開始 build pot；50% pot bet。"
        )
    )
}
