package az.novbe.radar.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Azərbaycan dilində tarix və bildiriş mətnləri. */
object Az {
    private val MONTH_SHORT = arrayOf("Yan", "Fev", "Mar", "Apr", "May", "İyn", "İyl", "Avq", "Sen", "Okt", "Noy", "Dek")
    private val MONTH_LONG = arrayOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun", "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr"
    )

    fun dow(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "Bazar ertəsi"
        DayOfWeek.TUESDAY -> "Çərşənbə axşamı"
        DayOfWeek.WEDNESDAY -> "Çərşənbə"
        DayOfWeek.THURSDAY -> "Cümə axşamı"
        DayOfWeek.FRIDAY -> "Cümə"
        DayOfWeek.SATURDAY -> "Şənbə"
        DayOfWeek.SUNDAY -> "Bazar"
    }

    fun monthShort(m: Int): String = MONTH_SHORT[(m - 1).coerceIn(0, 11)]

    fun monthLong(ym: YearMonth): String = "${MONTH_LONG[ym.monthValue - 1]} ${ym.year}"

    /** "16 Okt (Cümə)" */
    fun dateLabel(d: LocalDate): String = "${d.dayOfMonth} ${monthShort(d.monthValue)} (${dow(d.dayOfWeek)})"

    /** "🔔 16 Okt (Cümə) · E2 Big Wheel TUR · 16:00–00:00 · ortadadır (KA)" */
    fun notifLine(date: LocalDate?, dateRaw: String, table: String, time: String, initials: String): String {
        val who = if (initials.isNotBlank()) " ($initials)" else ""
        val day = date?.let { dateLabel(it) } ?: dateRaw
        val t = table.ifBlank { "Masa bilinmir" }
        return "🔔 $day · $t · $time · ortadadır$who"
    }

    fun notifLine(t: Tracked): String = notifLine(t.localDate, t.date, t.table, t.time, t.initials)

    fun hm(minutes: Int): String = TimeRange.fmt(minutes)
}
