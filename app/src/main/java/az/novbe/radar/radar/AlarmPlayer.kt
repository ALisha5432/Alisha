package az.novbe.radar.radar

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** "Alarm rejimi": səssiz rejimdə də eşidilən qısa siqnal (alarm səs axını). */
object AlarmPlayer {
    private var ringtone: Ringtone? = null
    private val main = Handler(Looper.getMainLooper())

    fun play(ctx: Context, seconds: Int = 12, vibrate: Boolean = true) {
        stop()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(ctx.applicationContext, uri) ?: return@runCatching
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) r.isLooping = true
            r.play()
            ringtone = r
        }
        if (vibrate) runCatching {
            @Suppress("DEPRECATION")
            val v = ctx.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 600), -1))
        }
        main.postDelayed({ stop() }, seconds * 1000L)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }
        ringtone = null
    }
}
