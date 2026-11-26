package com.wpradar

import android.app.Application
import com.wpradar.data.HistoryRepository
import com.wpradar.data.JournalStore
import com.wpradar.data.SeenStore
import com.wpradar.data.SettingsRepository
import com.wpradar.notify.AlertNotifier
import com.wpradar.service.ListenerStatus
import com.wpradar.service.WatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WpRadarApp : Application() {
    /** Ekrandan çıxsanız belə yarımçıq qalmamalı yazmalar üçün. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings by lazy { SettingsRepository(this) }
    val history by lazy { HistoryRepository(this) }
    val seen by lazy { SeenStore(this) }
    val journal by lazy { JournalStore(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        ListenerStatus.init(this)
        AlertNotifier.ensureChannels(this)
        runCatching { WatchdogWorker.schedule(this) }
    }
}
