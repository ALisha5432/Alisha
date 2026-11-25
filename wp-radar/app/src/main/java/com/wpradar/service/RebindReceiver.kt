package com.wpradar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wpradar.system.SystemStatus

/** Telefon açılanda və tətbiq yenilənəndə dinləyicini yenidən qoşur. */
class RebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> SystemStatus.requestRebind(context)
        }
    }
}
