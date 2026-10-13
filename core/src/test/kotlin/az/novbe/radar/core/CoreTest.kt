package az.novbe.radar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class TimeRangeTest {
    @Test fun parsesCommonForms() {
        assertEquals(TimeRange(960, 0), TimeRange.parse("16:00 - 0:00"))
        assertEquals(TimeRange(960, 0), TimeRange.parse("16:00-00:00"))
        assertEquals(TimeRange(960, 0), TimeRange.parse("16:00 – 24:00"))
        assertEquals(TimeRange(480, 960), TimeRange.parse("8:00 - 16:00"))
        assertEquals(TimeRange(960, 0), TimeRange.parse("4:00 PM - 12:00 AM"))
        assertNull(TimeRange.parse("8h"))
        assertEquals("16:00–00:00", TimeRange(960, 0).pretty)
    }

    @Test fun filterOnlyExactHours() {
        val start = 16 * 60
        val end = 0
        assertTrue(ShiftFilter.matches(TimeRange.parse("16:00 - 0:00")!!, start, end))
        assertTrue(ShiftFilter.matches(TimeRange.parse("16:00-00:00")!!, start, end))
        assertFalse(ShiftFilter.matches(TimeRange.parse("8:00 - 16:00")!!, start, end))
        assertFalse(ShiftFilter.matches(TimeRange.parse("16:00 - 23:00")!!, start, end))
        assertFalse(ShiftFilter.matches(TimeRange.parse("10:00 - 12:00")!!, start, end))
    }
}

class ClassifyTest {
    @Test fun kinds() {
        assertEquals(Kind.TITLE_AVAILABLE, Patterns.classify("Available Shifts"))
        assertEquals(Kind.TITLE_SCHEDULE, Patterns.classify("My Schedule"))
        assertEquals(Kind.MONTH, Patterns.classify("October 2026"))
        assertEquals(Kind.TIME_RANGE, Patterns.classify("16:00 - 0:00"))
        assertEquals(Kind.DOW, Patterns.classify("Mon"))
        assertEquals(Kind.DAYNUM, Patterns.classify("19"))
        assertEquals(Kind.DATE, Patterns.classify("Mon 19"))
        assertEquals(Kind.DURATION, Patterns.classify("8h"))
        assertEquals(Kind.INITIALS, Patterns.classify("KA"))
        assertEquals(Kind.ROLE, Patterns.classify("DEALER"))
        assertEquals(Kind.BUTTON, Patterns.classify("RESPOND"))
        assertEquals(Kind.SCHEDULED, Patterns.classify("SCHEDULED"))
        assertEquals(Kind.AVAIL_COUNT, Patterns.classify("4 SHIFTS AVAILABLE"))
        assertEquals(Kind.AVAIL_COUNT, Patterns.classify("1 SHIFT AVAILABLE"))
        assertEquals(Kind.REST_DAY, Patterns.classify("REST DAY"))
        assertEquals(Kind.OTHER, Patterns.classify("E2 Big Wheel TUR"))
        assertEquals(Kind.LOGIN, Patterns.classify("Sign in"))
    }

    @Test fun safety() {
        assertTrue(Safety.isForbidden("RESPOND"))
        assertTrue(Safety.isForbidden("Accept shift"))
        assertFalse(Safety.isForbidden("4 SHIFTS AVAILABLE"))
        assertFalse(Safety.isForbidden("Available Shifts"))
        assertFalse(Safety.isForbidden("October 2026"))
    }
}

class ParserTest {
    private fun check(tree: UiTree) {
        val page = Parser.parse(tree)
        assertEquals(Screen.AVAILABLE, page.screen)
        assertEquals(YearMonth.of(2026, 10), page.month)
        val cards = page.cards
        assertEquals(6, cards.size)
        val e2 = cards.first { it.table == "E2 Big Wheel TUR" }
        assertEquals("KA", e2.initials)
        assertEquals("DEALER", e2.role)
        assertEquals("8h", e2.duration)
        assertEquals(TimeRange(960, 0), e2.time)
        assertTrue(e2.hasButton)
        assertEquals("MC", cards.first { it.table.startsWith("A2 Widet") }.initials)
        assertEquals("LB", cards.first { it.table.startsWith("D1") }.initials)
        assertEquals("ND", cards.first { it.time == TimeRange(480, 960) && it.table.startsWith("D2") }.initials)

        // Tarix yalnız günün ilk kartının yanındadır: ondan əvvəlki kartlar əvvəlki günə aiddir.
        val acc = mutableListOf<Tok>(DateTok(16, DayOfWeek.FRIDAY, 0))
        Stitch.forward(acc, page.tokens)
        val res = Assemble.shifts(acc, page.month!!)
        assertEquals(0, res.undated)
        assertEquals(0, res.dowMismatch)
        val byTable = res.shifts.groupBy { it.table }
        assertEquals(LocalDate.of(2026, 10, 16), byTable["E2 Big Wheel TUR"]!!.single().date)
        assertEquals(LocalDate.of(2026, 10, 19), byTable["A2 Widet...ablex BJ"]!!.single().date)
        val d2 = byTable["D2 Sample TUR RL"]!!.sortedBy { it.date }
        assertEquals(LocalDate.of(2026, 10, 24), d2[0].date)
        assertEquals(LocalDate.of(2026, 10, 25), d2[1].date)
    }

