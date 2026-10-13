package az.novbe.radar.radar

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import az.novbe.radar.data.Status
import az.novbe.radar.ui.MainActivity
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

/**
 * Fasiləsiz işləyən foreground xidmət: növbəti skanı planlaşdırır (setAlarmClock),
 * ekran sönəndə yoxlayır, lazım olanda ekranı oyadır və skandan sonra yenidən kilidləyir.
 */
class RadarService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        register(this)
        Notifier.ensureChannels(this)
        goForeground(statusLine())
        running = true
        Status.update { it.copy(serviceRunning = true) }
        registerScreen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(statusLine())
        when (intent?.action) {
            ACTION_ALARM -> onAlarm()
            ACTION_SCAN_NOW -> tryScan("əl ilə", manual = true, returnToApp = intent?.getBooleanExtra(EXTRA_RETURN, false) == true)
            else -> if (Status.cur.nextScanAt < System.currentTimeMillis()) scheduleNext(this, initial = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        Status.update { it.copy(serviceRunning = false) }
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        main.removeCallbacksAndMessages(null)
        releaseWake()
        super.onDestroy()
    }

    private fun goForeground(text: String) {
        val n = Notifier.serviceNotification(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifier.ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_SERVICE, n)
        }
    }

    private fun registerScreen() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_SCREEN_OFF) onScreenOff()
            }
        }
        ContextCompat.registerReceiver(this, r, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = r
    }

    // ---------- Qərar məntiqi ----------

    private fun onAlarm() {
        scheduleNext(this)
        tryScan("plan üzrə", manual = false)
    }

    private fun onScreenOff() {
        if (wokeByUs || Status.cur.scanning) return // öz kilidləməmiz
        val overdue = System.currentTimeMillis() - Status.cur.lastScanAt >= Prefs.s.intervalMs
        if (pendingOnScreenOff || overdue) {
            pendingOnScreenOff = false
            // Ekran tam sönsün, sonra yoxla
            main.postDelayed({ tryScan("ekran söndü", manual = false) }, 2500)
        }
    }

    private fun tryScan(reason: String, manual: Boolean, returnToApp: Boolean = false) {
        val s = Prefs.s
        if (!s.enabled && !manual) return
        val svc = RadarAccessibilityService.get()
        if (svc == null) {
            val msg = "Accessibility xidməti söndürülüb — Növbə Radarını açıb yenidən yandır"
            Status.update { it.copy(lastError = msg) }
            Repo.log(LogType.ERROR, msg)
            Notifier.warn(this, msg)
            return
        }
        if (Status.cur.scanning) return

        if (!manual) {
            if (Device.inCall(this)) { skip("zəng gedir"); return }
            val bat = Device.battery(this)
            if (bat.percent < 15 && !bat.charging) { skip("batareya ${bat.percent}%"); return }
            val now = LocalTime.now()
            if (s.pauseInQuiet && s.inQuietHours(now.hour * 60 + now.minute)) { skip("səssiz saatlar"); return }
        }

        if (manual) {
            startScan(svc, reason, wake = false, returnToApp = returnToApp)
            return
        }
        if (Device.isScreenOn(this)) {
            // Telefonu işlədirsən (və ya kilid ekranına baxırsan) — ekran sönəndə yoxlayacağıq
            pendingOnScreenOff = true
            skip(if (Device.isLocked(this)) "ekran açıqdır — sönəndə yoxlanacaq" else "telefon istifadədədir — ekran sönəndə yoxlanacaq")
            return
        }
        if (Device.isSecureLock(this) && Device.needsCredential(this)) {
            skip("telefon PIN/naxışla kilidlidir — fonda açmaq olmur")
            return
        }
        startScan(svc, reason, wake = true, returnToApp = false)
    }

    private fun skip(why: String) {
        Repo.log(LogType.SKIP, "Skan ötürüldü: $why")
        updateNotification()
    }

    private fun startScan(svc: RadarAccessibilityService, reason: String, wake: Boolean, returnToApp: Boolean) {
        acquireWake()
        returnAfterScan = returnToApp
        if (wake) {
            wokeByUs = true
            pendingScanReason = reason
            // Ekranı oyadan şəffaf pəncərə; o hazır olanda skan başlayır
            val i = Intent(svc, WakeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            val started = runCatching { svc.startActivity(i) }.isSuccess
            if (!started) {
                wokeByUs = false
                pendingScanReason = null
                releaseWake()
                val msg = "Ekran oyadıla bilmədi (Xiaomi: \"Fonda pəncərə açmaq\" icazəsini ver)"
                Repo.log(LogType.ERROR, msg)
                Notifier.warn(this, msg)
                return
            }
            // WakeActivity 6 saniyəyə cavab verməsə, yenə də cəhd et
            main.postDelayed({ if (pendingScanReason != null) onWokeInternal(false) }, 6000)
        } else {
            svc.runFullScan(reason, background = false)
        }
    }

    private fun onWokeInternal(ok: Boolean) {
        val reason = pendingScanReason ?: return
        pendingScanReason = null
        val svc = RadarAccessibilityService.get() ?: run {
            wokeByUs = false
            releaseWake()
            return
        }
        if (!ok && Device.needsCredential(this)) {
            wokeByUs = false
            releaseWake()
            val msg = "Kilid ekranı açılmadı — kilidi \"Sürüşdür\" və ya \"Yoxdur\" et"
            Repo.log(LogType.ERROR, msg)
            Notifier.warn(this, msg)
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            return
        }
        svc.runFullScan(reason, background = true)
    }

    private fun onScanDone(lock: Boolean) {
        if (wokeByUs) {
            if (lock) {
                // Ekranı yenidən söndür (kilidlə)
                main.postDelayed({
                    RadarAccessibilityService.get()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    main.postDelayed({ wokeByUs = false }, 2500)
                }, 300)
            } else {
                wokeByUs = false
            }
        }
        if (returnAfterScan) {
            returnAfterScan = false
            main.postDelayed({
                val i = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                runCatching { (RadarAccessibilityService.get() ?: this).startActivity(i) }
            }, 500)
        }
        releaseWake()
        updateNotification()
    }

    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovbeRadari:scan").apply {
            setReferenceCounted(false)
            acquire(120_000)
        }
    }

    private fun releaseWake() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun statusLine(): String {
        val st = Status.cur
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val last = if (st.lastScanAt > 0) "son yoxlama ${fmt.format(Date(st.lastScanAt))}" else "hələ yoxlama olmayıb"
        val next = if (st.nextScanAt > 0) " · növbəti ${fmt.format(Date(st.nextScanAt))}" else ""
        val pin = if (Device.isSecureLock(this)) " · PIN: yalnız telefon açıq olanda" else ""
        return last + next + pin
    }

    private fun updateNotification() {
        runCatching { goForeground(statusLine()) }
    }

    companion object {
        const val ACTION_ALARM = "az.novbe.radar.ALARM"
        const val ACTION_SCAN_NOW = "az.novbe.radar.SCAN_NOW"
        const val EXTRA_RETURN = "return_to_app"

        @Volatile var running = false
            private set
        @Volatile private var wokeByUs = false
        @Volatile private var pendingOnScreenOff = false
        @Volatile private var pendingScanReason: String? = null
        @Volatile private var returnAfterScan = false
        private var instanceRef: java.lang.ref.WeakReference<RadarService>? = null

        fun ensureRunning(ctx: Context) {
            if (!Prefs.s.enabled) return
            val i = Intent(ctx, RadarService::class.java)
            runCatching { ContextCompat.startForegroundService(ctx, i) }
                .onFailure { Repo.log(LogType.ERROR, "Fon xidməti başlamadı: ${it.message}") }
        }

        fun stop(ctx: Context) {
            cancelAlarm(ctx)
            ctx.stopService(Intent(ctx, RadarService::class.java))
        }

        /** Əl ilə skan. [returnToApp] = skandan sonra Növbə Radarına qayıt. */
        fun scanNow(ctx: Context, returnToApp: Boolean) {
            val i = Intent(ctx, RadarService::class.java).setAction(ACTION_SCAN_NOW).putExtra(EXTRA_RETURN, returnToApp)
            runCatching { ContextCompat.startForegroundService(ctx, i) }
        }

        fun scanNowIntent(ctx: Context): PendingIntent {
            val i = Intent(ctx, RadarService::class.java).setAction(ACTION_SCAN_NOW)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(ctx, 12, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getService(ctx, 12, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
        }

        /** WakeActivity ekran açılanda çağırır. */
        fun onWoke(ctx: Context, ok: Boolean) {
            instanceRef?.get()?.onWokeInternal(ok)
        }

        fun onScanFinished(ctx: Context, durationMs: Long, lock: Boolean) {
            val s = instanceRef?.get()
            if (s != null) {
                s.onScanDone(lock)
            } else if (wokeByUs) {
                if (lock) RadarAccessibilityService.get()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                wokeByUs = false
            }
        }

        private fun register(s: RadarService) {
            instanceRef = java.lang.ref.WeakReference(s)
        }

        /** Növbəti skanı dəqiq vaxta planlaşdır (Doze rejimində də oyanır). */
        fun scheduleNext(ctx: Context, initial: Boolean = false) {
            val s = Prefs.s
            if (!s.enabled) {
                cancelAlarm(ctx)
                return
            }
            val delay = if (initial) 30_000L else s.intervalMs
            val at = System.currentTimeMillis() + delay
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = alarmIntent(ctx)
            val show = PendingIntent.getActivity(
                ctx, 11, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching {
                if (Device.canExactAlarm(ctx)) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }.onFailure {
                runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
            }
            Status.update { it.copy(nextScanAt = at) }
        }

        fun cancelAlarm(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
            Status.update { it.copy(nextScanAt = 0L) }
        }

        private fun alarmIntent(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 10, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_ALARM),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
