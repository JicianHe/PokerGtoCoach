package com.pokercoach.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pokercoach.PokerCoachApp
import com.pokercoach.ui.screen.GameScreen
import com.pokercoach.ui.screen.HandHistoryScreen
import com.pokercoach.ui.screen.MainMenuScreen
import com.pokercoach.ui.screen.OnboardingScreen
import com.pokercoach.ui.screen.RangeVisualizerScreen
import com.pokercoach.ui.screen.ReplayScreen
import com.pokercoach.ui.screen.SettingsScreen
import com.pokercoach.ui.screen.StatsScreen
import com.pokercoach.ui.screen.TrainerScreen
import com.pokercoach.viewmodel.GameViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 應用單一 NavHost。
 *
 * 為何把 GameViewModel 的 factory 建在這層：
 *   - 確保 freeplay route 切回時 ViewModel 還活著（NavBackStackEntry-scoped）
 *   - Repository 從 PokerCoachApp 取得，避免 Compose 端到處傳 context
 *
 * 起始路由依 onboardingSeen 設定，初次安裝顯示教學引導。
 */
@Composable
fun AppNavHost() {
    val app = LocalContext.current.applicationContext as PokerCoachApp

    // 先讀到 settings 才決定 startDestination；尚未讀到時顯示空白 Surface 一瞬即可
    val onboardingSeen by app.settingsRepo.settingsFlow
        .map { it.onboardingSeen as Boolean? }
        .collectAsState(initial = null)

    Surface(modifier = Modifier.fillMaxSize()) {
        if (onboardingSeen == null) return@Surface  // splash 過渡（< 100ms）
        val nav = rememberNavController()
        val start = if (onboardingSeen == true) Routes.MENU else Routes.ONBOARDING
        NavHost(navController = nav, startDestination = start) {
            composable(Routes.ONBOARDING) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                OnboardingScreen(onFinish = {
                    scope.launch { app.settingsRepo.markOnboardingSeen() }
                    nav.navigate(Routes.MENU) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.MENU) {
                MainMenuScreen(
                    onFreePlay = { nav.navigate(Routes.FREEPLAY) },
                    onTrainer  = { nav.navigate(Routes.TRAINER) },
                    onRanges   = { nav.navigate(Routes.RANGES) },
                    onStats    = { nav.navigate(Routes.STATS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onHistory  = { nav.navigate(Routes.HISTORY) }
                )
            }
            composable(Routes.FREEPLAY) {
                val vm: GameViewModel = viewModel(
                    factory = GameViewModel.Factory(
                        settingsRepo = app.settingsRepo,
                        statsRepo = app.statsRepo,
                        historyRepo = app.historyRepo,
                        soundManager = app.soundManager
                    )
                )
                GameScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.TRAINER) {
                TrainerScreen(
                    statsRepo = app.statsRepo,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.RANGES) {
                RangeVisualizerScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.STATS) {
                StatsScreen(
                    statsRepo = app.statsRepo,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settingsRepo = app.settingsRepo,
                    statsRepo = app.statsRepo,
                    historyRepo = app.historyRepo,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.HISTORY) {
                HandHistoryScreen(
                    historyRepo = app.historyRepo,
                    onBack = { nav.popBackStack() },
                    onReplay = { index -> nav.navigate(Routes.replay(index)) }
                )
            }
            composable(
                route = Routes.REPLAY,
                arguments = listOf(androidx.navigation.navArgument("handIndex") {
                    type = androidx.navigation.NavType.IntType
                })
            ) { backEntry ->
                val index = backEntry.arguments?.getInt("handIndex") ?: 0
                ReplayScreen(
                    historyRepo = app.historyRepo,
                    handIndex = index,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
