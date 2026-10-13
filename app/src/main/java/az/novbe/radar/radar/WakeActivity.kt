package az.novbe.radar.radar

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Şəffaf pəncərə: ekranı yandırır və (kilid "Yoxdur"/"Sürüşdür" olanda) kilidi götürür.
 * Sonra radar EvoApp-ı açır; bu pəncərə özünü bağlayır.
 */
class WakeActivity : Activity() {
    private var reported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = report(true)
                override fun onDismissError() = report(false)
                override fun onDismissCancelled() = report(false)
            })
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ report(true) }, 300)
        }
        // Təhlükəsizlik: bu pəncərə uzun qalmasın
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 8000)
    }

    private fun report(ok: Boolean) {
        if (reported) return
        reported = true
        RadarService.onWoke(applicationContext, ok)
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 1500)
    }
}
