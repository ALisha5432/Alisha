package az.novbe.radar.radar

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import az.novbe.radar.core.Assemble
import az.novbe.radar.core.Az
import az.novbe.radar.core.Demo
import az.novbe.radar.core.Parser
import az.novbe.radar.core.Screen
import az.novbe.radar.core.ShiftFilter
import az.novbe.radar.core.Tracked
import az.novbe.radar.core.Tracker
import az.novbe.radar.data.LogType
import az.novbe.radar.data.Prefs
import az.novbe.radar.data.Repo
import java.time.LocalDate
import java.time.YearMonth

/**
 * "Test" düyməsi: saxta EvoApp ekranını əsl skanın keçdiyi bütün addımlardan keçirir
 * və sonda əsl bildiriş göndərir. EvoApp-a toxunmur.
 */
object TestChain {
    data class Step(val title: String, val ok: Boolean, val detail: String)

    fun run(ctx: Context): List<Step> {
        val steps = ArrayList<Step>()
        val s = Prefs.s
        val date = LocalDate.now().plusDays(3)

        val page = Parser.parse(Demo.tree(date, s.startMin, s.endMin))
        val screenOk = page.screen == Screen.AVAILABLE
        steps += Step("Ekranı tanıma", screenOk, "\"Available Shifts\" · ${page.cards.size} kart oxundu")
        if (!screenOk) return steps

        val res = Assemble.shifts(page.tokens, page.month ?: YearMonth.from(date))
        val datesOk = res.shifts.isNotEmpty() && res.shifts.all { it.date == date }
        steps += Step("Tarixi təyin etmə", datesOk, Az.dateLabel(date))

        val matched = res.shifts.filter { ShiftFilter.matches(it.time, s.startMin, s.endMin) }
        val filterOk = matched.size == 1 && res.shifts.size == 2
        steps += Step(
            "Saat filtri",
            filterOk,
            "${Az.hm(s.startMin)}–${Az.hm(s.endMin)}: ${res.shifts.size} növbədən ${matched.size} uyğun",
        )
        if (matched.isEmpty()) return steps

        val now = System.currentTimeMillis()
        val first = Tracker.applyPassive(emptyList(), matched, now, LocalDate.now())
        val second = Tracker.applyPassive(first.list, matched, now + 1, LocalDate.now())
        val dedupOk = first.newOnes.size == 1 && second.newOnes.isEmpty()
        steps += Step("Təkrar bildirişin qarşısı", dedupOk, "eyni növbə ikinci dəfə bildirilmir")

        val base = Tracked.of(matched[0], now, test = true)
        val t = base.copy(key = base.key + "|" + now)
        Repo.setFound(Repo.found.value + t)
        steps += Step("Tapılanlar siyahısı", true, "test kartı əlavə olundu")

        val (canNotify, why) = notificationsReady(ctx, Notifier.shiftChannel(s, Notifier.inQuietNow(s)))
        Notifier.notifyShift(ctx, t)
        steps += Step("Bildiriş", canNotify, if (canNotify) Az.notifLine(t) else why)
        Repo.log(LogType.TEST, "Test: ${Az.notifLine(t)}" + if (canNotify) "" else " · $why")
        return steps
    }

    private fun notificationsReady(ctx: Context, channel: String): Pair<Boolean, String> {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false to "bildiriş icazəsi yoxdur"
        val ch = ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channel)
        if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return false to "\"Tapılan növbələr\" kanalı söndürülüb"
        return true to ""
    }
}
