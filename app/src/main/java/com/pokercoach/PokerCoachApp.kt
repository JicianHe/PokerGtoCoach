package com.pokercoach

import android.app.Application
import com.pokercoach.data.HandHistoryRepository
import com.pokercoach.data.SettingsRepository
import com.pokercoach.data.StatsRepository
import com.pokercoach.ui.feedback.SoundManager

/**
 * 應用容器：持有跨畫面的 repository 與 SoundManager 單例。
 *
 * 不引 Hilt，這個小型 app 用手動 service locator 已綽綽有餘。
 * ViewModel 透過 (LocalContext.current.applicationContext as PokerCoachApp) 取得依賴。
 */
class PokerCoachApp : Application() {
    val settingsRepo: SettingsRepository by lazy { SettingsRepository(this) }
    val statsRepo: StatsRepository by lazy { StatsRepository(this) }
    val historyRepo: HandHistoryRepository by lazy { HandHistoryRepository(this) }
    val soundManager: SoundManager by lazy { SoundManager(this) }

    override fun onTerminate() {
        soundManager.release()
        super.onTerminate()
    }
}
