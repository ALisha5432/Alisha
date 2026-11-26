package com.wpradar.core

/**
 * WhatsApp qrup bildirişini hər yeni mesajda köhnə mesajlarla birlikdə yenidən göndərir.
 * Jurnal dolmasın deyə hər mesaj yalnız qərarı dəyişəndə yazılır.
 */
class JournalDeduper(private val maxEntries: Int = 1000) {
    private val logged = LinkedHashMap<String, Verdict>()

    fun shouldLog(key: String, verdict: Verdict): Boolean {
        val previous = logged[key]
        if (previous == verdict) return false
        // Xəbər verilmiş mesajın təkrarı jurnala ikinci dəfə yazılmır.
        if (verdict == Verdict.DUPLICATE && previous != null) return false
        logged.remove(key)
        logged[key] = verdict
        val it = logged.keys.iterator()
        while (logged.size > maxEntries && it.hasNext()) {
            it.next()
            it.remove()
        }
        return true
    }
}
