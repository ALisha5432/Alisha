package az.novbe.radar.radar

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import az.novbe.radar.core.Az
import az.novbe.radar.core.DayStatus
import az.novbe.radar.core.Nav
import az.novbe.radar.core.NodeSig
import az.novbe.radar.core.Parser
import az.novbe.radar.core.Shift
import az.novbe.radar.core.Tracker
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

/**
 * EvoApp-ı oxuyan accessibility xidməti. RadarService onu komandalarla idarə edir.
 * Heç vaxt RESPOND və bənzər düymələrə toxunmur (bax: core/Safety, Nav.safeTarget).
 */
class RadarAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanMutex = Mutex()
    private lateinit var overlay: ScanOverlay
    private lateinit var scanner: Scanner
    @Volatile private var passiveJob: Job? = null
    @Volatile private var learnJob: Job? = null
    @Volatile private var lastPassiveAt = 0L
    @Volatile private var lastPassiveLogAt = 0L
    @Volatile private var learnLastHash = 0
    @Volatile private var userStopped = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = ScanOverlay(this)
        scanner = Scanner(this, overlay)
        instance = this
        Status.update { it.copy(accessibilityOn = true) }
        Repo.log(LogType.INFO, "Radarın oxuma xidməti qoşuldu")
        RadarService.ensureRunning(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Status.update { it.copy(accessibilityOn = false) }
        Repo.log(LogType.ERROR, "Radarın oxuma xidməti söndü (Accessibility)")
        if (::overlay.isInitialized) overlay.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (::overlay.isInitialized) overlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val evo = Prefs.s.evoPackage ?: return
        if (pkg != evo || Status.cur.scanning) return
        if (Prefs.s.learnMode) scheduleLearn()
        if (Prefs.s.passiveRead) schedulePassive()
    }

    // ---------- Tam skan (komanda ilə) ----------

    /** [background] = ekranı biz oyatmışıq: qaranlıq pərdə göstərilir, toxunanda skan dayanır. */
    fun runFullScan(reason: String, background: Boolean) {
        scope.launch {
            if (scanMutex.isLocked) return@launch
            scanMutex.withLock { doFullScan(reason, background) }
        }
    }

    private suspend fun doFullScan(reason: String, background: Boolean) {
        Status.setScanning(true, "Başlanır")
        val started = System.currentTimeMillis()
        userStopped = false
        scanner.abort = false
        if (background) {
            overlay.onTouch = {
                userStopped = true
                scanner.abort = true
                overlay.hide()
            }
            overlay.show(Prefs.s.darkOverlay)
        }
        try {
            when (val out = scanner.fullScan { ph -> Status.setScanning(true, ph) }) {
                is ScanOutcome.Success -> {
                    val matched = handleShifts(out.shifts, out.months, passive = false, schedule = out.schedule)
                    Notifier.clearWarn(this)
                    Status.update {
                        it.copy(lastScanAt = System.currentTimeMillis(), lastScanOk = true, lastError = null, totalScans = it.totalScans + 1)
                    }
                    val extra = buildString {
                        if (scanner.usedOcr) append(" · OCR")
                        if (out.notes.isNotEmpty()) append(" · ").append(out.notes.joinToString(", "))
                    }
                    val monthsTxt = out.months.sorted().joinToString("+") { Az.monthShort(it.monthValue) }
                    Repo.log(LogType.OK, "Skan ($reason): $monthsTxt · ${out.shifts.size} növbə, $matched uyğun$extra")
                }
                is ScanOutcome.Problem -> {
                    Status.update {
                        it.copy(lastScanAt = System.currentTimeMillis(), lastScanOk = false, lastError = out.reason, totalScans = it.totalScans + 1)
                    }
                    Repo.log(LogType.ERROR, out.reason)
                    Notifier.warn(this, out.reason)
                }
            }
        } catch (e: ScanAborted) {
            Repo.log(LogType.SKIP, "Skan dayandırıldı — ekrana toxundun")
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (t: Throwable) {
            val msg = "Gözlənilməz xəta: ${t.message ?: t.javaClass.simpleName}"
            Status.update { it.copy(lastScanAt = System.currentTimeMillis(), lastScanOk = false, lastError = msg) }
            Repo.log(LogType.ERROR, msg)
            performGlobalAction(GLOBAL_ACTION_HOME)
        } finally {
            overlay.onTouch = null
            overlay.hide()
            Status.setScanning(false, "")
            RadarService.onScanFinished(this, System.currentTimeMillis() - started, lock = !userStopped)
        }
    }

    // ---------- Passiv oxuma (istifadəçi özü EvoApp-dadır) ----------

    private fun schedulePassive() {
        if (passiveJob?.isActive == true) return
        passiveJob = scope.launch {
            val wait = maxOf(900L, 1500L - (System.currentTimeMillis() - lastPassiveAt))
            delay(wait) // ekran otursun
            if (Status.cur.scanning || scanMutex.isLocked) return@launch
            lastPassiveAt = System.currentTimeMillis()
            runCatching {
                when (val out = scanner.passiveRead()) {
                    is ScanOutcome.Success -> handleShifts(out.shifts, out.months, passive = true, schedule = out.schedule)
                    is ScanOutcome.Problem -> Unit
                }
            }
        }
    }

    // ---------- Öyrənmə rejimi ----------

    private fun scheduleLearn() {
        if (learnJob?.isActive == true) return
        learnJob = scope.launch {
            delay(1200)
            if (Status.cur.scanning) return@launch
            val dump = runCatching { scanner.dumpCurrent() }.getOrNull() ?: return@launch
            val h = dump.hashCode()
            if (h == learnLastHash) return@launch
            learnLastHash = h
            LearnStore.append(this@RadarAccessibilityService, dump)
            // My Schedule ekranındakı "N SHIFTS AVAILABLE" girişini yadda saxla — skan onu istifadə edəcək
            runCatching {
                val tree = scanner.snapshot() ?: return@runCatching
                val page = Parser.parse(tree)
                Nav.availableEntry(page)?.let { entry ->
                    val n = tree[entry.nodeId]
                    val sig = NodeSig(n.viewId, n.text, n.desc, n.cls)
                    Prefs.update { it.copy(learnedNav = sig.serialize()) }
                }
            }
            Repo.log(LogType.INFO, "Öyrənmə: ekran yazıldı (${LearnStore.count(this@RadarAccessibilityService)} ekran)")
        }
    }

    // ---------- Ümumi nəticə emalı ----------

    /** Uyğun növbələri izləyiciyə verir, yenilərə bildiriş göndərir. Uyğunların sayını qaytarır. */
    private fun handleShifts(
        shifts: List<Shift>,
        months: Set<YearMonth>,
        passive: Boolean,
        schedule: Map<LocalDate, DayStatus> = emptyMap(),
    ): Int {
        if (schedule.isNotEmpty()) Repo.updateSchedule(schedule, full = !passive)
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val matched = Scanner.filterShifts(shifts)
        val res = if (passive) Tracker.applyPassive(Repo.found.value, matched, now, today)
        else Tracker.applyFullScan(Repo.found.value, matched, months, now, today)
        Repo.setFound(res.list)
        for (t in res.newOnes) {
            Notifier.notifyShift(this, t)
            Repo.log(if (passive) LogType.PASSIVE else LogType.FOUND, Az.notifLine(t))
        }
        for (g in res.gone) Repo.log(LogType.INFO, "Götürüldü: ${g.table} · ${g.date} · ${g.time}")
        if (passive && shifts.isNotEmpty() && now - lastPassiveLogAt > 5 * 60_000) {
            lastPassiveLogAt = now
            Repo.log(LogType.PASSIVE, "EvoApp-a baxdın: ${shifts.size} növbə oxundu, ${matched.size} uyğun")
        }
        return matched.size
    }

    companion object {
        @Volatile private var instance: RadarAccessibilityService? = null
        fun get(): RadarAccessibilityService? = instance
        fun isConnected(): Boolean = instance != null
    }
}
