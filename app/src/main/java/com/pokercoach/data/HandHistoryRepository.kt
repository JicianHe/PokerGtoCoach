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
    val timestamp: Long
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
            finalBoard: List<com.pokercoach.core.model.Card>
        ): HandRecord? {
            val ended = log.lastOrNull() as? GameEvent.HandEnded ?: return null
            val actionLines = log.mapNotNull { ev ->
                when (ev) {
                    is GameEvent.ActionTaken ->
                        "${com.pokercoach.ui.theme.Strings.street(ev.street)} 座${ev.seat}: ${com.pokercoach.ui.theme.Strings.actionLabel(ev.action)} → 底池 ${"%.1f".format(ev.potAfter)}bb"
                    is GameEvent.StreetDealt ->
                        "── ${com.pokercoach.ui.theme.Strings.street(ev.street)}：${ev.newBoardCards.joinToString(" ")}"
                    is GameEvent.BlindsPosted ->
                        "小盲 ${ev.sb}bb（座${ev.sbSeat}）/ 大盲 ${ev.bb}bb（座${ev.bbSeat}）"
                    else -> null
                }
            }
            return HandRecord(
                handNumber = handNumber,
                buttonSeat = buttonSeat,
                winners = ended.winners,
                payouts = ended.amounts,
                reason = ended.reason,
                finalBoard = finalBoard.joinToString(" "),
                actions = actionLines,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}
