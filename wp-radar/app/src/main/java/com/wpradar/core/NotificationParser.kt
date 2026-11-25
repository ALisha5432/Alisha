package com.wpradar.core

/**
 * WhatsApp bildirişindən qrup adı, göndərən və mesaj(lar)ı çıxarır.
 *
 * Əsas yol MessagingStyle-dır (müasir WhatsApp): hər mesajın öz göndərəni və vaxtı var.
 * Olmasa, EXTRA_CONVERSATION_TITLE / EXTRA_TITLE / EXTRA_BIG_TEXT / EXTRA_TEXT / EXTRA_TEXT_LINES
 * üzərindən köhnə formatlar başa düşülür:
 *   başlıq "Ali @ Ailə", mətn "salam"
 *   başlıq "Ailə", mətn "Ali: salam"
 *   sətir  "Ali @ Ailə: salam" (InboxStyle)
 */
object NotificationParser {
    /** "Ailə (3 mesaj)", "Ofis (5 messages)" → sondakı sayğacı at. "(2024)" toxunulmaz qalır. */
    private val countSuffix = Regex("""\s*\(\d+\s+[\p{L}\s]{1,30}\)""")
    private val senderAtGroupLine = Regex("""^(.{1,80}?) @ (.{1,120}?): (.+)$""", RegexOption.DOT_MATCHES_ALL)
    private val senderPrefixLine = Regex("""^([^:\n]{1,60}): (.+)$""", RegexOption.DOT_MATCHES_ALL)
    private val whitespace = Regex("\\s+")
    private val skippedCategories = setOf("call", "missed_call", "progress", "service", "status", "sys")
    private val appTitles = setOf("whatsapp", "whatsapp business")

    fun cleanName(value: CharSequence?): String? {
        if (value == null) return null
        val s = countSuffix.replace(TextNormalizer.stripInvisible(value.toString()), "")
        return whitespace.replace(s, " ").trim().ifEmpty { null }
    }

    fun cleanText(value: CharSequence?): String? {
        if (value == null) return null
        return TextNormalizer.stripInvisible(value.toString()).trim().ifEmpty { null }
    }

    fun parse(raw: RawNotification): List<ParsedMessage> {
        // Qrup xülasəsi ("3 söhbətdən 5 mesaj") ayrı-ayrı bildirişləri təkrarlayır — atlayırıq.
        if (raw.isGroupSummary || raw.isOngoing) return emptyList()
        if (raw.category != null && raw.category in skippedCategories) return emptyList()
        val title = cleanName(raw.title)
        val conversation = cleanName(raw.conversationTitle)
        return if (raw.messages.isNotEmpty()) {
            fromMessagingStyle(raw, title, conversation)
        } else {
            fromPlainExtras(raw, title, conversation)
        }
    }

    private fun fromMessagingStyle(
        raw: RawNotification,
        title: String?,
        conversation: String?,
    ): List<ParsedMessage> {
        val isGroup = raw.isGroupConversation || conversation != null
        val group = when {
            !isGroup -> null
            conversation != null -> conversation
            else -> splitTitle(title).first
        }
        // Bildirişdən cavab yazanda sizin öz mesajınız da siyahıya düşür — onu yoxlamırıq.
        val hasOthers = raw.messages.any { !it.fromSelf }
        return raw.messages.mapNotNull { m ->
            if (m.fromSelf && hasOthers) return@mapNotNull null
            val text = cleanText(m.text) ?: return@mapNotNull null
            val sender = cleanName(m.sender) ?: if (isGroup) senderFromTitle(title, group) else title
            ParsedMessage(raw.packageName, group, sender, text, m.timestamp.takeIf { it > 0L }, isGroup)
        }
    }

    private fun fromPlainExtras(
        raw: RawNotification,
        title: String?,
        conversation: String?,
    ): List<ParsedMessage> {
        val isAppTitle = conversation == null && title != null && title.lowercase() in appTitles
        val lines = raw.textLines.mapNotNull { cleanText(it) }
            .ifEmpty { listOfNotNull(cleanText(raw.bigText) ?: cleanText(raw.text)) }

        val group: String?
        val sender: String?
        val isGroup: Boolean
        when {
            conversation != null -> {
                group = conversation
                sender = senderFromTitle(title, conversation)
                isGroup = true
            }
            title != null && (raw.isGroupConversation || title.contains(" @ ")) -> {
                val (g, s) = splitTitle(title)
                group = g
                sender = s
                isGroup = true
            }
            else -> {
                group = null
                sender = title
                isGroup = false
            }
        }
        val linesMayCarryGroup = isAppTitle || isGroup || raw.textLines.isNotEmpty()

        return lines.mapNotNull { line ->
            if (linesMayCarryGroup) {
                senderAtGroupLine.matchEntire(line)?.let { m ->
                    val text = m.groupValues[3].trim()
                    if (text.isEmpty()) return@mapNotNull null
                    return@mapNotNull ParsedMessage(
                        raw.packageName,
                        cleanName(m.groupValues[2]),
                        cleanName(m.groupValues[1]),
                        text,
                        null,
                        true,
                    )
                }
            }
            // "WhatsApp" başlıqlı ümumi bildiriş (məs. "5 yeni mesaj") — mesaj deyil.
            if (isAppTitle) return@mapNotNull null

            var lineSender = sender
            var text = line
            if (isGroup && sender == null) {
                senderPrefixLine.matchEntire(line)?.let { m ->
                    lineSender = cleanName(m.groupValues[1])
                    text = m.groupValues[2].trim()
                }
            }
            if (text.isEmpty()) null else ParsedMessage(raw.packageName, group, lineSender, text, null, isGroup)
        }
    }

    /** "Ali @ Ailə" → (Ailə, Ali); "Ailə: Ali" → (Ailə, Ali); "Ailə" → (Ailə, null). */
    private fun splitTitle(title: String?): Pair<String?, String?> {
        if (title == null) return null to null
        val at = title.indexOf(" @ ")
        if (at > 0) return cleanName(title.substring(at + 3)) to cleanName(title.substring(0, at))
        val colon = title.indexOf(": ")
        if (colon > 0) return cleanName(title.substring(0, colon)) to cleanName(title.substring(colon + 2))
        return title to null
    }

    /** Qrup adı məlum olanda başlıqdan göndərəni tapır: "Ailə: Ali", "Ali @ Ailə", "Ali". */
    private fun senderFromTitle(title: String?, group: String?): String? {
        if (title == null) return null
        if (group == null) return title
        if (title == group) return null
        if (title.startsWith("$group: ")) return cleanName(title.substring(group.length + 2))
        if (title.endsWith(" @ $group")) return cleanName(title.substring(0, title.length - group.length - 3))
        return title
    }
}
