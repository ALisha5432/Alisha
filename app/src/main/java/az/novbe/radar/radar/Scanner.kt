package az.novbe.radar.radar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import az.novbe.radar.core.Assemble
import az.novbe.radar.core.DayStatus
import az.novbe.radar.core.Dump
import az.novbe.radar.core.Nav
import az.novbe.radar.core.NodeSig
import az.novbe.radar.core.Page
import az.novbe.radar.core.Parser
import az.novbe.radar.core.Screen
import az.novbe.radar.core.Shift
import az.novbe.radar.core.ShiftFilter
import az.novbe.radar.core.Stitch
import az.novbe.radar.core.Tok
import az.novbe.radar.core.UiTree
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

sealed class ScanOutcome {
    data class Success(
        val shifts: List<Shift>,
        val months: Set<YearMonth>,
        val schedule: Map<LocalDate, DayStatus> = emptyMap(),
        val notes: List<String> = emptyList(),
    ) : ScanOutcome()

    data class Problem(val reason: String) : ScanOutcome()
}

/** İstifadəçi ekrana toxundu və ya xidmət dayandı: skan yarımçıq kəsilir. */
class ScanAborted : Exception("Skan dayandırıldı")

/**
 * Bir tam skan: EvoApp-ı aç → Available Shifts → yuxarıdan aşağı scroll → cari + növbəti ay → Home.
 *
 * QADAĞA: yalnız oxuyur. Toxunuş yalnız naviqasiya üçündür ("N SHIFTS AVAILABLE" girişi, ay oxları)
 * və hər dəfə [Nav.safeTarget] ilə yoxlanılır — RESPOND və bənzər düymələr heç vaxt basılmır.
 * Scroll toxunuşla deyil, node əməliyyatı (ACTION_SCROLL_FORWARD/BACKWARD) ilə edilir.
 */
class Scanner(private val svc: AccessibilityService, private val overlay: ScanOverlay?) {

    @Volatile var abort = false

    /** Son skanda OCR (ekran şəkli) istifadə olundumu? */
    var usedOcr = false
        private set

    private val notes = LinkedHashSet<String>()
    private var lastOcr = 0L

    // Passiv oxuma üçün: istifadəçi özü scroll edəndə səhifələri birləşdiririk ki, tarixlər itməsin
    private var passiveAcc: List<Tok> = emptyList()
    private var passiveMonth: YearMonth? = null
    private var passiveAt = 0L

    // ---------- Oxuma ----------

    private fun check() {
        if (abort) throw ScanAborted()
    }

    private suspend fun pause(ms: Long) {
        check()
        delay(ms)
        check()
    }

    fun snapshot(): UiTree? {
        val root = svc.rootSafe() ?: return null
        val dm = svc.resources.displayMetrics
        val r = Rect()
        root.getBoundsInScreen(r)
        return Snapshot.capture(root, maxOf(dm.widthPixels, r.right), maxOf(dm.heightPixels, r.bottom))
    }

    /** Eyni snapshot-dan həm ağac, həm də təhlil — node id-ləri uyğun gəlsin deyə. */
    private fun fresh(): Pair<UiTree, Page>? {
        val t = snapshot() ?: return null
        return t to Parser.parse(t)
    }

    /**
     * Node-lardan oxu; mətn alınmasa ekran şəkli + cihazda OCR (ehtiyat yol).
     * [ocrEveryMs]: OCR-lar arası minimum fasilə (passiv oxumada seyrək, batareya üçün).
     */
    private suspend fun page(ocrEveryMs: Long = 1200): Page? {
        check()
        val p = fresh()?.second
        if (p != null && p.screen in KNOWN) return p
        if (p != null && p.screen != Screen.EMPTY && p.lines.size >= 6) return p
        val now = System.currentTimeMillis()
        if (now - lastOcr < ocrEveryMs) return p
        lastOcr = now
        val tree = runCatching {
            if (overlay != null) overlay.hidden { OcrReader.read(svc) } else OcrReader.read(svc)
        }.getOrNull() ?: return p
        val op = Parser.parse(tree)
        if (op.screen in KNOWN) {
            usedOcr = true
            return op
        }
        return p
    }

    // ---------- Tam skan ----------

