package com.wpradar.core

import java.text.Normalizer

/**
 * Azərbaycan/Türk qaydaları ilə kiçik hərfə çevirmə.
 *
 * Standart lowercase() "I"-ni "i"-yə, "İ"-ni isə "i̇"-yə (i + nöqtə) çevirir və axtarışı pozur.
 * Burada: İ → i, I → ı, Ə/Ǝ → ə. Sonra (istəyə görə) diakritiklər atılır: ə→e, ı→i, ş→s ...
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
                else -> sb.append(ch.lowercaseChar())
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
}
