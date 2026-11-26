package com.wpradar.service

import android.app.PendingIntent
import android.util.Log
import com.wpradar.WpRadarApp
import com.wpradar.core.DedupKeys
import com.wpradar.core.KeywordMatcher
import com.wpradar.core.NotificationParser
import com.wpradar.core.ParsedMessage
import com.wpradar.core.RawNotification
import com.wpradar.core.Verdict
import com.wpradar.data.HistoryEntry
import com.wpradar.data.JournalEntry
import com.wpradar.notify.AlertNotifier
import com.wpradar.system.SystemStatus

object RadarEngine {
    private const val TAG = "WpRadar"

    private data class Outcome(val msg: ParsedMessage, val verdict: Verdict, val keyword: String?)

    suspend fun handle(app: WpRadarApp, raw: RawNotification, contentIntent: PendingIntent?) {
        val now = System.currentTimeMillis()
        val messages = NotificationParser.parse(raw)
        if (messages.isEmpty()) {
            if (!NotificationParser.isIgnorable(raw)) recordUnreadable(app, raw, now)
            return
        }

        val settings = app.settings.current()
        val matcher = KeywordMatcher(settings)
        val canNotify = SystemStatus.canPostNotifications(app)

        val results = messages.map { msg ->
            val evaluation = matcher.evaluate(msg)
            val verdict = when {
                evaluation.verdict != Verdict.MATCHED -> evaluation.verdict
                app.seen.checkAndMark(msg, now) -> Verdict.DUPLICATE
                !canNotify -> Verdict.CANNOT_NOTIFY
                else -> Verdict.MATCHED
            }
            Outcome(msg, verdict, evaluation.keyword)
        }

        // 1) Ən vacibi — xəbərdarlıq. Bir bildirişdə bir neçə yeni uyğunluq varsa,
        //    yalnız sonuncusu səslənir.
        val alerts = results.filter { it.verdict == Verdict.MATCHED }
        alerts.forEachIndexed { index, r ->
            try {
                AlertNotifier.showMatch(
                    context = app,
                    msg = r.msg,
                    keyword = r.keyword.orEmpty(),
                    loud = settings.loudMode,
                    repeat = settings.repeatAlert,
                    original = contentIntent,
                    silent = index < alerts.lastIndex,
                )
            } catch (e: Exception) {
                Log.e(TAG, "alert failed", e)
            }
        }

        // 2) Tapılanlar
        for (r in results) {
            if (r.verdict != Verdict.MATCHED && r.verdict != Verdict.CANNOT_NOTIFY) continue
            try {
                app.history.add(HistoryEntry.from(r.msg, r.keyword.orEmpty(), now))
            } catch (e: Exception) {
                Log.e(TAG, "history failed", e)
            }
        }

        // 3) Jurnal
        app.journal.record(
            results.map { r ->
                val key = DedupKeys.exact(r.msg) ?: DedupKeys.content(r.msg)
                key to JournalEntry(
                    id = app.journal.nextId(),
                    time = r.msg.timestamp ?: now,
                    packageName = r.msg.packageName,
                    group = r.msg.group,
                    sender = r.msg.sender,
                    text = if (r.verdict == Verdict.PRIVATE_SKIPPED) null else r.msg.text.take(MAX_TEXT),
                    isGroup = r.msg.isGroup,
                    verdict = r.verdict,
                    keyword = r.keyword,
                )
            },
        )

        // 4) Ayarlardakı "WhatsApp-da görülən qruplar" siyahısı
        for (group in messages.filter { it.isGroup }.mapNotNull { it.group }.distinct()) {
            try {
                app.settings.noteGroupSeen(group)
            } catch (e: Exception) {
                Log.w(TAG, "recent group failed", e)
            }
        }
    }

    private fun recordUnreadable(app: WpRadarApp, raw: RawNotification, now: Long) {
        val title = NotificationParser.cleanName(raw.conversationTitle) ?: NotificationParser.cleanName(raw.title)
        val text = NotificationParser.cleanText(raw.bigText ?: raw.text)
        val key = "u|${raw.packageName}|$title|$text"
        app.journal.record(
            listOf(
                key to JournalEntry(
                    id = app.journal.nextId(),
                    time = now,
                    packageName = raw.packageName,
                    group = title,
                    sender = null,
                    text = text?.take(MAX_TEXT),
                    isGroup = true,
                    verdict = Verdict.UNREADABLE,
                    keyword = null,
                ),
            ),
        )
    }

    private const val MAX_TEXT = 500
}
