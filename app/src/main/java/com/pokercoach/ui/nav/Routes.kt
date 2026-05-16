package com.pokercoach.ui.nav

/** 應用內所有導覽路由集中宣告。 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val MENU = "menu"
    const val FREEPLAY = "freeplay"
    const val TRAINER = "trainer"
    const val RANGES = "ranges"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    /** Replay 接收 hand index (在 history list 中的位置)，例如 "replay/3"。 */
    const val REPLAY = "replay/{handIndex}"
    fun replay(handIndex: Int) = "replay/$handIndex"
}
