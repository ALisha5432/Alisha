package com.wpradar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wpradar.core.RadarSettings
import com.wpradar.core.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val GROUPS = stringPreferencesKey("groups")
        val KEYWORDS = stringPreferencesKey("keywords")
        val FOLD = booleanPreferencesKey("fold_diacritics")
        val PRIVATE = booleanPreferencesKey("include_private")
        val LOUD = booleanPreferencesKey("loud_mode")
        val REPEAT = booleanPreferencesKey("repeat_alert")
        val RECENT_GROUPS = stringPreferencesKey("recent_groups")
    }

    val settings: Flow<RadarSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            RadarSettings(
                enabled = p[Keys.ENABLED] ?: true,
                groups = decode(p[Keys.GROUPS]),
                keywords = decode(p[Keys.KEYWORDS]),
                foldDiacritics = p[Keys.FOLD] ?: true,
                includePrivateChats = p[Keys.PRIVATE] ?: false,
                loudMode = p[Keys.LOUD] ?: false,
                repeatAlert = p[Keys.REPEAT] ?: false,
                recentGroups = decode(p[Keys.RECENT_GROUPS]),
            )
        }

    suspend fun current(): RadarSettings = settings.first()

    suspend fun setEnabled(value: Boolean) = setFlag(Keys.ENABLED, value)
    suspend fun setFoldDiacritics(value: Boolean) = setFlag(Keys.FOLD, value)
    suspend fun setIncludePrivateChats(value: Boolean) = setFlag(Keys.PRIVATE, value)
    suspend fun setLoudMode(value: Boolean) = setFlag(Keys.LOUD, value)
    suspend fun setRepeatAlert(value: Boolean) = setFlag(Keys.REPEAT, value)

    suspend fun addGroup(name: String) = addItems(Keys.GROUPS, listOf(name))
    suspend fun removeGroup(name: String) = removeItem(Keys.GROUPS, name)

    /** Vergül və ya yeni sətirlə bir neçə açar söz birdən əlavə etmək olar. */
    suspend fun addKeywords(input: String) = addItems(Keys.KEYWORDS, input.split(',', '\n'))
    suspend fun removeKeyword(word: String) = removeItem(Keys.KEYWORDS, word)

    /** Dinləyicinin gördüyü qrup adları — Ayarlarda bir toxunuşla əlavə etmək üçün. */
    suspend fun noteGroupSeen(group: String) {
        context.settingsStore.edit { p ->
            val list = decode(p[Keys.RECENT_GROUPS])
            if (list.firstOrNull() != group) {
                p[Keys.RECENT_GROUPS] = encode((listOf(group) + list.filter { it != group }).take(MAX_RECENT))
            }
        }
    }

    private suspend fun setFlag(key: Preferences.Key<Boolean>, value: Boolean) {
        context.settingsStore.edit { it[key] = value }
    }

    private suspend fun addItems(key: Preferences.Key<String>, items: List<String>) {
        val cleaned = items.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return
        context.settingsStore.edit { p ->
            val list = decode(p[key]).toMutableList()
            val known = list.map { norm(it) }.toMutableSet()
            for (item in cleaned) {
                if (known.add(norm(item))) list.add(item)
            }
            p[key] = encode(list)
        }
    }

    private suspend fun removeItem(key: Preferences.Key<String>, item: String) {
        context.settingsStore.edit { p -> p[key] = encode(decode(p[key]).filter { it != item }) }
    }

    private companion object {
        const val SEPARATOR = '\u001F'
        const val MAX_RECENT = 25

        fun encode(list: List<String>): String = list.joinToString(SEPARATOR.toString())

        fun decode(value: String?): List<String> =
            value?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

        fun norm(s: String): String = TextNormalizer.normalize(s, foldDiacritics = false)
    }
}
