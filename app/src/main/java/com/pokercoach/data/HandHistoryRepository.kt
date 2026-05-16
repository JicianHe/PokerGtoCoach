package com.pokercoach.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pokercoach.core.game.GameEvent
import com.pokercoach.core.game.Street
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 手牌歷史持久化：把每手結束時的精簡記錄序列化為 JSON 存起。
 *
 * 為避免 DataStore 單檔過大，限制只保留最近 200 手。每手記錄包含：
 *   - 手號、按鈕座位、贏家、贏金、結束原因、最終 board、動作摘要文字
 */
private val Context.historyDataStore by preferencesDataStore(name = "pokercoach_history")

@Serializable
data class HandRecord(
    val handNumber: Int,
    val buttonSeat: Int,
    val winners: List<Int>,
    val payouts: Map<Int, Double>,
    val reason: String,
    val finalBoard: String,
    val actions: List<String>,
    val timestamp: Long,
    /** 座位 → 玩家名稱（含英雄；舊紀錄為空 map）。 */
    val playerNames: Map<Int, String> = emptyMap(),
    /** 英雄座位（舊紀錄預設 0）。 */
    val heroSeat: Int = 0,
    /** 英雄底牌字串，例如 "A♠ K♥"（用於 Replay）。 */
    val heroHole: String = "",
    /** 翻牌/轉牌/河牌字串（用於 Replay 階段切換）。 */
    val flop: String = "",
    val turn: String = "",
    val river: String = ""
)

class HandHistoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY = stringPreferencesKey("history_json")
    private val maxRecords = 200

    val flow: Flow<List<HandRecord>> = context.historyDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val s = prefs[KEY]
            if (s.isNullOrEmpty()) emptyList()
            else try {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(HandRecord.serializer()), s)
            } catch (_: Exception) { emptyList() }
        }

    suspend fun append(record: HandRecord) {
        context.historyDataStore.edit { prefs ->
            val existing = prefs[KEY]?.let {
                runCatching {
                    json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(HandRecord.serializer()), it)
                }.getOrNull()
            } ?: emptyList()
            val merged = (listOf(record) + existing).take(maxRecords)
            prefs[KEY] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(HandRecord.serializer()),
                merged
            )
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { it.remove(KEY) }
    }

    companion object {
        /** 從事件 log 構造 HandRecord（在手牌結束時呼叫）。 */
        fun fromLog(
            handNumber: Int,
            buttonSeat: Int,
            log: List<GameEvent>,
            finalBoard: List<com.pokercoach.core.model.Card>,
            players: List<com.pokercoach.core.game.Player> = emptyList(),
            heroSeat: Int = 0
        ): HandRecord? {
            val ended = log.lastOrNull() as? GameEvent.HandEnded ?: return null
            fun nameOf(seat: Int): String =
                if (seat == heroSeat) com.pokercoach.ui.theme.Strings.NAME_YOU
                else players.firstOrNull { it.seatIndex == seat }?.name ?: "座位 $seat"
            val actionLines = log.mapNotNull { ev ->
                when (ev) {
                    is GameEvent.ActionTaken ->
                        "${com.pokercoach.ui.theme.Strings.street(ev.street)} ${nameOf(ev.seat)}: ${com.pokercoach.ui.theme.Strings.actionLabel(ev.action)} → 底池 ${"%.1f".format(ev.potAfter)}bb"
                    is GameEvent.StreetDealt ->
                        "── ${com.pokercoach.ui.theme.Strings.street(ev.street)}：${ev.newBoardCards.joinToString(" ")}"
                    is GameEvent.BlindsPosted ->
                        "小盲 ${ev.sb}bb（${nameOf(ev.sbSeat)}）/ 大盲 ${ev.bb}bb（${nameOf(ev.bbSeat)}）"
                    else -> null
                }
            }
            val streetCards = log.filterIsInstance<GameEvent.StreetDealt>()
            val flopCards = streetCards.firstOrNull { it.street == Street.FLOP }?.newBoardCards?.joinToString(" ") ?: ""
            val turnCard = streetCards.firstOrNull { it.street == Street.TURN }?.newBoardCards?.joinToString(" ") ?: ""
            val riverCard = streetCards.firstOrNull { it.street == Street.RIVER }?.newBoardCards?.joinToString(" ") ?: ""
            val hero = players.firstOrNull { it.seatIndex == heroSeat }
            val heroHoleStr = hero?.holeCards?.let { "${it.first} ${it.second}" } ?: ""
            return HandRecord(
                handNumber = handNumber,
                buttonSeat = buttonSeat,
                winners = ended.winners,
                payouts = ended.amounts,
                reason = ended.reason,
                finalBoard = finalBoard.joinToString(" "),
                actions = actionLines,
                timestamp = System.currentTimeMillis(),
                playerNames = players.associate { it.seatIndex to it.name },
                heroSeat = heroSeat,
                heroHole = heroHoleStr,
                flop = flopCards,
                turn = turnCard,
                river = riverCard
            )
        }
    }
}
