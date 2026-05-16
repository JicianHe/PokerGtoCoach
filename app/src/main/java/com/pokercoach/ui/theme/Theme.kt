package com.pokercoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PokerDarkScheme = darkColorScheme(
    primary = HudAccent,
    onPrimary = HudBg,
    secondary = SeatHero,
    background = FeltDark,
    onBackground = HudTextPrimary,
    surface = HudPanel,
    onSurface = HudTextPrimary,
    error = HudBad,
    onError = HudTextPrimary
)

@Composable
fun PokerCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PokerDarkScheme,            // 撲克桌一律深色 UI
        typography = PokerTypography,
        content = content
    )
}
