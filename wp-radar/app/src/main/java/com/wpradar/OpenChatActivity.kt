package com.wpradar

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.IntentCompat
import com.wpradar.service.WaListenerService
import com.wpradar.system.SystemStatus

/**
 * Görünməz keçid ekranı: xəbərdarlığa toxunanda əvvəlcə WhatsApp-ın öz bildiriş intent-ini
 * (birbaşa həmin söhbəti) açmağa çalışır, alınmasa sadəcə WhatsApp-ı açır.
 */
class OpenChatActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: WaListenerService.WHATSAPP
        val target = IntentCompat.getParcelableExtra(intent, EXTRA_TARGET, PendingIntent::class.java)
        if (target == null || !sendOriginal(target)) {
            SystemStatus.openWhatsApp(this, pkg)
        }
        finish()
    }

    private fun sendOriginal(target: PendingIntent): Boolean = try {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }
        target.send(this, 0, null, null, null, null, options.toBundle())
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    } catch (e: Exception) {
        Log.w("WpRadar", "original intent failed", e)
        false
    }

    companion object {
        private const val EXTRA_PACKAGE = "com.wpradar.extra.PACKAGE"
        private const val EXTRA_TARGET = "com.wpradar.extra.TARGET"

        fun intent(context: Context, packageName: String, target: PendingIntent?): Intent =
            Intent(context, OpenChatActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_PACKAGE, packageName)
                if (target != null) putExtra(EXTRA_TARGET, target)
            }
    }
}
