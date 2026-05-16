package com.pokercoach.ui.feedback

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 在 runtime 生成 Standard MIDI File (SMF Type 0)，避免靠外部音效素材。
 *
 * MIDI 規格摘要：
 *  - Header chunk "MThd" + 6 bytes：format=0, ntrks=1, division=ticks/quarter
 *  - Track chunk "MTrk" + length + events
 *  - 每個 event 前面是 variable-length delta-time（ticks）
 *  - Note On = 0x90 + channel, key, velocity
 *  - Note Off = 0x80 + channel, key, 0
 *  - Program Change = 0xC0 + channel, program
 *  - End of Track = FF 2F 00
 *
 * 我們生 3 個短旋律（每個 ~250-400ms）：
 *  - CHIP：木琴 (program 13) 上行三和弦琶音 C-E-G + 同時下 C 低音
 *  - DEAL：豎琴 (program 46) 五度跳音 + 七和弦點綴
 *  - BUTTON：鋼片琴 (program 8) 大三和弦同時擊出 + 高八度點綴
 *
 * Ticks per quarter = 480；BPM 預設 120 → 1 quarter = 500ms → 1 tick ≈ 1.04ms
 */
object MidiGenerator {

    private const val TPQ = 480       // ticks per quarter
    private const val CH = 0          // channel 0

    /** 寫所有需要的 MIDI 檔到 cacheDir，回傳 Sfx → File 對應表。 */
    fun ensureSoundFiles(cacheDir: File): Map<SoundManager.Sfx, File> {
        val dir = File(cacheDir, "sfx_midi").apply { mkdirs() }
        val map = mapOf(
            SoundManager.Sfx.CHIP   to File(dir, "chip.mid"),
            SoundManager.Sfx.DEAL   to File(dir, "deal.mid"),
            SoundManager.Sfx.BUTTON to File(dir, "button.mid")
        )
        if (!map[SoundManager.Sfx.CHIP]!!.exists())   map[SoundManager.Sfx.CHIP]!!.writeBytes(buildChip())
        if (!map[SoundManager.Sfx.DEAL]!!.exists())   map[SoundManager.Sfx.DEAL]!!.writeBytes(buildDeal())
        if (!map[SoundManager.Sfx.BUTTON]!!.exists()) map[SoundManager.Sfx.BUTTON]!!.writeBytes(buildButton())
        return map
    }

    // =================================================================
    // 各音效旋律設計（短而清脆，類似可愛系手遊回饋音）
    // =================================================================

    /** CHIP：木琴琶音 C5-E5-G5 + 同時 C4 低音 = 明亮 C 大三和弦。 */
    private fun buildChip(): ByteArray {
        val ev = mutableListOf<MidiEvent>()
        ev += programChange(0, 13)                 // Marimba/Xylophone
        // 低音 C4 (60) 持續整段
        ev += noteOn(0, 60, 70)
        // 琶音 C5-E5-G5（72-76-79），每音 60 ticks
        ev += noteOn(0, 72, 100)
        ev += noteOff(60, 72)
        ev += noteOn(0, 76, 95)
        ev += noteOff(60, 76)
        ev += noteOn(0, 79, 110)                   // 頂音稍重
        ev += noteOff(120, 79)
        ev += noteOff(0, 60)
        return assemble(ev)
    }

    /** DEAL：豎琴五度 + 七和弦點綴，模擬發牌的「滑過」感。 */
    private fun buildDeal(): ByteArray {
        val ev = mutableListOf<MidiEvent>()
        ev += programChange(0, 46)                 // Orchestral Harp
        // 同時擊出 A4-E5 五度
        ev += noteOn(0, 69, 80)                    // A4
        ev += noteOn(0, 76, 80)                    // E5
        // 持續 90 ticks，然後加入 G5 (B♭ maj9 風)
        ev += noteOn(90, 79, 70)                   // G5
        ev += noteOff(120, 69)
        ev += noteOff(0, 76)
        ev += noteOff(0, 79)
        return assemble(ev)
    }

