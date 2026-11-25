package com.wpradar.core

/** Bildirişdən çıxarılmış bir MessagingStyle mesajı (Android-dan asılı deyil). */
data class RawMessage(
    val sender: String?,
    val text: String?,
    val timestamp: Long,
    /** Mesajı telefonun sahibi özü yazıb (MessagingStyle-da göndərən boşdur). */
    val fromSelf: Boolean,
)

/** StatusBarNotification-ın bizə lazım olan hissəsi — sırf mətn, test etmək asandır. */
data class RawNotification(
    val packageName: String,
    val title: String?,
    val conversationTitle: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String> = emptyList(),
    val messages: List<RawMessage> = emptyList(),
    val isGroupConversation: Boolean = false,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val category: String? = null,
)

/** Təmizlənmiş, yoxlanmağa hazır mesaj. */
data class ParsedMessage(
    val packageName: String,
    val group: String?,
    val sender: String?,
    val text: String,
    /** Mesajın öz vaxtı (MessagingStyle-dan). Köhnə formatda olmur. */
    val timestamp: Long?,
    val isGroup: Boolean,
)

data class RadarSettings(
    val enabled: Boolean = true,
    val groups: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    /** ə=e, ı=i, ş=s, ç=c, ğ=g, ö=o, ü=u kimi müqayisə et. */
    val foldDiacritics: Boolean = true,
    val includePrivateChats: Boolean = false,
    val loudMode: Boolean = false,
    val recentGroups: List<String> = emptyList(),
)
