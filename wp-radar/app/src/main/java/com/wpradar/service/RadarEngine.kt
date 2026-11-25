package com.wpradar.service

import android.app.PendingIntent
import com.wpradar.WpRadarApp
import com.wpradar.core.KeywordMatcher
import com.wpradar.core.NotificationParser
import com.wpradar.core.RawNotification
import com.wpradar.data.HistoryEntry
import com.wpradar.notify.AlertNotifier

object RadarEngine {

    suspend fun handle(app: WpRadarApp, raw: RawNotification, contentIntent: PendingIntent?) {
        val messages = NotificationParser.parse(raw)
        if (messages.isEmpty()) return

        // Ayarlarda "son görülən qruplar" siyahısı üçün.
        messages.asSequence()
            .filter { it.isGroup }
            .mapNotNull { it.group }
            .distinct()
            .forEach { app.settings.noteGroupSeen(it) }

        val settings = app.settings.current()
        if (!settings.enabled) return
        val matcher = KeywordMatcher(settings)
        val now = System.currentTimeMillis()

        val hits = messages
            .mapNotNull { m -> matcher.match(m)?.let { keyword -> m to keyword } }
            .filter { (m, _) -> !app.seen.checkAndMark(m, now) }

        hits.forEachIndexed { index, (msg, keyword) ->
            // Bir bildirişdə bir neçə yeni uyğunluq varsa, yalnız sonuncusu səslənir.
            AlertNotifier.showMatch(
                context = app,
                msg = msg,
                keyword = keyword,
                loud = settings.loudMode,
                original = contentIntent,
                silent = index < hits.lastIndex,
            )
            app.history.add(HistoryEntry.from(msg, keyword, now))
        }
    }
}