    @Test fun flatLayout() = check(Fixtures.availableFlat())

    @Test fun nestedLayout() = check(Fixtures.availableNested())

    @Test fun mergedFlutterLikeLabels() {
        val b = UiTreeBuilder("evo", Fixtures.W, Fixtures.H)
        val root = b.add(-1, "FrameLayout", null, null, null, null, Box(0, 0, 720, 1600))
        b.add(root, "View", null, null, "Available Shifts", null, Box(250, 110, 470, 150))
        b.add(root, "View", null, null, "October 2026", null, Box(270, 212, 445, 250))
        val list = b.add(root, "ScrollView", null, null, null, null, Box(0, 262, 720, 1375), scrollable = true)
        b.add(list, "View", null, null, "Fri\n16", null, Box(40, 600, 90, 680))
        b.add(list, "View", null, null, "KA", null, Box(155, 585, 205, 620))
        b.add(list, "View", null, null, "E2 Big Wheel TUR\nDEALER\n16:00 - 0:00\n8h", null, Box(120, 605, 540, 725))
        b.add(list, "Button", null, null, "RESPOND", null, Box(555, 644, 660, 684), clickable = true)
        val page = Parser.parse(b.build())
        assertEquals(Screen.AVAILABLE, page.screen)
        val res = Assemble.shifts(page.tokens, page.month!!)
        val s = res.shifts.single()
        assertEquals(LocalDate.of(2026, 10, 16), s.date)
        assertEquals("E2 Big Wheel TUR", s.table)
        assertEquals("KA", s.initials)
    }

    @Test fun schedule() {
        val page = Parser.parse(Fixtures.scheduleFlat())
        assertEquals(Screen.SCHEDULE, page.screen)
        val days = Assemble.schedule(page.tokens, page.month!!)
        fun d(n: Int) = LocalDate.of(2026, 10, n)
        assertEquals(DayStatus.SCHEDULED, days[d(13)])
        assertEquals(DayStatus.SCHEDULED, days[d(14)])
        assertEquals(DayStatus.AVAILABLE, days[d(15)])
        assertEquals(DayStatus.AVAILABLE, days[d(16)])
        assertEquals(DayStatus.REST, days[d(17)])
        assertEquals(DayStatus.SCHEDULED, days[d(18)])
    }

    @Test fun loginAndUnknown() {
        val b = UiTreeBuilder("evo", 720, 1600)
        val root = b.add(-1, "FrameLayout", null, null, null, null, Box(0, 0, 720, 1600))
        b.add(root, "EditText", null, null, null, "Email", Box(40, 500, 680, 560))
        b.add(root, "EditText", null, null, null, "Password", Box(40, 600, 680, 660), password = true)
        b.add(root, "Button", null, "Sign in", null, null, Box(40, 700, 680, 760), clickable = true)
        assertEquals(Screen.LOGIN, Parser.parse(b.build()).screen)

        val e = UiTreeBuilder("evo", 720, 1600)
        e.add(-1, "FrameLayout", null, null, null, null, Box(0, 0, 720, 1600))
        assertEquals(Screen.EMPTY, Parser.parse(e.build()).screen)
    }
}

class NavTest {
    @Test fun arrowsAndEntry() {
        val tree = Fixtures.scheduleFlat()
        val page = Parser.parse(tree)
        val next = Nav.monthArrow(tree, page.monthLine!!, forward = true)!!
        val prev = Nav.monthArrow(tree, page.monthLine!!, forward = false)!!
        assertEquals(Box(655, 210, 685, 250), tree[next].box)
        assertEquals(Box(125, 210, 150, 250), tree[prev].box)
        val entry = Nav.availableEntry(page)!!
        assertEquals("1 SHIFT AVAILABLE", entry.text)
        assertNotNull(Nav.safeTarget(tree, entry.nodeId))

        val av = Fixtures.availableFlat()
        val ap = Parser.parse(av)
        assertEquals(Box(645, 205, 695, 255), av[Nav.monthArrow(av, ap.monthLine!!, true)!!].box)
        assertEquals(Box(20, 205, 65, 255), av[Nav.monthArrow(av, ap.monthLine!!, false)!!].box)
        // RESPOND-a heç vaxt toxunulmur
        val respond = av.nodes.first { it.text == "RESPOND" }
        assertNull(Nav.safeTarget(av, respond.id))
    }

