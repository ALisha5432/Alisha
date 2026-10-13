package az.novbe.radar.core

import java.time.DayOfWeek
import java.time.YearMonth

/** Bir mətn sətrinin nə olduğu. */
enum class Kind {
    TITLE_AVAILABLE, TITLE_SCHEDULE, MONTH, TIME_RANGE, DATE, DOW, DAYNUM, DURATION,
    INITIALS, ROLE, BUTTON, SCHEDULED, AVAIL_COUNT, REST_DAY, LOGIN, SYMBOL, OTHER
}

/** Növbə saatları, dəqiqə ilə (0..1439). Gecə yarısı = 0. */
data class TimeRange(val start: Int, val end: Int) {
    val key: String get() = "${fmt(start)}-${fmt(end)}"
    val pretty: String get() = "${fmt(start)}–${fmt(end)}"

    companion object {
        fun fmt(minutes: Int): String {
            val m = ((minutes % 1440) + 1440) % 1440
            return (m / 60).toString().padStart(2, '0') + ":" + (m % 60).toString().padStart(2, '0')
        }

        fun parse(s: String): TimeRange? {
            val m = Patterns.TIME.find(s) ?: return null
            val g = m.groupValues
            val a = toMinutes(g[1].toInt(), g[2].toInt(), g[3]) ?: return null
            val b = toMinutes(g[4].toInt(), g[5].toInt(), g[6]) ?: return null
            return TimeRange(a, b)
        }

        private fun toMinutes(h: Int, min: Int, ampm: String): Int? {
            if (min !in 0..59) return null
            val ap = ampm.lowercase().filter { it == 'a' || it == 'p' }
            val hh = if (ap.isNotEmpty()) {
                if (h !in 1..12) return null
                h % 12 + if (ap == "p") 12 else 0
            } else {
                if (h !in 0..24) return null
                h
            }
            return (hh * 60 + min) % 1440
        }
    }
}

