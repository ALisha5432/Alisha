package az.novbe.radar.radar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo

/** setAlarmClock vaxtı çatanda. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.s.enabled) return
        val i = Intent(context, RadarService::class.java).setAction(RadarService.ACTION_ALARM)
        runCatching { ContextCompat.startForegroundService(context, i) }.onFailure {
            Repo.log(LogType.ERROR, "Plan üzrə skan başlamadı: ${it.message}")
            RadarService.scheduleNext(context)
        }
    }
}

/** Telefon yenidən açılanda və ya tətbiq yenilənəndə radarı avtomatik başlat. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.s.enabled) return
        Repo.log(LogType.INFO, "Avtomatik başlama: ${intent.action?.substringAfterLast('.') ?: "?"}")
        RadarService.ensureRunning(context)
    }
}

/** Bildiriş düymələri. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_ALARM -> AlarmPlayer.stop()
        }
    }

    companion object {
        const val ACTION_STOP_ALARM = "az.novbe.radar.STOP_ALARM"
    }
}
