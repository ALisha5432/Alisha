package az.novbe.radar.core

import java.time.DayOfWeek
import java.time.YearMonth
import kotlin.math.abs

enum class Screen { AVAILABLE, SCHEDULE, LOGIN, UNKNOWN, EMPTY }

enum class DayStatus { SCHEDULED, AVAILABLE, REST, SHIFT_TIME }

/** Available Shifts-dəki bir kart. */
data class Card(
    val table: String,
    val role: String,
    val time: TimeRange,
    val timeText: String,
    val duration: String,
    val initials: String,
    val hasButton: Boolean,
    val box: Box,
) {
    val complete: Boolean get() = table.isNotBlank()
    val stitchKey: String get() = "${normKey(table)}|${time.key}"
    fun sameAs(o: Card): Boolean =
        stitchKey == o.stitchKey && (initials.isEmpty() || o.initials.isEmpty() || initials.equals(o.initials, true))

    fun richness(): Int = listOf(table, role, duration, initials).count { it.isNotEmpty() }
}

/** Siyahının ardıcıllığındakı element: tarix, kart və ya gün statusu. */
sealed class Tok {
    abstract val y: Int
}

data class DateTok(val day: Int, val dow: DayOfWeek?, override val y: Int) : Tok()
data class CardTok(val card: Card, override val y: Int) : Tok()
data class StatusTok(val status: DayStatus, val text: String, override val y: Int) : Tok()

data class Page(
    val screen: Screen,
    val month: YearMonth?,
    val monthLine: Line?,
    val tokens: List<Tok>,
    val lines: List<Line>,
    val region: Box,
) {
    val cards: List<Card> get() = tokens.filterIsInstance<CardTok>().map { it.card }

    /** Səhifənin "barmaq izi": scroll-dan sonra dəyişib-dəyişmədiyini bilmək üçün. */
    val signature: String get() = lines.joinToString("\u0001") { it.text + "@" + it.box.top }
}

fun normKey(s: String): String = s.lowercase().replace(Regex("""\s+"""), " ").trim()

object Parser {
    private val NOT_FIELDS = setOf(
        Kind.TIME_RANGE, Kind.DATE, Kind.DOW, Kind.DAYNUM, Kind.MONTH,
        Kind.TITLE_AVAILABLE, Kind.TITLE_SCHEDULE, Kind.SYMBOL, Kind.LOGIN
    )

    fun parse(tree: UiTree): Page {
        val lines = Lines.extract(tree)
        val screen = identify(tree, lines)
        val monthLine = lines.filter { it.kind == Kind.MONTH }.minByOrNull { it.box.top }
        val month = monthLine?.let { Patterns.month(it.text) }
        val region = contentRegion(tree, lines, monthLine)
        val tokens = when (screen) {
            Screen.AVAILABLE -> availableTokens(tree, lines, region)
            Screen.SCHEDULE -> scheduleTokens(tree, lines, region)
            else -> emptyList()
        }
        return Page(screen, month, monthLine, tokens, lines, region)
    }

    fun identify(tree: UiTree, lines: List<Line>): Screen {
        if (lines.isEmpty()) return if (tree.nodes.any { it.password }) Screen.LOGIN else Screen.EMPTY
        val h = tree.height
        val topLimit = if (h > 0) (h * 0.3).toInt() else Int.MAX_VALUE
        if (lines.any { it.kind == Kind.TITLE_AVAILABLE && it.box.top <= topLimit }) return Screen.AVAILABLE
        if (lines.any { it.kind == Kind.TITLE_SCHEDULE && it.box.top <= topLimit }) return Screen.SCHEDULE
        val times = lines.count { it.kind == Kind.TIME_RANGE }
        val buttons = lines.count { it.kind == Kind.BUTTON }
        val sched = lines.count { it.kind == Kind.SCHEDULED || it.kind == Kind.AVAIL_COUNT || it.kind == Kind.REST_DAY }
        val login = tree.nodes.any { it.password } || lines.any { it.kind == Kind.LOGIN }
        if (login && times == 0 && sched == 0) return Screen.LOGIN
        if (buttons > 0 && times > 0) return Screen.AVAILABLE
        if (sched > 0) return Screen.SCHEDULE
        return Screen.UNKNOWN
    }