    /** BUTTON：鋼片琴大三和弦同時擊出 + 高八度點綴，短促可愛。 */
    private fun buildButton(): ByteArray {
        val ev = mutableListOf<MidiEvent>()
        ev += programChange(0, 8)                  // Celesta
        // 同時擊 F5-A5-C6 (F maj 三和弦)
        ev += noteOn(0, 77, 95)
        ev += noteOn(0, 81, 90)
        ev += noteOn(0, 84, 95)
        ev += noteOff(80, 77)
        ev += noteOff(0, 81)
        ev += noteOff(0, 84)
        // 高八度點綴 F6 (89)
        ev += noteOn(20, 89, 70)
        ev += noteOff(60, 89)
        return assemble(ev)
    }

    // =================================================================
    // MIDI byte 組裝
    // =================================================================

    private data class MidiEvent(val deltaTicks: Int, val data: ByteArray)

    private fun noteOn(delta: Int, key: Int, vel: Int) =
        MidiEvent(delta, byteArrayOf((0x90 or CH).toByte(), key.toByte(), vel.toByte()))

    private fun noteOff(delta: Int, key: Int) =
        MidiEvent(delta, byteArrayOf((0x80 or CH).toByte(), key.toByte(), 0))

    private fun programChange(delta: Int, program: Int) =
        MidiEvent(delta, byteArrayOf((0xC0 or CH).toByte(), program.toByte()))

    private fun endOfTrack(delta: Int = 0) =
        MidiEvent(delta, byteArrayOf(0xFF.toByte(), 0x2F, 0x00))

    /** Tempo meta: FF 51 03 tt tt tt (microseconds per quarter). */
    private fun tempo(delta: Int, bpm: Int): MidiEvent {
        val usPerQuarter = (60_000_000 / bpm)
        return MidiEvent(
            delta,
            byteArrayOf(
                0xFF.toByte(), 0x51, 0x03,
                ((usPerQuarter ushr 16) and 0xFF).toByte(),
                ((usPerQuarter ushr 8) and 0xFF).toByte(),
                (usPerQuarter and 0xFF).toByte()
            )
        )
    }

    private fun assemble(rawEvents: List<MidiEvent>): ByteArray {
        // 開頭加 tempo（BPM 140 偏快、回饋感乾脆）
        val events = mutableListOf<MidiEvent>()
        events += tempo(0, 140)
        events += rawEvents
        events += endOfTrack(0)

        // Track bytes
        val trackBody = ByteArrayOutputStream()
        for (e in events) {
            trackBody.write(varLen(e.deltaTicks))
            trackBody.write(e.data)
        }
        val trackBytes = trackBody.toByteArray()

        // 全檔組裝
        val out = ByteArrayOutputStream()
        // Header chunk
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        out.write(intToBE(6, 4))                   // header length = 6
        out.write(intToBE(0, 2))                   // format = 0
        out.write(intToBE(1, 2))                   // ntrks = 1
        out.write(intToBE(TPQ, 2))                 // division
        // Track chunk
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        out.write(intToBE(trackBytes.size, 4))
        out.write(trackBytes)
        return out.toByteArray()
    }

    /** 大端整數 → bytes（指定長度 1..4）。 */
    private fun intToBE(value: Int, bytes: Int): ByteArray {
        val r = ByteArray(bytes)
        for (i in 0 until bytes) {
            r[bytes - 1 - i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
        return r
    }

    /** MIDI variable-length quantity 編碼（最高 0x0FFFFFFF）。 */
    private fun varLen(value: Int): ByteArray {
        require(value >= 0) { "varLen needs non-negative: $value" }
        if (value == 0) return byteArrayOf(0)
        val bytes = mutableListOf<Int>()
        var v = value
        bytes.add(v and 0x7F)
        v = v ushr 7
        while (v > 0) {
            bytes.add((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        bytes.reverse()
        return ByteArray(bytes.size) { bytes[it].toByte() }
    }
}
