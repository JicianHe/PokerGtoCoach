package com.pokercoach

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pokercoach.ui.screen.GameScreen
import com.pokercoach.ui.theme.PokerCoachTheme
import com.pokercoach.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 鎖橫向 + 全螢幕沉浸式（120Hz 在 Tab S11 由系統自動套用，
        // 只要 Activity 有 SurfaceView/Compose 並在動態畫面）
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Compose 內部會用 RenderThread 自動 vsync 對齊；要求 high refresh：
        @Suppress("DEPRECATION")
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 120f
        }

        setContent {
            PokerCoachTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GameScreen(vm = viewModel)
                }
            }
        }
    }
}
