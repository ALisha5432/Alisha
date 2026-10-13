package az.novbe.radar.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import az.novbe.radar.data.Prefs
import az.novbe.radar.radar.A11y
import az.novbe.radar.radar.Device
import az.novbe.radar.radar.EvoFinder
import az.novbe.radar.radar.RadarAccessibilityService

/** Quraşdırma vəziyyəti: hansı icazələr var, hansı çatmır. */
data class Checks(
    val evoPkg: String?,
    val evoLabel: String?,
    val notifications: Boolean,
    val accessibility: Boolean,
    val a11yConnected: Boolean,
    val exactAlarm: Boolean,
    val battery: Boolean,
    val xiaomi: Boolean,
    val autostartConfirmed: Boolean,
    val miuiPermsConfirmed: Boolean,
    val secureLock: Boolean,
) {
    /** Radarın işləməsi üçün mütləq lazım olanlar. */
    val essentialOk: Boolean get() = evoPkg != null && notifications && accessibility && exactAlarm

    /** Ana ekranda göstəriləcək çatışmayanlar. */
    fun missing(): List<String> = buildList {
        if (evoPkg == null) add("EvoApp seçilməyib")
        if (!accessibility) add("Accessibility xidməti söndürülüb")
        else if (!a11yConnected) add("Accessibility qoşulur… (bir neçə saniyə)")
        if (!notifications) add("Bildiriş icazəsi yoxdur")
        if (!exactAlarm) add("Dəqiq vaxt (alarm) icazəsi yoxdur")
        if (!battery) add("Batareya qənaəti radarı dayandıra bilər")
        if (xiaomi && !autostartConfirmed) add("Xiaomi: avtomatik başlama təsdiqlənməyib")
        if (xiaomi && !miuiPermsConfirmed) add("Xiaomi: fonda pəncərə / kilid ekranı icazələri təsdiqlənməyib")
    }

    companion object {
        fun read(ctx: Context): Checks {
            val s = Prefs.s
            val pkg = EvoFinder.packageName(ctx)
            val label = pkg?.let { p ->
                runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() }.getOrNull()
            }
            return Checks(
                evoPkg = pkg,
                evoLabel = label,
                notifications = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
                accessibility = A11y.isEnabled(ctx),
                a11yConnected = RadarAccessibilityService.isConnected(),
                exactAlarm = Device.canExactAlarm(ctx),
                battery = Device.ignoresBatteryOpt(ctx),
                xiaomi = Device.isXiaomi(),
                autostartConfirmed = s.autostartConfirmed,
                miuiPermsConfirmed = s.miuiPermsConfirmed,
                secureLock = Device.isSecureLock(ctx),
            )
        }
    }
}

/** Ayar ekranlarını açan keçidlər (bir yol işləməsə növbətisi yoxlanır). */
object Open {
    private fun pkgUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            val ok = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (ok) return true
        }
        Toast.makeText(ctx, "Bu ayar telefonda tapılmadı — Ayarlardan əl ilə aç", Toast.LENGTH_LONG).show()
        return false
    }

    fun appInfo(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)))

    fun accessibility(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun notifications(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)),
    )

    fun exactAlarm(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tryStart(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(ctx)), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)))
        } else {
            appInfo(ctx)
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun battery(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)),
    )

    fun xiaomiAutostart(ctx: Context) = tryStart(
        ctx,
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)),
    )

    fun xiaomiOtherPerms(ctx: Context) = tryStart(
        ctx,
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", ctx.packageName),
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            .putExtra("extra_pkgname", ctx.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)),
    )

    fun lockScreen(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun evoApp(ctx: Context): Boolean {
        val pkg = EvoFinder.packageName(ctx)
        val launch = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Toast.makeText(ctx, "EvoApp tapılmadı — Ayarlarda seç", Toast.LENGTH_LONG).show()
            return false
        }
        return tryStart(ctx, launch)
    }

    fun shareText(ctx: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        tryStart(ctx, Intent.createChooser(send, subject))
    }
}
