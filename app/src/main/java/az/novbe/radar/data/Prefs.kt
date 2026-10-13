package az.novbe.radar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** İstifadəçi ayarları. */
data class RadarSettings(
    val enabled: Boolean = true,
    val intervalMin: Int = 5,
    val startMin: Int = 16 * 60,
    val endMin: Int = 0,
    val freeDaysOnly: Boolean = false,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val alarmMode: Boolean = false,
    val quietEnabled: Boolean = false,
    val quietFrom: Int = 23 * 60,
    val quietTo: Int = 8 * 60,
    val pauseInQuiet: Boolean = false,
    val darkOverlay: Boolean = true,
    val passiveRead: Boolean = true,
    val evoPackage: String? = null,
    val setupDone: Boolean = false,
    val learnMode: Boolean = false,
    val autostartConfirmed: Boolean = false,
    val miuiPermsConfirmed: Boolean = false,
    val learnedNav: String? = null,
) {
    val intervalMs: Long get() = intervalMin.coerceIn(2, 30) * 60_000L

    fun inQuietHours(minuteOfDay: Int): Boolean {
        if (!quietEnabled || quietFrom == quietTo) return false
        return if (quietFrom < quietTo) minuteOfDay in quietFrom until quietTo
        else minuteOfDay >= quietFrom || minuteOfDay < quietTo
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _flow = MutableStateFlow(RadarSettings())
    val flow: StateFlow<RadarSettings> = _flow
    val s: RadarSettings get() = _flow.value

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("radar_settings", Context.MODE_PRIVATE)
        _flow.value = load()
    }

    @Synchronized
    fun update(f: (RadarSettings) -> RadarSettings) {
        val n = f(_flow.value)
        save(n)
        _flow.value = n
    }

    private fun load(): RadarSettings {
        val d = RadarSettings()
        return RadarSettings(
            enabled = sp.getBoolean("enabled", d.enabled),
            intervalMin = sp.getInt("intervalMin", d.intervalMin),
            startMin = sp.getInt("startMin", d.startMin),
            endMin = sp.getInt("endMin", d.endMin),
            freeDaysOnly = sp.getBoolean("freeDaysOnly", d.freeDaysOnly),
            sound = sp.getBoolean("sound", d.sound),
            vibrate = sp.getBoolean("vibrate", d.vibrate),
            alarmMode = sp.getBoolean("alarmMode", d.alarmMode),
            quietEnabled = sp.getBoolean("quietEnabled", d.quietEnabled),
            quietFrom = sp.getInt("quietFrom", d.quietFrom),
            quietTo = sp.getInt("quietTo", d.quietTo),
            pauseInQuiet = sp.getBoolean("pauseInQuiet", d.pauseInQuiet),
            darkOverlay = sp.getBoolean("darkOverlay", d.darkOverlay),
            passiveRead = sp.getBoolean("passiveRead", d.passiveRead),
            evoPackage = sp.getString("evoPackage", null),
            setupDone = sp.getBoolean("setupDone", d.setupDone),
            learnMode = sp.getBoolean("learnMode", d.learnMode),
            autostartConfirmed = sp.getBoolean("autostartConfirmed", d.autostartConfirmed),
            miuiPermsConfirmed = sp.getBoolean("miuiPermsConfirmed", d.miuiPermsConfirmed),
            learnedNav = sp.getString("learnedNav", null),
        )
    }

    private fun save(s: RadarSettings) {
        sp.edit()
            .putBoolean("enabled", s.enabled)
            .putInt("intervalMin", s.intervalMin)
            .putInt("startMin", s.startMin)
            .putInt("endMin", s.endMin)
            .putBoolean("freeDaysOnly", s.freeDaysOnly)
            .putBoolean("sound", s.sound)
            .putBoolean("vibrate", s.vibrate)
            .putBoolean("alarmMode", s.alarmMode)
            .putBoolean("quietEnabled", s.quietEnabled)
            .putInt("quietFrom", s.quietFrom)
            .putInt("quietTo", s.quietTo)
            .putBoolean("pauseInQuiet", s.pauseInQuiet)
            .putBoolean("darkOverlay", s.darkOverlay)
            .putBoolean("passiveRead", s.passiveRead)
            .putString("evoPackage", s.evoPackage)
            .putBoolean("setupDone", s.setupDone)
            .putBoolean("learnMode", s.learnMode)
            .putBoolean("autostartConfirmed", s.autostartConfirmed)
            .putBoolean("miuiPermsConfirmed", s.miuiPermsConfirmed)
            .putString("learnedNav", s.learnedNav)
            .apply()
    }
}
