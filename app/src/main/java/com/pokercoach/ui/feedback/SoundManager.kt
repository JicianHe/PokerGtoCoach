package com.pokercoach.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.pokercoach.R

/**
 * 簡易音效播放器，使用 SoundPool（適合 < 1MB 短音效）。
 *
 * 音檔放在 res/raw/，缺檔時 SoundPool.load 會丟例外但我們吞掉，
 * 之後 play() 就靜默 no-op，不影響遊戲流程。
 *
 * 預期檔名（OGG, mono, 44.1kHz, < 200ms）：
 *   - res/raw/sfx_chip.ogg    籌碼下注
 *   - res/raw/sfx_deal.ogg    發牌
 *   - res/raw/sfx_button.ogg  按鈕點擊
 */
class SoundManager(private val context: Context) {

    enum class Sfx { CHIP, DEAL, BUTTON }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = buildMap {
        tryLoad(Sfx.CHIP,   "sfx_chip")?.let { put(Sfx.CHIP, it) }
        tryLoad(Sfx.DEAL,   "sfx_deal")?.let { put(Sfx.DEAL, it) }
        tryLoad(Sfx.BUTTON, "sfx_button")?.let { put(Sfx.BUTTON, it) }
    }

    var enabled: Boolean = true

    fun play(sfx: Sfx, volume: Float = 0.7f) {
        if (!enabled) return
        val id = ids[sfx] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()

    private fun tryLoad(sfx: Sfx, resName: String): Int? = runCatching {
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) null else pool.load(context, resId, 1)
    }.getOrNull()
}
