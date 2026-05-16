package com.pokercoach.ui.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 震動輕回饋封裝。長度短（10–30ms），用於按鈕點擊與下注瞬間。
 *
 * Android 12+ 走 VibratorManager；舊版 fallback 到 deprecated Vibrator.vibrate。
 */
class HapticFeedback(private val ctx: Context) {

    var enabled: Boolean = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tick() = vibrate(10)
    fun tap()  = vibrate(20)
    fun thud() = vibrate(35)

    private fun vibrate(ms: Long) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }
}

@Composable
fun rememberHaptic(): HapticFeedback {
    val ctx = LocalContext.current
    return remember(ctx) { HapticFeedback(ctx) }
}
