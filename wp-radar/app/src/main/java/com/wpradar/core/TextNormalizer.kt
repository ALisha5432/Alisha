package com.wpradar.core

import java.text.Normalizer

/**
 * Azərbaycan/Türk qaydaları ilə kiçik hərfə çevirmə.
 *
 * Standart lowercase() "I"-ni "i"-yə, "İ"-ni isə "i̇"-yə (i + nöqtə) çevirir və axtarışı pozur.
 * Burada: İ → i, I → ı, Ə/Ǝ → ə. "Hərf fərqlərinə baxma" rejimində əlavə olaraq:
 * kiril → latın (ортада → ortada), sonra ə→e, ı→i, ş→s, ç→c, ğ→g, ö→o, ü→u.
 */
object TextNormalizer {
    // Görünməz simvollar: soft hyphen, zero-width, LRM/RLM, bidi embedding/isolate, BOM.
    private val invisible = Regex("[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]")
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    private const val CAPITAL_I_DOT = 'İ'      // İ
    private const val SMALL_DOTLESS_I = 'ı'    // ı
    private const val CAPITAL_SCHWA = 'Ə'      // Ə
    private const val CAPITAL_REVERSED_E = 'Ǝ' // Ǝ (səhvən Ə yerinə yazılır)
    private const val SMALL_TURNED_E = 'ǝ'     // ǝ (səhvən ə yerinə yazılır)
    private const val SMALL_SCHWA = 'ə'        // ə
    private const val COMBINING_DOT_ABOVE = '̇'

    /** Azərbaycan kiril əlifbası (+ rus hərfləri) → latın. Kiçik hərflər üçün. */
    private val cyrillic: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "q", 'д' to "d",
        'е' to "e", 'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y",
        'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "ç", 'ш' to "ş",
        'щ' to "şç", 'ъ' to "", 'ы' to "ı", 'ь' to "", 'э' to "e",
        'ю' to "yu", 'я' to "ya", 'ё' to "yo", 'і' to "i", 'ј' to "y",
        'ғ' to "ğ", 'ҝ' to "g", 'ү' to "ü", 'ҹ' to "c", 'һ' to "h",
        'ә' to "ə", 'ө' to "ö",
    )

    fun stripInvisible(input: String): String = invisible.replace(input, "")

    fun normalize(input: CharSequence?, foldDiacritics: Boolean): String {
        if (input.isNullOrEmpty()) return ""
        val composed = Normalizer.normalize(stripInvisible(input.toString()), Normalizer.Form.NFC)
        val sb = StringBuilder(composed.length)
        for (ch in composed) {
            when (ch) {
                CAPITAL_I_DOT -> sb.append('i')
                'I' -> sb.append(SMALL_DOTLESS_I)
                CAPITAL_SCHWA, CAPITAL_REVERSED_E, SMALL_TURNED_E -> sb.append(SMALL_SCHWA)
                // "i̇" (i + nöqtə) — başqa proqramın səhv lowercase-i; nöqtəni at.
                COMBINING_DOT_ABOVE -> if (sb.isEmpty() || sb[sb.length - 1] != 'i') sb.append(ch)
                else -> {
                    val lower = ch.lowercaseChar()
                    val latin = if (foldDiacritics) cyrillic[lower] else null
                    if (latin != null) sb.append(latin) else sb.append(lower)
                }
            }
        }
        var out = sb.toString()
        if (foldDiacritics) {
            out = out.replace(SMALL_SCHWA, 'e').replace(SMALL_DOTLESS_I, 'i')
            // ş→s, ç→c, ğ→g, ö→o, ü→u (və digər vurğulu hərflər)
            out = combiningMarks.replace(Normalizer.normalize(out, Normalizer.Form.NFD), "")
        }
        return whitespace.replace(out, " ").trim()
    }

    /** Mətndə açar sözün ilk yerini tapır (Tapılanlarda rəngləmək üçün). */
    fun findMatch(text: String, keyword: String, foldDiacritics: Boolean): IntRange? {
        val target = normalize(keyword, foldDiacritics)
        if (target.isEmpty()) return null
        val first = target[0]
        for (i in text.indices) {
            val head = normalize(text[i].toString(), foldDiacritics)
            if (head.isEmpty() || head[0] != first) continue
            var end = i + 1
            while (end <= text.length) {
                val piece = normalize(text.substring(i, end), foldDiacritics)
                if (piece == target) return i until end
                if (piece.length > target.length || !target.startsWith(piece)) break
                end++
            }
        }
        return null
    }
}
