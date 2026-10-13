package az.novbe.radar.radar

import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Telefonun vəziyyəti: ekran, kilid, zəng, batareya. */
object Device {
    fun isScreenOn(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** Ekran kilidi PIN/naxış/parol/barmaq izidir? (Bu halda telefonu özümüz aça bilmərik.) */
    fun isSecureLock(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceSecure ?: false

    /** Kilid ekranı göstərilir (sürüşdürmə kilidi də daxil). */
    fun isLocked(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false

    /** Telefon kilidlidir və açmaq üçün PIN/naxış lazımdır (ekran sönəndən sonrakı qısa güzəşt müddətində false). */
    fun needsCredential(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: false

    fun inCall(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return am.mode == AudioManager.MODE_IN_CALL ||
            am.mode == AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == AudioManager.MODE_RINGTONE
    }

    data class Battery(val percent: Int, val charging: Boolean)

    fun battery(ctx: Context): Battery {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(pct, charging)
    }

    fun canExactAlarm(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    fun ignoresBatteryOpt(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

    fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return "xiaomi" in m || "redmi" in m || "poco" in m
    }
}
