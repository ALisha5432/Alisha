package az.novbe.radar.core

import java.time.LocalDate
import java.time.YearMonth

/** Tarixi müəyyən edilmiş növbə. */
data class Shift(
    val date: LocalDate,
    val table: String,
    val role: String,
    val time: TimeRange,
    val duration: String,
    val initials: String,
) {
    /** Təkrar bildirişin qarşısını alan açar: tarix + masa + saat + inisial. */
    val key: String get() = "$date|${normKey(table)}|${time.key}|${initials.uppercase()}"
}

object Assemble {
    class Result(val shifts: List<Shift>, val undated: Int, val dowMismatch: Int)

    /** Tokenləri gəzir: hər kart ondan əvvəlki son tarixə aiddir. */
    fun shifts(tokens: List<Tok>, month: YearMonth): Result {
        val out = ArrayList<Shift>()
        var cur: LocalDate? = null
        var ym = month
        var prevDay = 0
        var undated = 0
        var mismatch = 0
        for (t in tokens) when (t) {
            is DateTok -> {
                if (prevDay != 0 && t.day < prevDay) ym = ym.plusMonths(1)
                prevDay = t.day
                val r = resolve(ym, t)
                cur = r?.first
                if (r == null || r.second) mismatch++
                if (r != null) ym = YearMonth.from(r.first)
            }
            is CardTok -> {
                val d = cur
                if (d == null) {
                    undated++
                } else {
                    val c = t.card
                    out += Shift(d, c.table, c.role, c.time, c.duration, c.initials)
                }
            }
            is StatusTok -> Unit
        }
        return Result(out, undated, mismatch)
    }

    /** Gün nömrəsini ayla birləşdirir; həftə günü uyğun gəlmirsə qonşu ayları yoxlayır. */
    private fun resolve(ym: YearMonth, t: DateTok): Pair<LocalDate, Boolean>? {
        for (cand in listOf(ym, ym.plusMonths(1), ym.minusMonths(1))) {
            if (t.day > cand.lengthOfMonth()) continue
            val d = cand.atDay(t.day)
            if (t.dow == null || d.dayOfWeek == t.dow) return d to (cand != ym)
        }
        return if (t.day <= ym.lengthOfMonth()) ym.atDay(t.day) to true else null
    }

    /** My Schedule: hansı günlərdə işləyirəm (SCHEDULED), hansılar boşdur. */
    fun schedule(tokens: List<Tok>, month: YearMonth): Map<LocalDate, DayStatus> {
        val out = LinkedHashMap<LocalDate, DayStatus>()
        var cur: LocalDate? = null
        var ym = month
        var prevDay = 0
        for (t in tokens) when (t) {
            is DateTok -> {
                if (prevDay != 0 && t.day < prevDay) ym = ym.plusMonths(1)
                prevDay = t.day
                cur = resolve(ym, t)?.first
                cur?.let { ym = YearMonth.from(it) }
            }
            is StatusTok -> {
                val d = cur ?: continue
                when (t.status) {
                    DayStatus.SCHEDULED -> out[d] = DayStatus.SCHEDULED
                    DayStatus.AVAILABLE, DayStatus.REST -> if (out[d] == null) out[d] = t.status
                    DayStatus.SHIFT_TIME -> Unit
                }
            }
            is CardTok -> Unit
        }
        return out
    }
}

object ShiftFilter {
    fun matches(time: TimeRange, startMin: Int, endMin: Int): Boolean =
        time.start == ((startMin % 1440) + 1440) % 1440 && time.end == ((endMin % 1440) + 1440) % 1440
}

/** "Tapılanlar" siyahısındakı növbə və onun vəziyyəti. */
data class Tracked(
    val key: String,
    val date: String,
    val table: String,
    val role: String,
    val time: String,
    val duration: String,
    val initials: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val present: Boolean,
    val miss: Int,
    val notifyCount: Int,
    val test: Boolean = false,
    val passive: Boolean = false,
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    companion object {
        fun of(s: Shift, now: Long, passive: Boolean = false, test: Boolean = false) = Tracked(
            key = (if (test) "TEST|" else "") + s.key,
            date = s.date.toString(),
            table = s.table,
            role = s.role,
            time = s.time.pretty,
            duration = s.duration,
            initials = s.initials,
            firstSeen = now,
            lastSeen = now,
            present = true,
            miss = 0,
            notifyCount = 1,
            test = test,
            passive = passive,
        )
    }
}

object Tracker {
    /** Bu qədər ardıcıl tam skanda görünməsə, növbə "götürülüb" sayılır. */
    const val MISSES_TO_GONE = 2

    class Result(val list: List<Tracked>, val newOnes: List<Tracked>, val gone: List<Tracked>)

    fun applyFullScan(
        current: List<Tracked>,
        matches: List<Shift>,
        scannedMonths: Set<YearMonth>,
        now: Long,
        today: LocalDate,
    ): Result = apply(current, matches, scannedMonths, now, today, passive = false)

    fun applyPassive(current: List<Tracked>, matches: List<Shift>, now: Long, today: LocalDate): Result =
        apply(current, matches, emptySet(), now, today, passive = true)

    private fun apply(
        current: List<Tracked>,
        matches: List<Shift>,
        scannedMonths: Set<YearMonth>,
        now: Long,
        today: LocalDate,
        passive: Boolean,
    ): Result {
        val byKey = LinkedHashMap<String, Tracked>()
        current.forEach { byKey[it.key] = it }
        val newOnes = ArrayList<Tracked>()
        val gone = ArrayList<Tracked>()
        val seen = matches.distinctBy { it.key }
        val seenKeys = seen.map { it.key }.toHashSet()
        for (s in seen) {
            val e = byKey[s.key]
            when {
                e == null -> Tracked.of(s, now, passive).also { byKey[s.key] = it; newOnes += it }
                !e.present -> e.copy(
                    present = true, miss = 0, lastSeen = now, firstSeen = now,
                    notifyCount = e.notifyCount + 1, passive = passive,
                    role = s.role.ifEmpty { e.role }, duration = s.duration.ifEmpty { e.duration },
                ).also { byKey[s.key] = it; newOnes += it }
                else -> byKey[s.key] = e.copy(lastSeen = now, miss = 0)
            }
        }
        if (!passive) {
            for ((k, e) in byKey.entries.toList()) {
                if (!e.present || e.test || k in seenKeys) continue
                val d = e.localDate ?: continue
                if (YearMonth.from(d) !in scannedMonths) continue
                val miss = e.miss + 1
                if (miss >= MISSES_TO_GONE) {
                    val g = e.copy(present = false, miss = miss)
                    byKey[k] = g
                    gone += g
                } else {
                    byKey[k] = e.copy(miss = miss)
                }
            }
        }
        // Keçmiş günlər: aktiv deyil; bir həftədən köhnələr silinir.
        val keepFrom = today.minusDays(7)
        val list = byKey.values.mapNotNull { e ->
            val d = e.localDate
            when {
                d == null -> e
                d.isBefore(keepFrom) -> null
                d.isBefore(today) && e.present -> e.copy(present = false)
                else -> e
            }
        }.sortedWith(compareBy<Tracked> { it.date }.thenBy { it.time }.thenBy { it.table })
        return Result(list.takeLast(300), newOnes, gone)
    }
}
