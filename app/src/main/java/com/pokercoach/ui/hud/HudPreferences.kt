package com.pokercoach.ui.hud

/**
 * HUD 可見性偏好（Settings 的 HUD 子集）。
 *
 * 為何獨立：HUD Panel 不該直接依賴 SettingsRepository.Settings
 * （減少耦合 + 便於測試與 Preview）。
 */
data class HudPreferences(
    val showGtoBars: Boolean = true,
    val showEvBreakdown: Boolean = true,
    val showAiInsight: Boolean = true,
    val showPostflopChecklist: Boolean = true,
    val showPotOdds: Boolean = true
) {
    companion object {
        val DEFAULT = HudPreferences()

        fun from(s: com.pokercoach.data.SettingsRepository.Settings) = HudPreferences(
            showGtoBars = s.hudShowGtoBars,
            showEvBreakdown = s.hudShowEvBreakdown,
            showAiInsight = s.hudShowAiInsight,
            showPostflopChecklist = s.hudShowPostflopChecklist,
            showPotOdds = s.hudShowPotOdds
        )
    }
}
