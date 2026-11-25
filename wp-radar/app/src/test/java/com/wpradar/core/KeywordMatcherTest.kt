package com.wpradar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordMatcherTest {
    private fun group(name: String?, text: String) =
        ParsedMessage("com.whatsapp", name, "Ali", text, 1L, isGroup = true)

    private fun private(text: String) =
        ParsedMessage("com.whatsapp", null, "Ali", text, 1L, isGroup = false)

    private fun matcher(
        groups: List<String> = emptyList(),
        keywords: List<String>,
        fold: Boolean = true,
        includePrivate: Boolean = false,
        enabled: Boolean = true,
    ) = KeywordMatcher(
        RadarSettings(
            enabled = enabled,
            groups = groups,
            keywords = keywords,
            foldDiacritics = fold,
            includePrivateChats = includePrivate,
        ),
    )

    @Test fun emptyGroupListMeansAllGroups() {
        assertEquals("iclas", matcher(keywords = listOf("iclas")).match(group("Hər hansı", "Sabah İCLAS var")))
    }

    @Test fun groupFilterUsesPartOfName() {
        val m = matcher(groups = listOf("ofis"), keywords = listOf("iclas"))
        assertEquals("iclas", m.match(group("🏢 OFİS İşçiləri", "iclas saat 5")))
        assertNull(m.match(group("Ailə", "iclas saat 5")))
        assertNull(m.match(group(null, "iclas saat 5")))
    }

    @Test fun privateChatsOnlyWhenEnabled() {
        assertNull(matcher(keywords = listOf("pul")).match(private("pul lazımdır")))
        assertEquals("pul", matcher(keywords = listOf("pul"), includePrivate = true).match(private("PUL lazımdır")))
    }

    @Test fun strictModeKeepsDottedAndDotlessIApart() {
        val m = matcher(keywords = listOf("iş"), fold = false)
        assertNull(m.match(group("A", "IŞ")))
        assertEquals("iş", m.match(group("A", "İŞ yoxdur")))
    }

    @Test fun foldModeIgnoresLetterDifferences() {
        val m = matcher(keywords = listOf("Gəncə"))
        assertEquals("Gəncə", m.match(group("A", "sabah GENCE-ye gedirik")))
        assertNull(matcher(keywords = listOf("Gəncə"), fold = false).match(group("A", "GENCE")))
    }

    @Test fun returnsKeywordAsTyped() {
        assertEquals("TƏCİLİ", matcher(keywords = listOf("  ", "TƏCİLİ")).match(group("A", "təcili zəng edin")))
    }

    @Test fun noKeywordsOrDisabledMeansNoMatch() {
        assertNull(matcher(keywords = emptyList()).match(group("A", "nə olursa")))
        assertNull(matcher(keywords = listOf("a"), enabled = false).match(group("A", "a")))
    }
}
