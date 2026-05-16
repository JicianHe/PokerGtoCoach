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
    val byStreet: Map<String, PositionStat> = emptyMap(),
    /** 英雄贏的手數（不含平分）。 */
    val heroHandsWon: Int = 0,
    /** 目前連續做出最佳/可接受決策的次數。 */
    val currentGoodStreak: Int = 0,
    /** 歷史最高連續好決策數。 */
    val bestGoodStreak: Int = 0,
    /** Trainer 答對總題數。 */
    val trainerCorrect: Int = 0,
    /** Trainer 連續答對。 */
    val trainerCurrentStreak: Int = 0,
    /** Trainer 歷史最高連勝。 */
    val trainerBestStreak: Int = 0,
    /** 已解鎖的成就 id 集合。 */
    val unlockedAchievements: Set<String> = emptySet()
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

            val newStreak = if (isGood) cur.currentGoodStreak + 1 else 0

            val updated = cur.copy(
                totalDecisions = cur.totalDecisions + 1,
                optimal = cur.optimal + if (verdict == VerdictBucket.OPTIMAL) 1 else 0,
                acceptable = cur.acceptable + if (verdict == VerdictBucket.ACCEPTABLE) 1 else 0,
                suboptimal = cur.suboptimal + if (verdict == VerdictBucket.SUBOPTIMAL) 1 else 0,
                blunder = cur.blunder + if (verdict == VerdictBucket.BLUNDER) 1 else 0,
                byPosition = cur.byPosition + (posKey to posStat),
                byStreet = cur.byStreet + (streetKey to streetStat),
                currentGoodStreak = newStreak,
                bestGoodStreak = maxOf(cur.bestGoodStreak, newStreak)
            )
            saveAndUnlock(prefs, updated)
        }
    }

    /** 記錄英雄是否贏了這一手（含平分時 share 計）。 */
    suspend fun recordHeroResult(heroWon: Boolean) {
        context.statsDataStore.edit { prefs ->
            val cur = current(prefs)
            val updated = cur.copy(heroHandsWon = cur.heroHandsWon + if (heroWon) 1 else 0)
            saveAndUnlock(prefs, updated)
        }
    }

    /** Trainer 答題結果。 */
    suspend fun recordTrainerAnswer(correct: Boolean) {
        context.statsDataStore.edit { prefs ->
            val cur = current(prefs)
            val newStreak = if (correct) cur.trainerCurrentStreak + 1 else 0
            val updated = cur.copy(
                trainerCorrect = cur.trainerCorrect + if (correct) 1 else 0,
                trainerCurrentStreak = newStreak,
                trainerBestStreak = maxOf(cur.trainerBestStreak, newStreak)
            )
            saveAndUnlock(prefs, updated)
        }
    }

    suspend fun incrementHands() {
        context.statsDataStore.edit { prefs ->
            val cur = current(prefs)
            val updated = cur.copy(handsPlayed = cur.handsPlayed + 1)
            saveAndUnlock(prefs, updated)
        }
    }

    suspend fun reset() {
        context.statsDataStore.edit { it.remove(KEY_BLOB) }
    }

    private fun current(prefs: Preferences): LearningStats =
        prefs[KEY_BLOB]?.let { runCatching { json.decodeFromString(LearningStats.serializer(), it) }.getOrNull() }
            ?: LearningStats()

    /**
     * 寫回 + 計算當下已達成的成就，把新解鎖的 id 加進 set。
     */
    private fun saveAndUnlock(prefs: androidx.datastore.preferences.core.MutablePreferences, s: LearningStats) {
        val newlyUnlocked = com.pokercoach.core.achievements.AchievementRegistry.ALL
            .filter { it.isUnlocked(s) }
            .map { it.id }
            .toSet()
        val merged = s.copy(unlockedAchievements = s.unlockedAchievements + newlyUnlocked)
        prefs[KEY_BLOB] = json.encodeToString(LearningStats.serializer(), merged)
    }

    enum class VerdictBucket { OPTIMAL, ACCEPTABLE, SUBOPTIMAL, BLUNDER, UNKNOWN }
}
