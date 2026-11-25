package com.wpradar.core

/**
 * Mesajın izlənən qrupdan olub-olmadığını və içində açar söz olub-olmadığını yoxlayır.
 * Qrup siyahısı boşdursa — bütün qruplar. Qrup adının bir hissəsini yazmaq kifayətdir.
 */
class KeywordMatcher(private val settings: RadarSettings) {
    private val fold = settings.foldDiacritics
    private val groups = settings.groups
        .map { TextNormalizer.normalize(it, fold) }
        .filter { it.isNotEmpty() }
    private val keywords = settings.keywords
        .map { it to TextNormalizer.normalize(it, fold) }
        .filter { it.second.isNotEmpty() }

    fun isChatWatched(msg: ParsedMessage): Boolean = when {
        !msg.isGroup -> settings.includePrivateChats
        groups.isEmpty() -> true
        else -> {
            val g = TextNormalizer.normalize(msg.group, fold)
            g.isNotEmpty() && groups.any { g.contains(it) }
        }
    }

    /** Tapılan açar sözü (istifadəçinin yazdığı kimi) qaytarır, yoxdursa null. */
    fun match(msg: ParsedMessage): String? {
        if (!settings.enabled || keywords.isEmpty()) return null
        if (!isChatWatched(msg)) return null
        val text = TextNormalizer.normalize(msg.text, fold)
        return keywords.firstOrNull { text.contains(it.second) }?.first
    }
}
