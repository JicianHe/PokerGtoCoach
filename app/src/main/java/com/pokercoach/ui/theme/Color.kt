package com.pokercoach.ui.theme

import androidx.compose.ui.graphics.Color

// ===== 桌面（馬卡龍綠 / 薄荷）=====
val TableTopMint   = Color(0xFFB8E6D3)   // 淺薄荷
val TableTopMid    = Color(0xFF8DD3B5)
val TableTopDeep   = Color(0xFF5FBA8E)
val TableRail      = Color(0xFFFFB3C7)   // 粉紅木邊
val TableRailHi    = Color(0xFFFFD1DC)

// ===== 籌碼（馬卡龍色系）=====
val ChipPeach   = Color(0xFFFFCDB2)
val ChipPink    = Color(0xFFFFB4A2)
val ChipCoral   = Color(0xFFE5989B)
val ChipPurple  = Color(0xFFB5838D)
val ChipMint    = Color(0xFF9DD9C5)
val ChipSky     = Color(0xFF8ECAE6)
val ChipLemon   = Color(0xFFFFE066)

// ===== 座位狀態 =====
val SeatActiveGlow   = Color(0xFFFFC857)  // 暖黃（行動中）
val SeatIdleSoft     = Color(0xFFDDE5EB)  // 灰白
val SeatFoldedGray   = Color(0xFFBFBFBF)
val SeatAllInOrange  = Color(0xFFFF8C61)
val SeatHeroBlue     = Color(0xFF80C2E3)

// ===== HUD 面板（米白底 + 柔和邊）=====
val HudBg            = Color(0xFFFFF8F0)   // 米白
val HudPanel         = Color(0xFFFFFFFF)
val HudPanelSoft     = Color(0xFFFFF1E6)
val HudAccent        = Color(0xFFFF8FA3)   // 主強調粉
val HudAccent2       = Color(0xFF7FB7BE)   // 副強調青
val HudTextPrimary   = Color(0xFF3A3A3A)
val HudTextDim       = Color(0xFF8B8B8B)
val HudTextLight     = Color(0xFFB0B0B0)
val HudGood          = Color(0xFF6BC598)
val HudWarn          = Color(0xFFFFB454)
val HudBad           = Color(0xFFE57373)

// ===== 牌張 =====
val CardFaceCream  = Color(0xFFFFFDF7)
val CardBackPattern = Color(0xFFFFB4A2)
val CardBackDeep    = Color(0xFFE5989B)
val CardSuitRed     = Color(0xFFE63946)   // 愛心
val CardSuitPink    = Color(0xFFFF8FA3)   // 雲朵
val CardSuitBlue    = Color(0xFF5BC0EB)   // 飛機
val CardSuitYellow  = Color(0xFFFFCB47)   // 星星
val CardBorder      = Color(0xFFE8C5C5)

// ===== 行動按鈕（柔和馬卡龍）=====
val ActionFoldGray  = Color(0xFFC9D1D9)
val ActionCheckBlue = Color(0xFF89C2F0)
val ActionCallGreen = Color(0xFF9DDFB8)
val ActionRaisePink = Color(0xFFFF9BB0)
val ActionAllInRed  = Color(0xFFFF6F91)

// ===== 背景漸層 =====
val BgGradientTop    = Color(0xFFFFE4EC)   // 粉紅
val BgGradientMid    = Color(0xFFFFF0F5)
val BgGradientBottom = Color(0xFFE6F4F1)   // 薄荷

// =====================================================================
// 向後相容別名（舊命名映射到新 macaron 配色）
// 保留以避免破壞既有引用；新程式碼請用上方語意化命名。
// =====================================================================
val FeltDark   = TableTopDeep
val FeltMid    = TableTopMid
val FeltLight  = TableTopMint
val FeltRail   = TableRail
val FeltRailHi = TableRailHi

val SeatActive = SeatActiveGlow
val SeatIdle   = SeatIdleSoft
val SeatFolded = SeatFoldedGray
val SeatAllIn  = SeatAllInOrange
val SeatHero   = SeatHeroBlue

val ChipWhite = ChipPeach
val ChipRed   = ChipPink
val ChipGreen = ChipMint
val ChipBlue  = ChipSky
val ChipBlack = ChipPurple

val ActionFold  = ActionFoldGray
val ActionCheck = ActionCheckBlue
val ActionCall  = ActionCallGreen
val ActionRaise = ActionRaisePink
val ActionAllIn = ActionAllInRed
