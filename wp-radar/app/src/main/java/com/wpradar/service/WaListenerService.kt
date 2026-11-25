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

class WaListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bildirişlər ardıcıl emal olunur ki, təkrar yoxlaması yarışa düşməsin. */
    private val mutex = Mutex()

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerStatus.connected.value = true
        Log.i(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerStatus.connected.value = false
        Log.w(TAG, "listener disconnected → requestRebind")
        SystemStatus.requestRebind(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName !in WHATSAPP_PACKAGES) return
        ListenerStatus.lastWhatsAppEventAt.value = System.currentTimeMillis()

        val raw = runCatching { NotificationExtractor.extract(sbn) }.getOrElse {
            Log.w(TAG, "extract failed", it)
            return
        }
        val contentIntent = sbn.notification.contentIntent
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
    }

    override fun onDestroy() {
        ListenerStatus.connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        val WHATSAPP_PACKAGES = setOf(WHATSAPP, WHATSAPP_BUSINESS)
        private const val TAG = "WpRadar"
    }
}
