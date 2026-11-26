package com.wpradar.data

import android.content.Context
import com.wpradar.core.JournalDeduper
import com.wpradar.core.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Radarın gördüyü WhatsApp mesajı və qərarı. */
data class JournalEntry(
    val id: Long,
    val time: Long,
    val packageName: String,
    val group: String?,
    val sender: String?,
    /** Yoxlanmayan şəxsi çatlarda mətn saxlanmır. */
    val text: String?,
    val isGroup: Boolean,
    val verdict: Verdict,
    val keyword: String?,
)

/**
 * Jurnal yaddaşda saxlanır, fayla isə gecikmə ilə yazılır.
 * Aktiv qrupda hər mesajda diskə yazmamaq üçün belə edilib.
 */
class JournalStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "journal_v1.json")
    private val deduper = JournalDeduper()
    private val ids = AtomicLong(System.currentTimeMillis() * 1000)
    private val _entries = MutableStateFlow(load())
    private var saveJob: Job? = null

    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    fun nextId(): Long = ids.incrementAndGet()

    /** Cütlər: (mesaj açarı, yazı). Eyni mesajın eyni qərarı ikinci dəfə yazılmır. */
    @Synchronized
    fun record(items: List<Pair<String, JournalEntry>>) {
        val fresh = items.filter { (key, entry) -> deduper.shouldLog(key, entry.verdict) }.map { it.second }
        if (fresh.isEmpty()) return
        _entries.value = (fresh.asReversed() + _entries.value).take(MAX_ENTRIES)
        scheduleSave()
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            save(_entries.value)
        }
    }

    private fun save(list: List<JournalEntry>) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encode(list))
            if (!tmp.renameTo(file)) {
                file.writeText(encode(list))
                tmp.delete()
            }
        }
    }

    private fun load(): List<JournalEntry> =
        runCatching { if (file.exists()) decode(file.readText()) else emptyList() }.getOrDefault(emptyList())

    private companion object {
        const val MAX_ENTRIES = 150
        const val SAVE_DELAY_MS = 3_000L

        fun encode(list: List<JournalEntry>): String {
            val array = JSONArray()
            for (e in list) {
                val o = JSONObject()
                o.put("id", e.id)
                o.put("t", e.time)
                o.put("p", e.packageName)
                e.group?.let { o.put("g", it) }
                e.sender?.let { o.put("s", it) }
                e.text?.let { o.put("x", it) }
                o.put("ig", e.isGroup)
                o.put("v", e.verdict.name)
                e.keyword?.let { o.put("k", it) }
                array.put(o)
            }
            return array.toString()
        }

        fun decode(value: String): List<JournalEntry> {
            val array = JSONArray(value)
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val verdict = runCatching { Verdict.valueOf(o.optString("v")) }.getOrNull()
                    ?: return@mapNotNull null
                JournalEntry(
                    id = o.optLong("id", i.toLong()),
                    time = o.optLong("t"),
                    packageName = o.optString("p", "com.whatsapp"),
                    group = if (o.has("g")) o.getString("g") else null,
                    sender = if (o.has("s")) o.getString("s") else null,
                    text = if (o.has("x")) o.getString("x") else null,
                    isGroup = o.optBoolean("ig", true),
                    verdict = verdict,
                    keyword = if (o.has("k")) o.getString("k") else null,
                )
            }
        }
    }
}
