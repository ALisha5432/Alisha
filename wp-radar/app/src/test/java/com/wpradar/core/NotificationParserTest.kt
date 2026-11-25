package com.wpradar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private fun raw(
        title: String? = null,
        conversation: String? = null,
        text: String? = null,
        bigText: String? = null,
        lines: List<String> = emptyList(),
        messages: List<RawMessage> = emptyList(),
        isGroup: Boolean = false,
        summary: Boolean = false,
        category: String? = "msg",
    ) = RawNotification(
        packageName = "com.whatsapp",
        title = title,
        conversationTitle = conversation,
        text = text,
        bigText = bigText,
        textLines = lines,
        messages = messages,
        isGroupConversation = isGroup,
        isGroupSummary = summary,
        category = category,
    )

    @Test fun messagingStyleGroup() {
        val result = NotificationParser.parse(
            raw(
                title = "Ailə (2 mesaj): Vəli",
                conversation = "‎Ailə (2 mesaj)",
                isGroup = true,
                messages = listOf(
                    RawMessage("Ali", "salam", 1000L, fromSelf = false),
                    RawMessage("~ Vəli", "iclas saat 5-də", 2000L, fromSelf = false),
                ),
            ),
        )
        assertEquals(2, result.size)
        assertEquals("Ailə", result[0].group)
        assertEquals("Ali", result[0].sender)
        assertEquals(1000L, result[0].timestamp)
        assertTrue(result[0].isGroup)
        assertEquals("~ Vəli", result[1].sender)
        assertEquals("iclas saat 5-də", result[1].text)
    }

    @Test fun ownRepliesAreSkipped() {
        val result = NotificationParser.parse(
            raw(
                conversation = "Ofis",
                isGroup = true,
                messages = listOf(
                    RawMessage("Ali", "iclas?", 1L, fromSelf = false),
                    RawMessage(null, "iclas yoxdur", 2L, fromSelf = true),
                ),
            ),
        )
        assertEquals(listOf("iclas?"), result.map { it.text })
    }

    @Test fun messagingStylePrivateChat() {
        val result = NotificationParser.parse(
            raw(title = "Ali", messages = listOf(RawMessage("Ali", "salam", 5L, fromSelf = false))),
        )
        assertEquals(1, result.size)
        assertFalse(result[0].isGroup)
        assertNull(result[0].group)
        assertEquals("Ali", result[0].sender)
    }

    @Test fun legacyAtTitle() {
        val m = NotificationParser.parse(raw(title = "Ali @ Ailə", text = "iclas saat 5")).single()
        assertEquals("Ailə", m.group)
        assertEquals("Ali", m.sender)
        assertEquals("iclas saat 5", m.text)
        assertTrue(m.isGroup)
        assertNull(m.timestamp)
    }

    @Test fun legacyInboxLines() {
        val result = NotificationParser.parse(
            raw(
                title = "WhatsApp",
                text = "2 söhbətdən 3 mesaj",
                lines = listOf("Ali @ Ailə: salam", "Vəli @ Ofis: iclas", "5 yeni mesaj"),
            ),
        )
        assertEquals(listOf("Ailə", "Ofis"), result.map { it.group })
        assertEquals(listOf("Ali", "Vəli"), result.map { it.sender })
    }

    @Test fun legacyConversationTitleWithSenderInTitle() {
        val m = NotificationParser.parse(
            raw(title = "Ofis: Vəli", conversation = "Ofis", text = "x", bigText = "iclas sabah"),
        ).single()
        assertEquals("Ofis", m.group)
        assertEquals("Vəli", m.sender)
        assertEquals("iclas sabah", m.text)
    }

    @Test fun legacyGroupFlagWithSenderPrefixInText() {
        val m = NotificationParser.parse(raw(title = "Ofis", text = "Vəli: iclas", isGroup = true)).single()
        assertEquals("Ofis", m.group)
        assertEquals("Vəli", m.sender)
        assertEquals("iclas", m.text)
    }

    @Test fun legacyPrivateChat() {
        val m = NotificationParser.parse(raw(title = "Ali", text = "Qeyd: sabah iclas")).single()
        assertFalse(m.isGroup)
        assertEquals("Ali", m.sender)
        assertEquals("Qeyd: sabah iclas", m.text)
    }

    @Test fun summariesAndCallsAreIgnored() {
        assertTrue(NotificationParser.parse(raw(title = "WhatsApp", text = "x", summary = true)).isEmpty())
        assertTrue(NotificationParser.parse(raw(title = "Ali", text = "Gələn zəng", category = "call")).isEmpty())
    }

    @Test fun countSuffixOnlyRemovedWhenItLooksLikeCounter() {
        assertEquals("Məzunlar (2024)", NotificationParser.cleanName("Məzunlar (2024)"))
        assertEquals("Ailə", NotificationParser.cleanName("Ailə (5 messages)"))
        assertEquals("Ailə", NotificationParser.cleanName("Ailə (2 yeni mesaj)"))
    }
}
