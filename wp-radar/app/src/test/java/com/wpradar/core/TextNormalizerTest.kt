package com.wpradar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {
    private fun strict(s: String) = TextNormalizer.normalize(s, foldDiacritics = false)
    private fun loose(s: String) = TextNormalizer.normalize(s, foldDiacritics = true)

    @Test fun dottedCapitalIBecomesI() = assertEquals("istanbul", strict("İSTANBUL"))

    @Test fun plainCapitalIBecomesDotlessI() = assertEquals("ısparta", strict("ISPARTA"))

    @Test fun azerbaijaniLetters() = assertEquals("şəki çay ğöü", strict("ŞƏKİ  ÇAY ĞÖÜ"))

    @Test fun foldedLetters() = assertEquals("seki cay gou isiq", loose("ŞƏKİ ÇAY ĞÖÜ IŞIQ"))

    @Test fun decomposedLettersAreComposed() = assertEquals("şe", strict("ŞE"))

    @Test fun brokenLowercaseDottedI() {
        assertEquals("istanbul", strict("i̇stanbul"))
        assertEquals("istanbul", strict("İstanbul"))
    }

    @Test fun invisibleCharactersRemoved() = assertEquals("iş", strict("‎İ​Ş‏"))

    @Test fun lookalikeSchwa() {
        assertEquals("əli", strict("ǝli"))
        assertEquals("əli", strict("ƎLİ"))
    }

    @Test fun nullAndEmpty() {
        assertEquals("", TextNormalizer.normalize(null, true))
        assertEquals("", strict("   "))
    }

    /** ОРТАДА, ҜӘНҸӘ, шәһәр — kiril ilə yazılmış mesajlar da tapılsın. */
    @Test fun cyrillicFoldsToLatin() {
        assertEquals("ortada", loose("ОРТАДА"))
        assertEquals("gence", loose("ҜӘНҸӘ"))
        assertEquals("seher", loose("шәһәр"))
    }

    @Test fun cyrillicKeptInStrictMode() =
        assertEquals("ортада", strict("ОРТАДА"))

    @Test fun latinUnchangedByCyrillicMap() = assertEquals("pecxoya", loose("PECXOYA"))

    @Test fun findMatchLocatesKeyword() {
        assertEquals(7..12, TextNormalizer.findMatch("Bu gün ORTADA nə var?", "ortada", true))
        assertEquals(6..10, TextNormalizer.findMatch("Sabah İCLAS var", "iclas", false))
        assertEquals(0..4, TextNormalizer.findMatch("gence-ye gedirik", "Gəncə", true))
        assertEquals(0..12, TextNormalizer.findMatch("sabah   iclas", "sabah iclas", false))
        assertEquals(3..8, TextNormalizer.findMatch("bu ортада", "ortada", true))
        assertNull(TextNormalizer.findMatch("salam", "iclas", true))
        assertNull(TextNormalizer.findMatch("salam", "   ", true))
    }
}