    @Test fun scrollsInnerListNotMonthPager() {
        val b = UiTreeBuilder("evo.test", Fixtures.W, Fixtures.H)
        val root = b.add(-1, "android.widget.FrameLayout", null, null, null, null, Box(0, 0, Fixtures.W, Fixtures.H))
        for (h in Fixtures.availableHeader) b.add(root, "android.widget.TextView", null, h.text, h.desc, null, h.box, clickable = h.clickable)
        val pager = b.add(root, "androidx.viewpager.widget.ViewPager", null, null, null, null, Box(0, 262, Fixtures.W, 1375), scrollable = true)
        val list = b.add(pager, "androidx.recyclerview.widget.RecyclerView", null, null, null, null, Box(0, 262, Fixtures.W, 1370), scrollable = true)
        for (i in Fixtures.cardA2T + Fixtures.cardE2) b.add(list, "android.widget.TextView", null, i.text, i.desc, null, i.box)
        val tree = b.build()
        val page = Parser.parse(tree)
        assertEquals(list, Nav.mainScrollable(tree, page))
    }

    @Test fun learnedSignature() {
        val sig = NodeSig(null, "4 SHIFTS AVAILABLE", null, "android.widget.TextView")
        val back = NodeSig.parse(sig.serialize())
        assertEquals(sig, back)
        val tree = Fixtures.scheduleFlat()
        val id = Nav.findSig(tree, sig)!!
        assertEquals("4 SHIFTS AVAILABLE", tree[id].text)
    }
}

class StitchTest {
    private fun card(table: String, time: String, ini: String = "") =
        CardTok(Card(table, "DEALER", TimeRange.parse(time)!!, time, "8h", ini, true, Box(0, 0, 1, 1)), 0)

    @Test fun forwardWithOverlap() {
        val acc = mutableListOf<Tok>(DateTok(15, null, 0), card("A", "8:00 - 16:00", "AA"), card("B", "16:00 - 0:00", "BB"))
        val page2 = listOf(card("B", "16:00 - 0:00", ""), DateTok(16, null, 0), card("C", "16:00 - 0:00", "CC"))
        assertEquals(2, Stitch.forward(acc, page2))
        assertEquals(5, acc.size)
        // Eyni səhifə yenidən (siyahının sonu) — heç nə əlavə olunmur
        assertEquals(0, Stitch.forward(acc, page2))
        val shifts = Assemble.shifts(acc, YearMonth.of(2026, 10)).shifts
        assertEquals(LocalDate.of(2026, 10, 15), shifts.first { it.table == "B" }.date)
        assertEquals("BB", shifts.first { it.table == "B" }.initials)
        assertEquals(LocalDate.of(2026, 10, 16), shifts.first { it.table == "C" }.date)
    }

    @Test fun mergeBothDirections() {
        val acc = listOf<Tok>(DateTok(19, null, 0), card("X", "9:00 - 11:00"), DateTok(24, null, 0), card("Y", "8:00 - 16:00"))
        val up = listOf<Tok>(DateTok(16, null, 0), card("E2", "16:00 - 0:00"), DateTok(19, null, 0), card("X", "9:00 - 11:00"))
        val merged = Stitch.merge(acc, up)!!
        assertEquals(6, merged.size)
        val res = Assemble.shifts(merged, YearMonth.of(2026, 10)).shifts
        assertEquals(LocalDate.of(2026, 10, 16), res.first { it.table == "E2" }.date)
        assertNull(Stitch.merge(acc, listOf(DateTok(28, null, 0), card("Z", "8:00 - 9:00"))))
    }

    @Test fun sameTableNextDayAtPageEdge() {
        // 15-də və 16-da eyni masa; 16-nın başlığı yeni səhifənin ən üstündədir — buraxılmamalıdır
        val acc = mutableListOf<Tok>(DateTok(15, null, 0), card("E2", "16:00 - 0:00", "KA"))
        val page2 = listOf<Tok>(DateTok(16, null, 0), card("E2", "16:00 - 0:00", "KA"), card("F", "16:00 - 0:00", "MC"))
        Stitch.forward(acc, page2)
        val res = Assemble.shifts(acc, YearMonth.of(2026, 10)).shifts
        assertEquals(listOf(15, 16), res.filter { it.table == "E2" }.map { it.date.dayOfMonth })
        assertEquals(16, res.first { it.table == "F" }.date.dayOfMonth)
    }

