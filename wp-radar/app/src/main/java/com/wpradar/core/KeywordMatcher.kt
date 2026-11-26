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

    /** Qərar və (uyğunluq varsa) istifadəçinin yazdığı açar söz. */
    fun evaluate(msg: ParsedMessage): Evaluation {
        if (!settings.enabled) return Evaluation(Verdict.DISABLED)
        if (keywords.isEmpty()) return Evaluation(Verdict.NO_KEYWORDS)
        if (!msg.isGroup && !settings.includePrivateChats) return Evaluation(Verdict.PRIVATE_SKIPPED)
        if (!isChatWatched(msg)) return Evaluation(Verdict.CHAT_NOT_WATCHED)
        val text = TextNormalizer.normalize(msg.text, fold)
        val hit = keywords.firstOrNull { text.contains(it.second) } ?: return Evaluation(Verdict.NO_KEYWORD)
        return Evaluation(Verdict.MATCHED, hit.first)
    }

    /** Tapılan açar söz, yoxdursa null. */
    fun match(msg: ParsedMessage): String? =
        evaluate(msg).takeIf { it.verdict == Verdict.MATCHED }?.keyword
}