    suspend fun fullScan(onPhase: (String) -> Unit): ScanOutcome {
        usedOcr = false
        notes.clear()
        val pkg = EvoFinder.packageName(svc)
            ?: return ScanOutcome.Problem("EvoApp tapılmadı — Ayarlarda tətbiqi seç")

        onPhase("EvoApp açılır")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
        if (!openEvo(pkg)) return ScanOutcome.Problem("EvoApp açıla bilmədi (Xiaomi: \"Fonda pəncərə açmaq\" icazəsini yoxla)")
        if (!waitFor(8000) { inApp(pkg) }) return problemAndHome("EvoApp 8 saniyəyə açılmadı")

        var p = stablePage(pkg) ?: return problemAndHome("EvoApp ekranı oxunmadı")
        if (p.screen == Screen.LOGIN) return problemAndHome("EvoApp login istəyir — bir dəfə özün daxil ol")

        // Köhnə siyahı qalmasın: Available Shifts artıq açıqdırsa, çıx və yenidən gir
        if (p.screen == Screen.AVAILABLE) {
            onPhase("Siyahı yenilənir")
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            pause(900)
            if (!inApp(pkg)) {
                openEvo(pkg)
                waitFor(6000) { inApp(pkg) }
            }
            p = stablePage(pkg) ?: p
        }

        var schedule: Map<LocalDate, DayStatus> = emptyMap()
        if (Prefs.s.freeDaysOnly && p.screen == Screen.SCHEDULE) {
            onPhase("Qrafikim oxunur")
            val month = p.month ?: YearMonth.now()
            schedule = Assemble.schedule(scrollAndCollect(pkg), month)
        }

        onPhase("Available Shifts açılır")
        ensureAvailable(pkg) ?: return problemAndHome(unknownReason())

        val shifts = ArrayList<Shift>()
        val months = LinkedHashSet<YearMonth>()
        val now = YearMonth.now()
        for ((i, target) in listOf(now, now.plusMonths(1)).withIndex()) {
            onPhase(if (i == 0) "Bu ay oxunur" else "Növbəti ay oxunur")
            val moved = goToMonth(pkg, target)
            if (moved == null && i == 1) {
                notes += "növbəti aya keçilmədi"
                break
            }
            val cur = page() ?: break
            if (cur.screen != Screen.AVAILABLE) {
                if (i == 0) return problemAndHome(unknownReason())
                break
            }
            val month = cur.month ?: target
            val res = Assemble.shifts(scrollAndCollect(pkg), month)
            res.shifts.filterTo(shifts) { it.table.isNotBlank() }
            months += month
            res.shifts.forEach { months += YearMonth.from(it.date) }
            if (res.undated > 0) notes += "${res.undated} kartın tarixi oxunmadı"
            if (cur.month == null) {
                notes += "ay başlığı oxunmadı"
                break
            }
        }

        onPhase("Ana ekrana qayıdılır")
        // Available Shifts-dən çıx ki, EvoApp-ı açanda adi ekranını görəsən
        if (inApp(pkg)) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            delay(350)
        }
        goHome()

