package com.wpradar.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wpradar.system.SystemStatus
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Hər 15 dəqiqədən bir: icazə var, amma dinləyici qopubsa — yenidən qoşur.
 * Bəzi telefonlar (Xiaomi, Samsung, Huawei) tətbiqi fonda öldürür və sistem onu özü qaytarmır.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (SystemStatus.hasNotificationAccess(context) && !ListenerStatus.connected.value) {
            SystemStatus.requestRebind(context)
            delay(5_000)
            if (!ListenerStatus.connected.value) SystemStatus.forceReconnect(context)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "listener-watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
