package az.novbe.radar.radar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import az.novbe.radar.R
import az.novbe.radar.core.Az
import az.novbe.radar.core.Tracked
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.RadarSettings

object Notifier {
    const val CH_SERVICE = "radar_service"
    // Android 8+-da səs/vibrasiya kanalın özündən gəlir, ona görə hər birləşmə üçün ayrıca kanal
    const val CH_SHIFT = "radar_shift" // səs + vibrasiya
    const val CH_SHIFT_SOUND = "radar_shift_sound" // yalnız səs
    const val CH_SHIFT_VIBRATE = "radar_shift_vibrate" // yalnız vibrasiya
    const val CH_SHIFT_ALARM = "radar_shift_alarm"
    const val CH_WARN = "radar_warn"

    const val ID_SERVICE = 1
    private const val ID_WARN = 2
    private const val BASE_SHIFT = 1000

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Radar xidməti", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Radarın fonda işlədiyini göstərən daimi bildiriş"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SHIFT, "Tapılan növbələr", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Uyğun yeni növbə tapılanda"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SHIFT_SOUND, "Tapılan növbələr (vibrasiyasız)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ayarlarda vibrasiya söndürüləndə"
                enableVibration(false)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SHIFT_VIBRATE, "Tapılan növbələr (səssiz)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ayarlarda səs söndürüləndə"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 200, 350)
                setSound(null, null)
            }
        )
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CH_SHIFT_ALARM, "Tapılan növbələr (alarm)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Səssiz rejimdə də çalan alarm bildirişi"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WARN, "Xəbərdarlıqlar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Radar işləməyəndə xəbərdarlıq"
            }
        )
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, az.novbe.radar.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, pi())
    }

    private fun openEvoIntent(ctx: Context): PendingIntent? {
        val pkg = EvoFinder.packageName(ctx) ?: return null
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 3, launch, pi())
    }

    private fun pi(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

    fun serviceNotification(ctx: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle("Növbə Radarı aktivdir")
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(ctx))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, "İndi yoxla", RadarService.scanNowIntent(ctx))
            .build()

    /** Ayarlara görə kanal: alarm, səs+vibrasiya, yalnız səs, yalnız vibrasiya. */
    fun shiftChannel(s: RadarSettings, quiet: Boolean): String = when {
        s.alarmMode && !quiet -> CH_SHIFT_ALARM
        quiet -> CH_SHIFT
        s.sound && s.vibrate -> CH_SHIFT
        s.sound -> CH_SHIFT_SOUND
        s.vibrate -> CH_SHIFT_VIBRATE
        else -> CH_SHIFT
    }

    fun inQuietNow(s: RadarSettings): Boolean {
        val now = java.time.LocalTime.now()
        return s.inQuietHours(now.hour * 60 + now.minute)
    }

    fun notifyShift(ctx: Context, t: Tracked) {
        val s = Prefs.s
        val quiet = inQuietNow(s)
        val alarm = s.alarmMode && !quiet
        val line = Az.notifLine(t)
        val sub = buildString {
            if (t.test) append("TEST — əsl növbə deyil")
            if (t.role.isNotBlank()) { if (isNotEmpty()) append(" · "); append(t.role) }
            if (t.duration.isNotBlank()) { if (isNotEmpty()) append(" · "); append(t.duration) }
            if (t.notifyCount > 1) { if (isNotEmpty()) append(" · "); append("yenidən atıldı") }
        }
        val openEvo = openEvoIntent(ctx)
        val b = NotificationCompat.Builder(ctx, shiftChannel(s, quiet))
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(line)
            .setContentText(sub.ifEmpty { "Yeni uyğun növbə" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(line + if (sub.isNotEmpty()) "\n$sub" else ""))
            .setAutoCancel(true)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openEvo ?: openAppIntent(ctx))
        if (quiet || (!s.sound && !s.vibrate && !alarm)) b.setSilent(true)
        if (openEvo != null) b.addAction(0, "EvoApp-ı aç", openEvo)
        if (alarm) {
            val stop = PendingIntent.getBroadcast(
                ctx, 4, Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_STOP_ALARM), pi(),
            )
            b.addAction(0, "Siqnalı dayandır", stop)
            b.setDeleteIntent(stop)
        }
        post(ctx, BASE_SHIFT + (t.key.hashCode() and 0x3FFF), b.build())
        if (alarm) AlarmPlayer.play(ctx, vibrate = s.vibrate)
    }

    fun warn(ctx: Context, reason: String) {
        val b = NotificationCompat.Builder(ctx, CH_WARN)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle("⚠️ Radar işləmir")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(ctx))
        post(ctx, ID_WARN, b.build())
    }

    fun clearWarn(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_WARN)

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