        if (months.isEmpty()) return ScanOutcome.Problem("Available Shifts oxunmadı")
        return ScanOutcome.Success(shifts.distinctBy { it.key }, months, schedule, notes.toList())
    }

    /** İstifadəçi özü EvoApp-dadır: yalnız görünən ekranı oxu, heç nəyə toxunma. */
    suspend fun passiveRead(): ScanOutcome {
        val p = page(ocrEveryMs = 8000) ?: return ScanOutcome.Problem("Ekran oxunmadı")
        val now = System.currentTimeMillis()
        when (p.screen) {
            Screen.AVAILABLE -> {
                val month = p.month ?: YearMonth.now()
                if (month != passiveMonth || now - passiveAt > 120_000) {
                    passiveAcc = emptyList()
                    passiveMonth = month
                }
                passiveAt = now
                passiveAcc = Stitch.merge(passiveAcc, p.tokens) ?: Stitch.trim(p.tokens)
                val res = Assemble.shifts(passiveAcc, month)
                return ScanOutcome.Success(res.shifts.filter { it.table.isNotBlank() }.distinctBy { it.key }, emptySet())
            }
            Screen.SCHEDULE -> {
                passiveAcc = emptyList()
                val month = p.month ?: return ScanOutcome.Problem("Ay oxunmadı")
                return ScanOutcome.Success(emptyList(), emptySet(), Assemble.schedule(p.tokens, month))
            }
            else -> {
                passiveAcc = emptyList()
                return ScanOutcome.Problem("Uyğun ekran deyil")
            }
        }
    }

    /** Öyrənmə rejimi üçün: cari ekranın təhlili + node ağacı mətn kimi. */
    fun dumpCurrent(): String? {
        val tree = snapshot() ?: return null
        val page = Parser.parse(tree)
        return buildString {
            append(Dump.page(page))
            append("\n--- NODE AĞACI (")
            append(tree.packageName ?: "?")
            append(") ---\n")
            append(Dump.tree(tree))
        }
    }

    // ---------- Naviqasiya ----------

    private fun openEvo(pkg: String): Boolean {
        val launch = svc.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { svc.startActivity(launch) }.isSuccess
    }

    private fun inApp(pkg: String): Boolean =
        svc.rootSafe()?.packageName?.toString() == pkg

    /** Ekran dəyişməyi dayandırana qədər gözlə (yüklənmə bitsin). */
    private suspend fun stablePage(pkg: String): Page? {
        var last: Page? = null
        var lastSig = ""
        repeat(12) {
            pause(300)
            if (!inApp(pkg)) return@repeat
            val p = page() ?: return@repeat
            val sig = p.signature
            if (sig == lastSig && p.screen != Screen.EMPTY) return p
            lastSig = sig
            last = p
        }
        return last
    }

    private suspend fun waitForScreen(pkg: String, screen: Screen, timeout: Long): Page? {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) {
            pause(300)
            if (!inApp(pkg)) continue
            val p = page() ?: continue
            if (p.screen == screen) return stablePage(pkg)?.takeIf { it.screen == screen } ?: p
        }
        return null
    }

    private suspend fun ensureAvailable(pkg: String): Page? {
        page()?.let { if (it.screen == Screen.AVAILABLE) return it }

        // 1) Öyrənmə rejimində yadda saxlanmış keçid
        NodeSig.parse(Prefs.s.learnedNav)?.let { sig ->
            val (tree, _) = fresh() ?: return@let
            val id = Nav.findSig(tree, sig) ?: return@let
            if (tap(tree, id)) waitForScreen(pkg, Screen.AVAILABLE, 5000)?.let { return it }
        }

        // 2) "N SHIFTS AVAILABLE" sətri; görünmürsə siyahını sürüşdür: əvvəl aşağı, sonda isə yuxarı
        // (qrafik oxunandan sonra siyahı artıq sondadır)
        var forward = true
        var failedTaps = 0
        repeat(12) {
            check()
            if (!inApp(pkg)) return null
            val (tree, p) = fresh() ?: return null
            if (p.screen == Screen.AVAILABLE) return p
            if (p.screen == Screen.LOGIN) return null
            val entry = Nav.availableEntry(p)?.takeIf { failedTaps < 3 }
            if (entry != null && tap(tree, entry.nodeId)) {
                waitForScreen(pkg, Screen.AVAILABLE, 5000)?.let { return it }
                failedTaps++
                // Başqa bir ekran açıldısa, geri qayıt
                val now = fresh()?.second
                if (now != null && now.screen != Screen.SCHEDULE && now.screen != Screen.AVAILABLE && inApp(pkg)) {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    pause(700)
                }
            } else if (!scroll(forward)) {
                if (!forward) return page()?.takeIf { it.screen == Screen.AVAILABLE }
                forward = false
            }
            pause(500)
        }
        return page()?.takeIf { it.screen == Screen.AVAILABLE }
    }

    /** Ay oxları ilə [target] ayına keç. Ay başlığı oxunmursa null. */
    private suspend fun goToMonth(pkg: String, target: YearMonth): Page? {
        repeat(5) {
            check()
            val (tree, p) = fresh() ?: return null
            if (p.screen != Screen.AVAILABLE) return null
            val m = p.month ?: return null
            val monthLine = p.monthLine ?: return null
            val steps = Nav.monthSteps(m, target)
            if (steps == 0) return p
            if (abs(steps) > 3) return null
            val arrow = Nav.monthArrow(tree, monthLine, forward = steps > 0) ?: return null
            if (!tap(tree, arrow)) return null
            if (!waitFor(3500) { fresh()?.second?.month.let { it != null && it != m } }) return null
            stablePage(pkg)
        }
        return null
    }

    /** Əvvəlcə siyahının başına qayıt, sonra aşağı doğru səhifə-səhifə oxu və birləşdir. */
    private suspend fun scrollAndCollect(pkg: String): List<Tok> {
        var lastSig = ""
        for (i in 0 until 15) {
            check()
            val sig = fresh()?.second?.signature ?: break
            if (sig == lastSig) break
            lastSig = sig
            if (!scroll(forward = false)) break
            pause(350)
        }

        val acc = ArrayList<Tok>()
        var p = page() ?: return acc
        val startMonth = p.month
        Stitch.forward(acc, p.tokens)
        var same = 0
        for (i in 0 until 40) {
            check()
            if (!inApp(pkg)) break
            val before = p.signature
            if (!scroll(forward = true)) break
            pause(450)
            val np = page() ?: break
            if (startMonth != null && np.month != null && np.month != startMonth) {
                // Səhv elementi sürüşdürdük (ay dəyişdi) — dayandır
                notes += "scroll ayı dəyişdi"
                break
            }
            Stitch.forward(acc, np.tokens)
            if (np.signature == before) {
                if (++same >= 2) break
            } else {
                same = 0
            }
            p = np
        }
        if (acc.isEmpty() && p.tokens.isNotEmpty()) acc += p.tokens
        return acc
    }

    private fun scroll(forward: Boolean): Boolean {
        val (tree, p) = fresh() ?: return false
        val id = Nav.mainScrollable(tree, p) ?: return false
        val node = live(tree, id) ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return runCatching { node.performAction(action) }.getOrDefault(false)
    }

    private suspend fun goHome() {
        runCatching { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        delay(400)
    }

    private fun problemAndHome(reason: String): ScanOutcome {
        runCatching { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        return ScanOutcome.Problem(reason)
    }

    private fun unknownReason(): String {
        val p = fresh()?.second
        val seen = p?.lines?.take(4)?.joinToString(" · ") { it.text.take(24) }.orEmpty()
        return if (seen.isBlank()) "\"Available Shifts\" ekranı tapılmadı (ekran boşdur)"
        else "\"Available Shifts\" ekranı tapılmadı. Ekranda: $seen"
    }

    // ---------- Təhlükəsiz toxunuş ----------

    /**
     * Yalnız [Nav.safeTarget] icazə verən node-a ACTION_CLICK. Valideynlərə qalxıb başqa
     * (yoxlanmamış) node-a basmırıq — beləcə RESPOND olan kartın özü heç vaxt basılmır.
     */
    private fun tap(tree: UiTree, nodeId: Int): Boolean {
        val safe = Nav.safeTarget(tree, nodeId) ?: return false
        val node = live(tree, safe) ?: return false
        val texts = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        if (texts.any { az.novbe.radar.core.Safety.isForbidden(it) }) return false
        return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    /** Snapshot-dakı node-u canlı ağacda tap: eyni sinif + mətn + id + sərhəd. */
    private fun live(tree: UiTree, id: Int): AccessibilityNodeInfo? {
        val want = tree[id]
        if (want.cls?.startsWith("ocr.") == true) return null
        val root = svc.rootSafe() ?: return null
        val r = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MAX_VALUE
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var budget = 6000
        while (stack.isNotEmpty() && budget-- > 0) {
            val n = stack.removeLast()
            if (n.className?.toString() == want.cls) {
                n.getBoundsInScreen(r)
                var score = abs(r.left - want.box.left) + abs(r.top - want.box.top) +
                    abs(r.right - want.box.right) + abs(r.bottom - want.box.bottom)
                if (n.text?.toString()?.takeIf { it.isNotBlank() } != want.text) score += 40
                if (runCatching { n.viewIdResourceName }.getOrNull() != want.viewId) score += 40
                if (score < bestScore) {
                    bestScore = score
                    best = n
                }
            }
            for (i in 0 until n.childCount) runCatching { n.getChild(i) }.getOrNull()?.let { stack.addLast(it) }
        }
        return if (bestScore <= 24) best else null
    }

    private suspend fun waitFor(timeout: Long, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) {
            if (cond()) return true
            pause(250)
        }
        return cond()
    }

    companion object {
        private val KNOWN = setOf(Screen.AVAILABLE, Screen.SCHEDULE, Screen.LOGIN)

        /** Saat aralığı + (istəyə görə) yalnız boş günlər. */
        fun filterShifts(shifts: List<Shift>): List<Shift> {
            val s = Prefs.s
            val scheduled = if (s.freeDaysOnly) Repo.scheduledDays() else emptySet()
            return shifts.filter { sh ->
                ShiftFilter.matches(sh.time, s.startMin, s.endMin) && sh.date.toString() !in scheduled
            }
        }
    }
}
