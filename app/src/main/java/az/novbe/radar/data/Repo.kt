package az.novbe.radar.data

import android.content.Context
import az.novbe.radar.core.DayStatus
import az.novbe.radar.core.Tracked
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

enum class LogType { OK, SKIP, ERROR, FOUND, PASSIVE, INFO, TEST }

data class LogEntry(val id: Long, val time: Long, val type: LogType, val msg: String, val count: Int = 1)

/** Tapılan növbələr, jurnal və qrafik (SCHEDULED günlər) — hamısı telefonun daxili yaddaşında. */
object Repo {
    private lateinit var dir: File
    private val lock = Any()

    private val _found = MutableStateFlow<List<Tracked>>(emptyList())
    val found: StateFlow<List<Tracked>> = _found

    private val _journal = MutableStateFlow<List<LogEntry>>(emptyList())
    val journal: StateFlow<List<LogEntry>> = _journal

    private var schedule: Map<String, String> = emptyMap()
    var scheduleUpdatedAt: Long = 0L
        private set

    private const val MAX_LOG = 400
    private var lastId = 0L

    fun init(ctx: Context) {
        dir = ctx.filesDir
        _found.value = runCatching { loadFound() }.getOrDefault(emptyList())
        _journal.value = runCatching { loadJournal() }.getOrDefault(emptyList()).distinctBy { it.id }
        lastId = _journal.value.maxOfOrNull { it.id } ?: 0L
        runCatching { loadSchedule() }
    }

    // ---------- Tapılanlar ----------

    fun setFound(list: List<Tracked>) {
        synchronized(lock) {
            _found.value = list
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("key", t.key).put("date", t.date).put("table", t.table).put("role", t.role)
                        .put("time", t.time).put("duration", t.duration).put("initials", t.initials)
                        .put("firstSeen", t.firstSeen).put("lastSeen", t.lastSeen).put("present", t.present)
                        .put("miss", t.miss).put("notifyCount", t.notifyCount).put("test", t.test).put("passive", t.passive)
                )
            }
            write("found.json", arr.toString())
        }
    }

    fun clearTests() = setFound(_found.value.filterNot { it.test })

    fun clearFound() = setFound(emptyList())

    private fun loadFound(): List<Tracked> {
        val txt = read("found.json") ?: return emptyList()
        val arr = JSONArray(txt)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Tracked(
                key = o.getString("key"), date = o.getString("date"), table = o.optString("table"),
                role = o.optString("role"), time = o.optString("time"), duration = o.optString("duration"),
                initials = o.optString("initials"), firstSeen = o.optLong("firstSeen"), lastSeen = o.optLong("lastSeen"),
                present = o.optBoolean("present"), miss = o.optInt("miss"), notifyCount = o.optInt("notifyCount", 1),
                test = o.optBoolean("test"), passive = o.optBoolean("passive"),
            )
        }
    }

    // ---------- Jurnal ----------

    fun log(type: LogType, msg: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val cur = _journal.value
            val last = cur.lastOrNull()
            val next = if (last != null && last.type == type && last.msg == msg && (type == LogType.SKIP || type == LogType.ERROR)) {
                cur.dropLast(1) + last.copy(time = now, count = last.count + 1)
            } else {
                val id = maxOf(lastId + 1, now * 1000).also { lastId = it }
                (cur + LogEntry(id, now, type, msg)).takeLast(MAX_LOG)
            }
            _journal.value = next
            saveJournal(next)
        }
    }

    fun clearJournal() {
        synchronized(lock) {
            _journal.value = emptyList()
            saveJournal(emptyList())
        }
    }

    private fun saveJournal(list: List<LogEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("t", e.time).put("type", e.type.name).put("msg", e.msg).put("n", e.count))
        }
        write("journal.json", arr.toString())
    }

    private fun loadJournal(): List<LogEntry> {
        val txt = read("journal.json") ?: return emptyList()
        val arr = JSONArray(txt)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LogEntry(
                o.optLong("id"), o.optLong("t"),
                runCatching { LogType.valueOf(o.optString("type")) }.getOrDefault(LogType.INFO),
                o.optString("msg"), o.optInt("n", 1),
            )
        }
    }

    // ---------- Qrafik (yalnız boş günlər filtri üçün) ----------

    fun updateSchedule(days: Map<LocalDate, DayStatus>, full: Boolean) {
        if (days.isEmpty()) return
        synchronized(lock) {
            val m = schedule.toMutableMap()
            days.forEach { (d, st) -> m[d.toString()] = if (st == DayStatus.SCHEDULED) "SCHEDULED" else "FREE" }
            val cutoff = LocalDate.now().minusDays(3).toString()
            schedule = m.filterKeys { it >= cutoff }
            if (full) scheduleUpdatedAt = System.currentTimeMillis()
            val o = JSONObject().put("updatedAt", scheduleUpdatedAt)
            val days2 = JSONObject()
            schedule.forEach { (k, v) -> days2.put(k, v) }
            o.put("days", days2)
            write("schedule.json", o.toString())
        }
    }

    fun scheduledDays(): Set<String> = schedule.filterValues { it == "SCHEDULED" }.keys

    fun knownDays(): Int = schedule.size

    private fun loadSchedule() {
        val txt = read("schedule.json") ?: return
        val o = JSONObject(txt)
        scheduleUpdatedAt = o.optLong("updatedAt")
        val days = o.optJSONObject("days") ?: return
        val m = HashMap<String, String>()
        days.keys().forEach { k -> m[k] = days.optString(k) }
        schedule = m
    }

    // ---------- Fayl ----------

    private fun write(name: String, text: String) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            val f = File(dir, name)
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
        }
    }

    private fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
}
