package com.wpradar.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.wpradar.core.RawMessage
import com.wpradar.core.RawNotification

/** StatusBarNotification → RawNotification (EXTRA_* sahələri və MessagingStyle mesajları). */
object NotificationExtractor {

    fun extract(sbn: StatusBarNotification): RawNotification {
        val n: Notification = sbn.notification
        val extras: Bundle = n.extras ?: Bundle()

        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()

        val messages = style?.messages.orEmpty().map { m ->
            RawMessage(
                sender = m.person?.name?.toString(),
                text = m.text?.toString(),
                timestamp = m.timestamp,
                // MessagingStyle-da göndərəni olmayan mesaj telefon sahibinin özünündür.
                fromSelf = m.person == null,
            )
        }

        val conversationTitle = style?.conversationTitle
            ?: extras.getCharSequence(NotificationCompat.EXTRA_CONVERSATION_TITLE)
        val isGroup = style?.isGroupConversation
            ?: extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION, false)

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)

        return RawNotification(
            packageName = sbn.packageName,
            title = title?.toString(),
            conversationTitle = conversationTitle?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.mapNotNull { it?.toString() }
                .orEmpty(),
            messages = messages,
            isGroupConversation = isGroup,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            category = n.category,
        )
    }
}
