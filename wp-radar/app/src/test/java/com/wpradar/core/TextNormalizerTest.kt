package com.wpradar.core

import org.junit.Assert.assertEquals
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
}