object Patterns {
    val TIME = Regex(
        """(\d{1,2})\s*[:.]\s*(\d{2})\s*([AaPp]\.?\s?[Mm]\.?)?\s*[-–—~]\s*(\d{1,2})\s*[:.]\s*(\d{2})\s*([AaPp]\.?\s?[Mm]\.?)?"""
    )
    private val TIME_FULL = Regex("""^\s*""" + TIME.pattern + """\s*$""")
    private val MONTH_YEAR = Regex("""^([\p{L}.]+)\s+(\d{4})$""")
    private val DATE_DOW_NUM = Regex("""^([\p{L}]+)\.?,?\s+(\d{1,2})$""")
    private val DATE_NUM_DOW = Regex("""^(\d{1,2})\.?\s+([\p{L}]+)\.?$""")
    private val DAYNUM = Regex("""^\d{1,2}$""")
    private val DURATION = Regex(
        """^\d+(?:[.,]\d+)?\s*(?:h|hr|hrs|hours?|saat|ч)(?:\s*\d+\s*(?:m|min|mins))?$|^\d+\s*(?:m|min|mins)$""",
        RegexOption.IGNORE_CASE
    )
    private val INITIALS = Regex("""^[A-ZƏĞİŞÇÖÜ]{2,3}$""")
    private val BUTTON = Regex("""^(respond|responded|pending|requested|applied|cancel request|withdraw)$""", RegexOption.IGNORE_CASE)
    private val SCHEDULED = Regex("""^scheduled$""", RegexOption.IGNORE_CASE)
    val AVAIL_COUNT = Regex("""^(\d+)\s+shifts?\s+available\b.*""", RegexOption.IGNORE_CASE)
    private val REST_DAY = Regex("""^rest\s*day\b.*""", RegexOption.IGNORE_CASE)
    private val TITLE_AVAILABLE = Regex("""^available\s+shifts?$""", RegexOption.IGNORE_CASE)
    private val TITLE_SCHEDULE = Regex("""^my\s+schedule$""", RegexOption.IGNORE_CASE)
    private val LOGIN = Regex(
        """^(log\s*in|sign\s*in|login|password|username|forgot\s+(your\s+)?password\??|(log|sign)\s*in\s+with\s+.+|continue\s+with\s+.+|enter\s+(your\s+)?(password|email|pin|code)|verification\s+code|session\s+expired.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val SYMBOL = Regex("""^[<>‹›«»+→←•·|*\-]{1,2}$""")

    val ROLES = setOf(
        "DEALER", "SHUFFLER", "GAME PRESENTER", "PRESENTER", "HOST", "CROUPIER", "SUPERVISOR",
        "SHIFT SUPERVISOR", "SHIFT MANAGER", "PIT BOSS", "INSPECTOR", "TRAINER", "TRAINEE",
        "FLOOR MANAGER", "CAMERA OPERATOR", "OPERATOR", "RUNNER", "STUDIO ASSISTANT"
    )

    private val MONTHS: Map<String, Int> = buildMap {
        val en = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
        en.forEachIndexed { i, s -> put(s, i + 1); put(s.take(3), i + 1) }
        put("sept", 9)
        listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr")
            .forEachIndexed { i, s -> put(s, i + 1) }
        listOf("ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık")
            .forEachIndexed { i, s -> put(s, i + 1) }
        listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
            .forEachIndexed { i, s -> put(s, i + 1) }
    }

    private val DOWS: Map<String, DayOfWeek> = buildMap {
        fun p(d: DayOfWeek, vararg names: String) = names.forEach { put(it, d) }
        p(DayOfWeek.MONDAY, "mon", "monday", "пн", "pzt")
        p(DayOfWeek.TUESDAY, "tue", "tues", "tuesday", "вт", "sal")
        p(DayOfWeek.WEDNESDAY, "wed", "weds", "wednesday", "ср", "çar")
        p(DayOfWeek.THURSDAY, "thu", "thur", "thurs", "thursday", "чт", "per")
        p(DayOfWeek.FRIDAY, "fri", "friday", "пт", "cum")
        p(DayOfWeek.SATURDAY, "sat", "saturday", "сб", "cmt")
        p(DayOfWeek.SUNDAY, "sun", "sunday", "вс", "paz")
    }

    fun isPureTime(s: String): Boolean = TIME_FULL.matches(s) && TimeRange.parse(s) != null

    fun month(s: String): YearMonth? {
        val m = MONTH_YEAR.matchEntire(s.trim()) ?: return null
        val mon = MONTHS[m.groupValues[1].lowercase().trimEnd('.')] ?: return null
        val y = m.groupValues[2].toInt()
        if (y !in 2000..2100) return null
        return YearMonth.of(y, mon)
    }

    fun dow(s: String): DayOfWeek? = DOWS[s.trim().lowercase().trimEnd('.', ',')]

    /** "Mon 19", "19 Mon" kimi birləşik tarix. */
    fun date(s: String): Pair<DayOfWeek, Int>? {
        val t = s.trim()
        DATE_DOW_NUM.matchEntire(t)?.let { m ->
            val d = dow(m.groupValues[1]) ?: return@let
            val n = m.groupValues[2].toInt()
            if (n in 1..31) return d to n
        }
        DATE_NUM_DOW.matchEntire(t)?.let { m ->
            val d = dow(m.groupValues[2]) ?: return@let
            val n = m.groupValues[1].toInt()
            if (n in 1..31) return d to n
        }
        return null
    }

    fun classify(s: String): Kind {
        val t = s.trim()
        return when {
            t.isEmpty() -> Kind.OTHER
            TITLE_AVAILABLE.matches(t) -> Kind.TITLE_AVAILABLE
            TITLE_SCHEDULE.matches(t) -> Kind.TITLE_SCHEDULE
            month(t) != null -> Kind.MONTH
            isPureTime(t) -> Kind.TIME_RANGE
            SCHEDULED.matches(t) -> Kind.SCHEDULED
            AVAIL_COUNT.matches(t) -> Kind.AVAIL_COUNT
            REST_DAY.matches(t) -> Kind.REST_DAY
            BUTTON.matches(t) -> Kind.BUTTON
            LOGIN.matches(t) -> Kind.LOGIN
            dow(t) != null -> Kind.DOW
            DAYNUM.matches(t) -> if (t.toInt() in 1..31) Kind.DAYNUM else Kind.OTHER
            date(t) != null -> Kind.DATE
            DURATION.matches(t) -> Kind.DURATION
            t.uppercase() in ROLES -> Kind.ROLE
            INITIALS.matches(t) -> Kind.INITIALS
            SYMBOL.matches(t) -> Kind.SYMBOL
            else -> Kind.OTHER
        }
    }
}

/** Avtomatik toxunuşdan qoruma: bu sözlər olan heç nəyə basılmır. */
object Safety {
    private val FORBIDDEN = Regex(
        """\b(respond(ed)?|accept|apply|confirm|submit|request(ed)?|take|pick\s?up|claim|swap|trade|drop|give\s?away|cancel|delete|remove|decline|reject|approve|yes|ok|send|book|sign\s?out|log\s?out|withdraw)\b""",
        RegexOption.IGNORE_CASE
    )

    fun isForbidden(text: String): Boolean = FORBIDDEN.containsMatchIn(text)
}
