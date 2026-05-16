package com.pokercoach.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 應用設定持久化（DataStore Preferences）。
 *
 * 存：音效 / 震動 / 動畫開關 + AI 難度。
 * 為避免阻塞主執行緒，所有讀寫皆為 Flow / suspend。
 */
private val Context.settingsDataStore by preferencesDataStore(name = "pokercoach_settings")

class SettingsRepository(private val context: Context) {

    enum class Difficulty(val storeKey: String) {
        PURE_GTO("gto"),
        MIXED_PROFILES("mixed"),
        FISH_POOL("fish");
        companion object {
            fun fromKey(k: String?): Difficulty =
                values().firstOrNull { it.storeKey == k } ?: MIXED_PROFILES
        }
    }

    data class Settings(
        val soundOn: Boolean = true,
        val hapticOn: Boolean = true,
        val animationsOn: Boolean = true,
        val difficulty: Difficulty = Difficulty.MIXED_PROFILES
    )

    private object Keys {
        val SOUND     = booleanPreferencesKey("sound")
        val HAPTIC    = booleanPreferencesKey("haptic")
        val ANIM      = booleanPreferencesKey("anim")
        val DIFFICULTY = stringPreferencesKey("difficulty")
    }

    val settingsFlow: Flow<Settings> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            Settings(
                soundOn = prefs[Keys.SOUND] ?: true,
                hapticOn = prefs[Keys.HAPTIC] ?: true,
                animationsOn = prefs[Keys.ANIM] ?: true,
                difficulty = Difficulty.fromKey(prefs[Keys.DIFFICULTY])
            )
        }

    suspend fun setSound(on: Boolean) =
        context.settingsDataStore.edit { it[Keys.SOUND] = on }
    suspend fun setHaptic(on: Boolean) =
        context.settingsDataStore.edit { it[Keys.HAPTIC] = on }
    suspend fun setAnimations(on: Boolean) =
        context.settingsDataStore.edit { it[Keys.ANIM] = on }
    suspend fun setDifficulty(d: Difficulty) =
        context.settingsDataStore.edit { it[Keys.DIFFICULTY] = d.storeKey }

    suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }
}
