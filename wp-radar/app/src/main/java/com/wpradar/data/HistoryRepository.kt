package com.wpradar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wpradar.core.ParsedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private val Context.historyStore: DataStore<Preferences> by preferencesDataStore(name = "history")

data class HistoryEntry(
    val id: String,
    val time: Long,
    val packageName: String,
    val group: String?,
    val sender: String?,
    val text: String,
    val keyword: String,
    val isGroup: Boolean,
) {
    companion object {
        fun from(msg: ParsedMessage, keyword: String, now: Long) = HistoryEntry(
            id = UUID.randomUUID().toString(),
            time = msg.timestamp ?: now,
            packageName = msg.packageName,
            group = msg.group,
            sender = msg.sender,
            text = msg.text,
            keyword = keyword,
            isGroup = msg.isGroup,
        )
    }
}

class HistoryRepository(private val context: Context) {
    private val key = stringPreferencesKey("entries")

    val entries: Flow<List<HistoryEntry>> = context.historyStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { decode(it[key]) }

    suspend fun add(entry: HistoryEntry) {
        context.historyStore.edit { p -> p[key] = encode((listOf(entry) + decode(p[key])).take(MAX_ENTRIES)) }
    }

    suspend fun clear() {
        context.historyStore.edit { it.remove(key) }
    }

    private companion object {
        const val MAX_ENTRIES = 300

        fun encode(list: List<HistoryEntry>): String {
            val array = JSONArray()
            for (e in list) {
                val o = JSONObject()
                o.put("id", e.id)
                o.put("t", e.time)
                o.put("p", e.packageName)
                e.group?.let { o.put("g", it) }
                e.sender?.let { o.put("s", it) }
                o.put("x", e.text)
                o.put("k", e.keyword)
                o.put("ig", e.isGroup)
                array.put(o)
            }
            return array.toString()
        }

        fun decode(value: String?): List<HistoryEntry> {
            if (value.isNullOrEmpty()) return emptyList()
            return try {
                val array = JSONArray(value)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    HistoryEntry(
                        id = o.optString("id").ifEmpty { "h$i" },
                        time = o.optLong("t"),
                        packageName = o.optString("p", "com.whatsapp"),
                        group = if (o.has("g")) o.getString("g") else null,
                        sender = if (o.has("s")) o.getString("s") else null,
                        text = o.optString("x"),
                        keyword = o.optString("k"),
                        isGroup = o.optBoolean("ig", true),
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
