package com.wpradar.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow

/** Dinləyicinin canlı vəziyyəti. Son vaxtlar telefon yenidən başlasa da yadda qalır. */
object ListenerStatus {
    val connected = MutableStateFlow(false)
    val lastWhatsAppEventAt = MutableStateFlow(0L)
    val lastConnectedAt = MutableStateFlow(0L)

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("listener_status", Context.MODE_PRIVATE)
        prefs = p
        lastWhatsAppEventAt.value = p.getLong(KEY_EVENT, 0L)
        lastConnectedAt.value = p.getLong(KEY_CONNECTED, 0L)
    }

    fun onConnected() {
        val now = System.currentTimeMillis()
        connected.value = true
        lastConnectedAt.value = now
        prefs?.edit { putLong(KEY_CONNECTED, now) }
    }

    fun onDisconnected() {
        connected.value = false
    }

    fun onWhatsAppEvent() {
        val now = System.currentTimeMillis()
        val previous = lastWhatsAppEventAt.value
        lastWhatsAppEventAt.value = now
        // Aktiv qrupda hər saniyə diskə yazmayaq.
        if (now - previous > 10_000L) prefs?.edit { putLong(KEY_EVENT, now) }
    }

    private const val KEY_EVENT = "last_event"
    private const val KEY_CONNECTED = "last_connected"
}
