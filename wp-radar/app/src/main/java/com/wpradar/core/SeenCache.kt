package com.wpradar.core

/**
 * Eyni mesaja iki dəfə xəbərdarlıq getməsin deyə görülən mesajların açarları.
 *
 * WhatsApp qrup bildirişini hər yeni mesajda yenidən göndərir və köhnə mesajlar da içində olur.
 * - MessagingStyle mesajının öz vaxtı var → "dəqiq açar" (paket + qrup + göndərən + vaxt + mətn).
 * - Əlavə olaraq "məzmun açarı": eyni qrup + göndərən + mətn qısa müddətdə təkrar gəlsə, yenə susuruq
 *   (vaxtı olan mesajlar üçün 2 dəq., vaxtı olmayan köhnə format üçün 6 saat).
 */
class SeenCache(
    private val retentionMs: Long = 3 * DAY,
    private val maxEntries: Int = 2000,
) {
    private val entries = LinkedHashMap<String, Long>()

    val size: Int get() = entries.size

    /** true → bu mesaj artıq görülüb (xəbərdarlıq göndərmə). false → yenidir və indi qeyd olundu. */
    fun checkAndMark(msg: ParsedMessage, now: Long): Boolean {
        prune(now)
        val exact = DedupKeys.exact(msg)
        val content = DedupKeys.content(msg)

        if (exact != null && entries.containsKey(exact)) {
            touch(exact, now)
            return true
        }
        val window = if (msg.timestamp != null) CONTENT_WINDOW_TIMESTAMPED else CONTENT_WINDOW_UNTIMED
        val contentSeenAt = entries[content]
        if (contentSeenAt != null && now - contentSeenAt < window) {
            exact?.let { touch(it, now) }
            if (msg.timestamp == null) touch(content, now)
            return true
        }
        exact?.let { touch(it, now) }
        touch(content, now)
        trim()
        return false
    }

    fun serialize(): String = buildString {
        for ((key, time) in entries) append(key).append('\t').append(time).append('\n')
    }

    fun load(text: String) {
        entries.clear()
        for (line in text.lineSequence()) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val time = line.substring(tab + 1).trim().toLongOrNull() ?: continue
            entries[line.substring(0, tab)] = time
        }
        trim()
    }

    private fun touch(key: String, now: Long) {
        entries.remove(key)
        entries[key] = now
    }

    private fun prune(now: Long) {
        val it = entries.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > retentionMs) it.remove()
        }
    }

    private fun trim() {
        val it = entries.keys.iterator()
        while (entries.size > maxEntries && it.hasNext()) {
            it.next()
            it.remove()
        }
    }

    companion object {
        const val DAY = 86_400_000L
        const val CONTENT_WINDOW_TIMESTAMPED = 2 * 60_000L
        const val CONTENT_WINDOW_UNTIMED = 6 * 60 * 60_000L
    }
}

object DedupKeys {
    fun exact(m: ParsedMessage): String? = m.timestamp?.let { key("e", m, it.toString()) }

    fun content(m: ParsedMessage): String = key("c", m, "")

    /** Eyni mesaj üçün həmişə eyni bildiriş ID-si (təsadüfən təkrar olsa, köhnəni əvəz edir). */
    fun notificationId(m: ParsedMessage): Int = (exact(m) ?: content(m)).hashCode() and 0x7FFFFFFF

    private fun key(kind: String, m: ParsedMessage, extra: String): String {
        val raw = listOf(kind, m.packageName, norm(m.group), norm(m.sender), extra, norm(m.text))
            .joinToString("\u001F")
        return kind + java.lang.Long.toHexString(fnv1a64(raw))
    }

    private fun norm(s: String?): String = TextNormalizer.normalize(s, foldDiacritics = false)

    private fun fnv1a64(s: String): Long {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }
}
