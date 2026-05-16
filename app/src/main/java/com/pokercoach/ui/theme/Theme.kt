package com.pokercoach.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PokerLightScheme = lightColorScheme(
    primary = HudAccent,
    onPrimary = HudPanel,
    secondary = HudAccent2,
    onSecondary = HudTextPrimary,
    tertiary = SeatHeroBlue,
    background = HudBg,
    onBackground = HudTextPrimary,
    surface = HudPanel,
    onSurface = HudTextPrimary,
    surfaceVariant = HudPanelSoft,
    error = HudBad,
    onError = HudPanel
)

// 圓潤的元件形狀（卡通風）
val PokerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(18.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PokerCoachTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PokerLightScheme,
        typography = PokerTypography,
        shapes = PokerShapes,
        content = content
    )
}
