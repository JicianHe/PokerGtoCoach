package com.pokercoach

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pokercoach.ui.nav.AppNavHost
import com.pokercoach.ui.theme.PokerCoachTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 120f
        }

        setContent {
            PokerCoachTheme {
                AppNavHost()
            }
        }
    }
}