    private fun contentRegion(tree: UiTree, lines: List<Line>, monthLine: Line?): Box {
        val w = tree.width
        val h = tree.height
        val titleBottom = lines.filter { it.kind == Kind.TITLE_AVAILABLE || it.kind == Kind.TITLE_SCHEDULE }
            .filter { h <= 0 || it.box.top <= h * 0.3 }
            .maxOfOrNull { it.box.bottom }
        val headerBottom = listOfNotNull(monthLine?.box?.bottom, titleBottom).maxOrNull() ?: 0
        val scroll = tree.nodes
            .filter { it.scrollable && !it.box.isEmpty && it.box.bottom > headerBottom + 20 }
            .maxByOrNull { it.box.area }
        val top = maxOf(headerBottom, scroll?.box?.top ?: 0)
        val bottom = scroll?.box?.bottom ?: h
        return Box(0, top, w, if (bottom > top) bottom else h)
    }

    private fun inRegion(lines: List<Line>, region: Box): List<Line> =
        lines.filter { it.box.cy >= region.top - 2 && it.box.cy <= region.bottom + 2 }

    private class Dates(val toks: List<DateTok>, val used: Set<Int>)

    private fun dateTokens(lines: List<Line>, screenW: Int): Dates {
        val used = HashSet<Int>()
        val out = ArrayList<DateTok>()
        for (l in lines) if (l.kind == Kind.DATE) {
            val (d, n) = Patterns.date(l.text) ?: continue
            out += DateTok(n, d, l.box.top)
            used += l.order
        }
        val dows = lines.filter { it.kind == Kind.DOW }
        for (n in lines.filter { it.kind == Kind.DAYNUM }) {
            val w = dows.filter { it.order !in used }.filter { d ->
                d.nodeId == n.nodeId || (
                    abs(d.box.cx - n.box.cx) <= maxOf(d.box.width, n.box.width, 1) &&
                        d.box.cy <= n.box.cy &&
                        n.box.top - d.box.bottom <= maxOf(n.box.height, d.box.height, 1) * 2
                    )
            }.minByOrNull { abs(n.box.top - it.box.bottom) }
            if (w != null) {
                out += DateTok(n.text.trim().toInt(), Patterns.dow(w.text), minOf(w.box.top, n.box.top))
                used += w.order
                used += n.order
            } else if (screenW > 0 && n.box.cx < screenW * 0.22) {
                out += DateTok(n.text.trim().toInt(), null, n.box.top)
                used += n.order
            }
        }
        return Dates(out, used)
    }

    private fun sortTokens(t: List<Tok>): List<Tok> =
        t.sortedWith(compareBy<Tok> { it.y }.thenBy { if (it is DateTok) 0 else 1 })

    private fun availableTokens(tree: UiTree, all: List<Line>, region: Box): List<Tok> {
        val lines = inRegion(all, region)
        val dates = dateTokens(lines, tree.width)
        val anchors = lines.filter { it.kind == Kind.TIME_RANGE }.sortedBy { it.box.cy }
        val spacing = medianSpacing(anchors)
        val cards = anchors.mapIndexed { i, a ->
            val band = bandScope(lines, anchors, i, spacing)
            val scope = structuralScope(tree, lines, anchors, a, region) ?: band
            CardTok(buildCard(a, scope, band, dates.used), a.box.cy)
        }
        return sortTokens(dates.toks + cards)
    }

    private fun medianSpacing(anchors: List<Line>): Int? {
        if (anchors.size < 2) return null
        val d = anchors.zipWithNext { a, b -> b.box.cy - a.box.cy }.filter { it > 0 }.sorted()
        return d.getOrNull(d.size / 2)
    }

    /** Kartın konteyneri: yalnız bu saatı (anchor) saxlayan ən böyük valideyn. */
    private fun structuralScope(tree: UiTree, lines: List<Line>, anchors: List<Line>, a: Line, region: Box): List<Line>? {
        var best = -1
        var cur = a.nodeId
        val maxH = maxOf(region.height, 1) * 0.45
        while (cur >= 0) {
            val node = tree[cur]
            if (node.box.height > maxH) break
            val sub = tree.subtreeIds(cur).toHashSet()
            if (anchors.count { it.nodeId in sub } > 1) break
            best = cur
            cur = node.parent
        }
        if (best < 0) return null
        val sub = tree.subtreeIds(best).toHashSet()
        val scoped = lines.filter { it.nodeId in sub }
        return if (scoped.size >= 2) scoped else null
    }