    @Test fun monthRollover() {
        val toks = listOf<Tok>(DateTok(30, DayOfWeek.FRIDAY, 0), card("A", "16:00 - 0:00"), DateTok(2, DayOfWeek.MONDAY, 0), card("B", "16:00 - 0:00"))
        val res = Assemble.shifts(toks, YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 10, 30), res.shifts[0].date)
        assertEquals(LocalDate.of(2026, 11, 2), res.shifts[1].date)
    }
}

class TrackerTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val oct = YearMonth.of(2026, 10)
    private fun s(day: Int, ini: String = "KA") =
        Shift(LocalDate.of(2026, 10, day), "E2 Big Wheel TUR", "DEALER", TimeRange(960, 0), "8h", ini)

    @Test fun lifecycle() {
        var r = Tracker.applyFullScan(emptyList(), listOf(s(16)), setOf(oct), 1, today)
        assertEquals(1, r.newOnes.size)
        r = Tracker.applyFullScan(r.list, listOf(s(16)), setOf(oct), 2, today)
        assertEquals(0, r.newOnes.size)
        // bir dəfə görünmədi — hələ "götürülüb" sayılmır
        r = Tracker.applyFullScan(r.list, emptyList(), setOf(oct), 3, today)
        assertTrue(r.list.single().present)
        r = Tracker.applyFullScan(r.list, emptyList(), setOf(oct), 4, today)
        assertFalse(r.list.single().present)
        assertEquals(1, r.gone.size)
        // yenidən ortaya atıldı — yenə bildiriş
        r = Tracker.applyFullScan(r.list, listOf(s(16)), setOf(oct), 5, today)
        assertEquals(1, r.newOnes.size)
        assertEquals(2, r.newOnes.single().notifyCount)
        // başqa inisial = başqa növbə
        r = Tracker.applyFullScan(r.list, listOf(s(16), s(16, "MC")), setOf(oct), 6, today)
        assertEquals(1, r.newOnes.size)
    }

    @Test fun otherMonthNotScannedIsNotGone() {
        var r = Tracker.applyFullScan(emptyList(), listOf(s(20)), setOf(oct), 1, today)
        repeat(3) { r = Tracker.applyFullScan(r.list, emptyList(), setOf(oct.plusMonths(1)), 2L + it, today) }
        assertTrue(r.list.single().present)
    }

    @Test fun passiveAddsOnly() {
        val r = Tracker.applyPassive(emptyList(), listOf(s(16)), 1, today)
        assertEquals(1, r.newOnes.size)
        val r2 = Tracker.applyPassive(r.list, emptyList(), 2, today)
        assertTrue(r2.list.single().present)
    }
}

class AzTest {
    @Test fun notificationLine() {
        val t = Tracked.of(Shift(LocalDate.of(2026, 10, 16), "E2 Big Wheel TUR", "DEALER", TimeRange(960, 0), "8h", "KA"), 0)
        assertEquals("🔔 16 Okt (Cümə) · E2 Big Wheel TUR · 16:00–00:00 · ortadadır (KA)", Az.notifLine(t))
    }

    @Test fun dumpContainsTexts() {
        val tree = Fixtures.availableFlat()
        val d = Dump.tree(tree)
        assertTrue(d.contains("t=\"E2 Big Wheel TUR\""))
        assertTrue(Dump.page(Parser.parse(tree)).contains("KART [E2 Big Wheel TUR"))
    }
}

class DemoTest {
    @Test fun demoChain() {
        for (date in listOf(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 31), LocalDate.of(2027, 1, 1))) {
            val page = Parser.parse(Demo.tree(date))
            assertEquals(Screen.AVAILABLE, page.screen)
            val res = Assemble.shifts(page.tokens, page.month!!)
            assertEquals(2, res.shifts.size)
            assertTrue(res.shifts.all { it.date == date })
            val m = res.shifts.filter { ShiftFilter.matches(it.time, 960, 0) }
            assertEquals(1, m.size)
            assertEquals(Demo.TABLE, m[0].table)
            assertEquals(Demo.INITIALS, m[0].initials)
        }
        // İstifadəçi filtri dəyişibsə, test növbəsi də ona uyğun olur
        for ((a, b) in listOf(8 * 60 to 16 * 60, 9 * 60 + 30 to 18 * 60, 0 to 8 * 60)) {
            val page = Parser.parse(Demo.tree(LocalDate.of(2026, 10, 20), a, b))
            val res = Assemble.shifts(page.tokens, page.month!!)
            assertEquals(1, res.shifts.count { ShiftFilter.matches(it.time, a, b) })
        }
    }
}

