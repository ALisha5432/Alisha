package com.wpradar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenCacheTest {
    private fun msg(text: String, ts: Long?, sender: String = "Ali", group: String = "Ailə") =
        ParsedMessage("com.whatsapp", group, sender, text, ts, isGroup = true)

    private val min = 60_000L
    private val t0 = 1_700_000_000_000L

    @Test fun sameMessageRepostedIsDuplicate() {
        val c = SeenCache()
        assertFalse(c.checkAndMark(msg("iclas", 100L), t0))
        // WhatsApp yeni mesaj gələndə köhnəni də yenidən göndərir — saatlar sonra belə.
        assertTrue(c.checkAndMark(msg("iclas", 100L), t0 + 5 * 60 * min))
        assertTrue(c.checkAndMark(msg("İCLAS‎", 100L), t0 + 6 * 60 * min))
    }

    @Test fun differentMessagesAreNotDuplicates() {
        val c = SeenCache()
        assertFalse(c.checkAndMark(msg("iclas", 100L), t0))
        assertFalse(c.checkAndMark(msg("iclas sabah", 200L), t0))
        assertFalse(c.checkAndMark(msg("iclas", 300L, sender = "Vəli"), t0))
        assertFalse(c.checkAndMark(msg("iclas", 400L, group = "Ofis"), t0))
    }

    @Test fun sameTextNewTimestampShortlyAfterIsDuplicate() {
        val c = SeenCache()
        assertFalse(c.checkAndMark(msg("iclas", 100L), t0))
        assertTrue(c.checkAndMark(msg("iclas", 160L), t0 + min))
        assertFalse(c.checkAndMark(msg("iclas", 900L), t0 + 3 * min))
    }

    @Test fun untimedMessagesUseLongerWindow() {
        val c = SeenCache()
        assertFalse(c.checkAndMark(msg("iclas", null), t0))
        assertTrue(c.checkAndMark(msg("iclas", null), t0 + 60 * min))
        assertFalse(c.checkAndMark(msg("iclas", null), t0 + 60 * min + SeenCache.CONTENT_WINDOW_UNTIMED))
    }

    @Test fun entriesExpire() {
        val c = SeenCache()
        assertFalse(c.checkAndMark(msg("iclas", 100L), t0))
        assertFalse(c.checkAndMark(msg("iclas", 100L), t0 + 4 * SeenCache.DAY))
    }

    @Test fun survivesSerialization() {
        val a = SeenCache()
        assertFalse(a.checkAndMark(msg("iclas", 100L), t0))
        val b = SeenCache().apply { load(a.serialize()) }
        assertEquals(a.size, b.size)
        assertTrue(b.checkAndMark(msg("iclas", 100L), t0 + 10 * min))
    }

    @Test fun sizeIsCapped() {
        val c = SeenCache(maxEntries = 10)
        repeat(50) { c.checkAndMark(msg("m$it", it.toLong() + 1), t0) }
        assertEquals(10, c.size)
    }

    @Test fun notificationIdIsStable() {
        assertEquals(DedupKeys.notificationId(msg("a", 1L)), DedupKeys.notificationId(msg("a", 1L)))
        assertNotEquals(DedupKeys.notificationId(msg("a", 1L)), DedupKeys.notificationId(msg("a", 2L)))
        assertTrue(DedupKeys.notificationId(msg("a", 1L)) >= 0)
    }
}
