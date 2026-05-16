package com.pokercoach.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * 音效播放器：用 MediaPlayer 播 runtime 生成的 MIDI 檔（含和弦），
 * 不依賴任何外部素材，APK 體積零增加。
 *
 * 為何不用 SoundPool？
 *  - SoundPool 不支援 MIDI，只能 PCM/OGG/WAV。
 *  - MIDI 由系統內建 SoundBank 合成（GM 音色），跨裝置一致且檔案 < 300 bytes。
 *
 * 為何要 pool 多個 MediaPlayer？
 *  - 同一 MediaPlayer 重複 start() 在 Android 上會卡住或延遲。
 *  - 每個 Sfx 各維持 2 個實例輪流用，可重疊播放（e.g. 連續下注）。
 */
class SoundManager(private val context: Context) {

    enum class Sfx { CHIP, DEAL, BUTTON }

    @Volatile var enabled: Boolean = true

    private val files = MidiGenerator.ensureSoundFiles(context.cacheDir)
    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // 每個 sfx 兩個 player 輪用，避免快速連按時被截斷
    private val players: Map<Sfx, Array<MediaPlayer?>> = Sfx.values().associateWith {
        arrayOfNulls<MediaPlayer>(POOL_SIZE)
    }
    private val cursors: MutableMap<Sfx, Int> = Sfx.values().associateWith { 0 }.toMutableMap()

    init {
        // 預熱：每個 sfx 預先準備一個 player
        for (sfx in Sfx.values()) {
            buildPlayer(sfx)?.let { players[sfx]!![0] = it }
        }
    }

    fun play(sfx: Sfx, volume: Float = 0.6f) {
        if (!enabled) return
        val slot = nextSlot(sfx)
        val arr = players[sfx] ?: return
        val mp = arr[slot] ?: buildPlayer(sfx)?.also { arr[slot] = it } ?: return
        try {
            mp.setVolume(volume, volume)
            if (mp.isPlaying) {
                mp.seekTo(0)
            } else {
                mp.start()
            }
        } catch (e: IllegalStateException) {
            // player 處於非法狀態，重建
            arr[slot] = buildPlayer(sfx)
            arr[slot]?.start()
        } catch (e: Exception) {
            Log.w(TAG, "play($sfx) failed", e)
        }
    }

    fun release() {
        for ((_, arr) in players) {
            for (i in arr.indices) {
                try { arr[i]?.release() } catch (_: Exception) {}
                arr[i] = null
            }
        }
    }

    private fun nextSlot(sfx: Sfx): Int {
        val c = cursors[sfx] ?: 0
        val next = (c + 1) % POOL_SIZE
        cursors[sfx] = next
        return c
    }

    private fun buildPlayer(sfx: Sfx): MediaPlayer? {
        val f = files[sfx] ?: return null
        if (!f.exists() || f.length() == 0L) return null
        return try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(f.absolutePath)
                isLooping = false
                prepare()                // MIDI 檔極小（< 300 bytes），同步 prepare 不會阻塞
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildPlayer($sfx) failed", e)
            null
        }
    }

    companion object {
        private const val POOL_SIZE = 2
        private const val TAG = "SoundManager"
    }
}
