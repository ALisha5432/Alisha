package com.wpradar.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.wpradar.WpRadarApp
import com.wpradar.system.SystemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference

class WaListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bildirişlər ardıcıl emal olunur ki, təkrar yoxlaması yarışa düşməsin. */
    private val mutex = Mutex()

    override fun onListenerConnected() {
        super.onListenerConnected()
        current = WeakReference(this)
        ListenerStatus.onConnected()
        Log.i(TAG, "listener connected")
        // Dinləyici qopuq olanda gəlib hələ paneldə duran mesajları da yoxla.
        scanActive()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        current = null
        ListenerStatus.onDisconnected()
        Log.w(TAG, "listener disconnected → requestRebind")
        SystemStatus.requestRebind(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) process(sbn, live = true)
    }

    /** Paneldə hazırda duran WhatsApp bildirişlərini yoxlayır. Neçəsini yoxladığını qaytarır. */
    fun scanActive(): Int {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            Log.w(TAG, "getActiveNotifications failed", e)
            null
        } ?: return 0
        var count = 0
        for (sbn in active) {
            if (process(sbn, live = false)) count++
        }
        return count
    }

    private fun process(sbn: StatusBarNotification, live: Boolean): Boolean {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return false
        if (live) ListenerStatus.onWhatsAppEvent()

        val raw = runCatching { NotificationExtractor.extract(sbn) }.getOrElse {
            Log.w(TAG, "extract failed", it)
            return true
        }
        val contentIntent = sbn.notification?.contentIntent
        val app = application as WpRadarApp
        scope.launch {
            mutex.withLock {
                try {
                    RadarEngine.handle(app, raw, contentIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "handle failed", e)
                }
            }
        }
        return true
    }

    override fun onDestroy() {
        current = null
        ListenerStatus.onDisconnected()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        val WHATSAPP_PACKAGES = setOf(WHATSAPP, WHATSAPP_BUSINESS)
        private const val TAG = "WpRadar"

        @Volatile
        private var current: WeakReference<WaListenerService>? = null

        /** Paneldəki WhatsApp bildirişlərini indi yoxla. null → dinləyici qoşulu deyil. */
        fun scanNow(): Int? = current?.get()?.scanActive()
    }
}
