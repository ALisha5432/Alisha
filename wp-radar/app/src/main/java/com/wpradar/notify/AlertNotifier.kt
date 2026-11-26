package com.wpradar.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.wpradar.OpenChatActivity
import com.wpradar.R
import com.wpradar.core.DedupKeys
import com.wpradar.core.ParsedMessage
import com.wpradar.service.WaListenerService
import com.wpradar.system.SystemStatus

object AlertNotifier {
    /** Kanalın səsi/vibrasiyası yaradılandan sonra kodla dəyişmir — dəyişsək, ID-ni artırmalıyıq. */
    const val CHANNEL_NORMAL = "radar_alerts_v1"
    const val CHANNEL_LOUD = "radar_alerts_loud_v1"
    private const val NOTIFICATION_TAG = "match"

    val VIBRATION = longArrayOf(0, 500, 150, 500, 150, 900)
    private val COLOR = 0xFF00A884.toInt()

    fun channelId(loud: Boolean) = if (loud) CHANNEL_LOUD else CHANNEL_NORMAL

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val sound = Uri.parse(
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/radar_alert",
        )

        fun channel(id: String, name: String, description: String, usage: Int) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = description
                enableVibration(true)
                vibrationPattern = VIBRATION
                enableLights(true)
                lightColor = COLOR
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }

        nm.createNotificationChannels(
            listOf(
                channel(
                    CHANNEL_NORMAL,
                    "Açar söz xəbərdarlığı",
                    "WhatsApp qrupunda açar söz tapılanda — güclü səs və vibrasiya",
                    AudioAttributes.USAGE_NOTIFICATION,
                ),
                channel(
                    CHANNEL_LOUD,
                    "Açar söz (alarm səsi)",
                    "Telefon səssiz rejimdə olsa belə alarm səs axını ilə çalır",
                    AudioAttributes.USAGE_ALARM,
                ),
            ),
        )
    }

    fun showMatch(
        context: Context,
        msg: ParsedMessage,
        keyword: String,
        loud: Boolean,
        repeat: Boolean,
        original: PendingIntent?,
        silent: Boolean = false,
    ) {
        if (!SystemStatus.canPostNotifications(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val id = DedupKeys.notificationId(msg)
        val sender = msg.sender ?: "Naməlum"
        val chat = if (msg.isGroup) msg.group ?: "Qrup" else "Şəxsi çat"
        val app = if (msg.packageName == WaListenerService.WHATSAPP_BUSINESS) "WA Business" else "WhatsApp"
        val title = if (msg.isGroup) "🔔 $chat" else "🔔 $sender"
        val details = buildString {
            if (msg.isGroup) append("Qrup: ").append(chat).append('\n')
            append("Göndərən: ").append(sender).append('\n')
            append("Açar söz: ").append(keyword).append("\n\n")
            append(msg.text)
        }

        val tap = PendingIntent.getActivity(
            context,
            id,
            OpenChatActivity.intent(context, msg.packageName, original),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId(loud))
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(title)
            .setContentText("$sender: ${msg.text}")
            .setSubText("$app · «$keyword»")
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(details))
            .setTicker("$chat — $sender: ${msg.text}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (loud) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(COLOR)
            .setWhen(msg.timestamp ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setVibrate(VIBRATION)
            .setSilent(silent)
            .build()
        // Səs bildirişə toxunana və ya onu silənə qədər təkrarlanır.
        if (repeat && !silent) notification.flags = notification.flags or Notification.FLAG_INSISTENT

        nm.notify(NOTIFICATION_TAG, id, notification)
    }

    fun showTest(context: Context, loud: Boolean, repeat: Boolean) {
        val msg = ParsedMessage(
            packageName = WaListenerService.WHATSAPP,
            group = "Test qrupu",
            sender = "WP Radar",
            text = "Bu test bildirişidir. Səs və vibrasiya belə olacaq.",
            timestamp = System.currentTimeMillis(),
            isGroup = true,
        )
        showMatch(context, msg, keyword = "test", loud = loud, repeat = repeat, original = null)
    }
}