    /** Konteyner yoxdursa: qonşu saatlar arasındakı orta xətlərlə bölünmüş zolaq. */
    private fun bandScope(lines: List<Line>, anchors: List<Line>, i: Int, spacing: Int?): List<Line> {
        val a = anchors[i]
        val half = (spacing ?: (maxOf(a.box.height, 10) * 8)) / 2
        val top = if (i > 0) (anchors[i - 1].box.cy + a.box.cy) / 2 else a.box.cy - half
        val bottom = if (i < anchors.size - 1) (a.box.cy + anchors[i + 1].box.cy) / 2 else a.box.cy + half
        return lines.filter { it.box.cy > top && it.box.cy <= bottom }
    }

    private fun buildCard(a: Line, scope: List<Line>, band: List<Line>, usedDates: Set<Int>): Card {
        val fields = scope.filter { it.order != a.order && it.order !in usedDates && it.kind !in NOT_FIELDS }
        // Konteyner kartın hamısını əhatə etməyə bilər (məs. avatar qonşu node-dadır) — çatışmayanı zolaqdan götür.
        val extra = band.filter { it.order != a.order && it.order !in usedDates && it.kind !in NOT_FIELDS && it !in fields }
        val initials = fields.filter { it.kind == Kind.INITIALS }.minByOrNull { it.box.top }
            ?: extra.filter { it.kind == Kind.INITIALS }.minByOrNull { it.box.top }
        val duration = fields.filter { it.kind == Kind.DURATION }.minByOrNull { abs(it.box.cy - a.box.cy) }
            ?: extra.filter { it.kind == Kind.DURATION }.minByOrNull { abs(it.box.cy - a.box.cy) }
        val hasButton = fields.any { it.kind == Kind.BUTTON } || extra.any { it.kind == Kind.BUTTON }
        val textual = fields.filter {
            it.kind == Kind.OTHER || it.kind == Kind.ROLE || (it.kind == Kind.INITIALS && it !== initials)
        }.ifEmpty { extra.filter { it.kind == Kind.OTHER || it.kind == Kind.ROLE } }
        val roleLine = textual.firstOrNull { it.kind == Kind.ROLE }
        val tableCands = textual.filter { it !== roleLine }
        val left = tableCands.filter { it.box.right <= a.box.left + 8 }
        val tableLine = (left.ifEmpty { tableCands })
            .minWithOrNull(compareBy<Line> { abs(it.box.cy - a.box.cy) }.thenBy { it.box.top })
        val role = roleLine?.text ?: tableCands.firstOrNull {
            it !== tableLine && it.box.top >= (tableLine?.box?.top ?: Int.MIN_VALUE) &&
                it.text == it.text.uppercase() && it.text.any { c -> c.isLetter() }
        }?.text ?: ""
        val box = scope.fold(a.box) { acc, l -> acc.union(l.box) }
        return Card(
            table = tableLine?.text ?: "",
            role = role,
            time = TimeRange.parse(a.text)!!,
            timeText = a.text,
            duration = duration?.text ?: "",
            initials = initials?.text ?: "",
            hasButton = hasButton,
            box = box,
        )
    }

    private fun scheduleTokens(tree: UiTree, all: List<Line>, region: Box): List<Tok> {
        val lines = inRegion(all, region)
        val dates = dateTokens(lines, tree.width)
        val st = lines.mapNotNull { l ->
            when (l.kind) {
                Kind.SCHEDULED -> StatusTok(DayStatus.SCHEDULED, "SCHEDULED", l.box.cy)
                Kind.AVAIL_COUNT -> StatusTok(DayStatus.AVAILABLE, l.text.uppercase(), l.box.cy)
                Kind.REST_DAY -> StatusTok(DayStatus.REST, "REST DAY", l.box.cy)
                Kind.TIME_RANGE -> StatusTok(DayStatus.SHIFT_TIME, TimeRange.parse(l.text)!!.key, l.box.cy)
                else -> null
            }
        }
        return sortTokens(dates.toks + st)
    }
}
