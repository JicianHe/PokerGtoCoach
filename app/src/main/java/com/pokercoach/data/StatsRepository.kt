package com.pokercoach.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pokercoach.core.game.Street
import com.pokercoach.core.model.Action
import com.pokercoach.core.model.Position
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 學習統計持久化：累積總手數、每種評價類別的次數、按位置/街切分。
 *
 * 使用 DataStore Preferences 存單一 JSON blob（schema 簡單，無須完整 Room）。
 */
private val Context.statsDataStore by preferencesDataStore(name = "pokercoach_stats")

@Serializable
data class LearningStats(
    val handsPlayed: Int = 0,
    val totalDecisions: Int = 0,
    val optimal: Int = 0,
    val acceptable: Int = 0,
    val suboptimal: Int = 0,
    val blunder: Int = 0,
    /** position 名稱 → (decisions, optimal+acceptable count) */
    val byPosition: Map<String, PositionStat> = emptyMap(),
    /** street 名稱 → 同上 */
    val byStreet: Map<String, PositionStat> = emptyMap()
) {
    val optimalRate: Double get() =
        if (totalDecisions == 0) 0.0 else optimal.toDouble() / totalDecisions
    val acceptableRate: Double get() =
        if (totalDecisions == 0) 0.0 else (optimal + acceptable).toDouble() / totalDecisions
    val blunderRate: Double get() =
        if (totalDecisions == 0) 0.0 else blunder.toDouble() / totalDecisions

    @Serializable
    data class PositionStat(val decisions: Int = 0, val good: Int = 0) {
        val rate: Double get() = if (decisions == 0) 0.0 else good.toDouble() / decisions
    }
}

class StatsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY_BLOB = stringPreferencesKey("stats_json")

    val flow: Flow<LearningStats> = context.statsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val s = prefs[KEY_BLOB]
            if (s.isNullOrEmpty()) LearningStats()
            else try { json.decodeFromString(LearningStats.serializer(), s) }
            catch (_: Exception) { LearningStats() }
        }

    suspend fun recordDecision(
        verdict: VerdictBucket,
        position: Position,
        street: Street
    ) {
        context.statsDataStore.edit { prefs ->
            val cur = prefs[KEY_BLOB]?.let { runCatching { json.decodeFromString(LearningStats.serializer(), it) }.getOrNull() }
                ?: LearningStats()
            val isGood = verdict == VerdictBucket.OPTIMAL || verdict == VerdictBucket.ACCEPTABLE

            val posKey = position.name
            val streetKey = street.name
            val posStat = (cur.byPosition[posKey] ?: LearningStats.PositionStat())
                .let { it.copy(decisions = it.decisions + 1, good = it.good + if (isGood) 1 else 0) }
            val streetStat = (cur.byStreet[streetKey] ?: LearningStats.PositionStat())
                .let { it.copy(decisions = it.decisions + 1, good = it.good + if (isGood) 1 else 0) }

            val updated = cur.copy(
                totalDecisions = cur.totalDecisions + 1,
                optimal = cur.optimal + if (verdict == VerdictBucket.OPTIMAL) 1 else 0,
                acceptable = cur.acceptable + if (verdict == VerdictBucket.ACCEPTABLE) 1 else 0,
                suboptimal = cur.suboptimal + if (verdict == VerdictBucket.SUBOPTIMAL) 1 else 0,
                blunder = cur.blunder + if (verdict == VerdictBucket.BLUNDER) 1 else 0,
                byPosition = cur.byPosition + (posKey to posStat),
                byStreet = cur.byStreet + (streetKey to streetStat)
            )
            prefs[KEY_BLOB] = json.encodeToString(LearningStats.serializer(), updated)
        }
    }

    suspend fun incrementHands() {
        context.statsDataStore.edit { prefs ->
            val cur = prefs[KEY_BLOB]?.let { runCatching { json.decodeFromString(LearningStats.serializer(), it) }.getOrNull() }
                ?: LearningStats()
            val updated = cur.copy(handsPlayed = cur.handsPlayed + 1)
            prefs[KEY_BLOB] = json.encodeToString(LearningStats.serializer(), updated)
        }
    }

    suspend fun reset() {
        context.statsDataStore.edit { it.remove(KEY_BLOB) }
    }

    enum class VerdictBucket { OPTIMAL, ACCEPTABLE, SUBOPTIMAL, BLUNDER, UNKNOWN }
}
