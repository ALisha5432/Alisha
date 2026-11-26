package com.wpradar.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalDeduperTest {
    @Test fun sameVerdictLoggedOnce() {
        val d = JournalDeduper()
        assertTrue(d.shouldLog("a", Verdict.NO_KEYWORD))
        assertFalse(d.shouldLog("a", Verdict.NO_KEYWORD))
    }

    @Test fun changedVerdictLoggedAgain() {
        val d = JournalDeduper()
        assertTrue(d.shouldLog("a", Verdict.NO_KEYWORD))
        assertTrue(d.shouldLog("a", Verdict.MATCHED))
    }

    @Test fun duplicateOfAlertNotLogged() {
        val d = JournalDeduper()
        assertTrue(d.shouldLog("a", Verdict.MATCHED))
        assertFalse(d.shouldLog("a", Verdict.DUPLICATE))
        assertFalse(d.shouldLog("a", Verdict.DUPLICATE))
        // Proses yenidən başlayandan sonra gələn təkrar bir dəfə yazılır.
        assertTrue(d.shouldLog("b", Verdict.DUPLICATE))
    }

    @Test fun oldKeysForgotten() {
        val d = JournalDeduper(maxEntries = 2)
        assertTrue(d.shouldLog("a", Verdict.NO_KEYWORD))
        assertTrue(d.shouldLog("b", Verdict.NO_KEYWORD))
        assertTrue(d.shouldLog("c", Verdict.NO_KEYWORD))
        assertTrue(d.shouldLog("a", Verdict.NO_KEYWORD))
    }
}
