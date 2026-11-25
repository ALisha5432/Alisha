package com.wpradar.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wpradar.service.WaListenerService

/** İcazələrin vəziyyəti və uyğun sistem ayarlarını açan köməkçilər. */
object SystemStatus {
    private const val TAG = "WpRadar"

    fun listenerComponent(context: Context) = ComponentName(context, WaListenerService::class.java)

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun hasPostPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canPostNotifications(context: Context): Boolean =
        hasPostPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isChannelEnabled(context: Context, channelId: String): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    /** Dinləyici qopubsa, sistemdən yenidən bağlamağı xahiş edir. */
    fun requestRebind(context: Context) {
        if (!hasNotificationAccess(context)) return
        try {
            NotificationListenerService.requestRebind(listenerComponent(context))
        } catch (e: Exception) {
            Log.w(TAG, "requestRebind failed", e)
        }
    }

    /** Ən etibarlı üsul: komponenti söndürüb-yandırmaq sistemi dinləyicini yenidən bağlamağa məcbur edir. */
    fun forceReconnect(context: Context) {
        val pm = context.packageManager
        val component = listenerComponent(context)
        try {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (e: Exception) {
            Log.w(TAG, "toggle failed", e)
        }
        requestRebind(context)
    }

    fun openNotificationAccessSettings(context: Context): Boolean =
        safeStart(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) ||
            openAppDetails(context)

    fun openAppDetails(context: Context): Boolean = safeStart(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    fun openAppNotificationSettings(context: Context): Boolean = safeStart(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    ) || openAppDetails(context)

    fun openChannelSettings(context: Context, channelId: String): Boolean = safeStart(
        context,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
    ) || openAppNotificationSettings(context)

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean = safeStart(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
    ) || safeStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openWhatsApp(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val other = if (packageName == WaListenerService.WHATSAPP) {
            WaListenerService.WHATSAPP_BUSINESS
        } else {
            WaListenerService.WHATSAPP
        }
        val launch = pm.getLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(other)
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return safeStart(context, launch)
    }

    fun safeStart(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
