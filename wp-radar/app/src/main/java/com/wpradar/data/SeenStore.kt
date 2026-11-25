package com.wpradar.data

import android.content.Context
import com.wpradar.core.ParsedMessage
import com.wpradar.core.SeenCache
import java.io.File

/** SeenCache-in faylda saxlanan variantı: telefon yenidən başlasa da təkrar xəbərdarlıq getmir. */
class SeenStore(context: Context) {
    private val file = File(context.filesDir, "seen_v1.txt")
    private val cache = SeenCache()
    private var loaded = false

    /** true → artıq görülüb, xəbərdarlıq göndərmə. */
    @Synchronized
    fun checkAndMark(msg: ParsedMessage, now: Long = System.currentTimeMillis()): Boolean {
        if (!loaded) {
            runCatching { if (file.exists()) cache.load(file.readText()) }
            loaded = true
        }
        val duplicate = cache.checkAndMark(msg, now)
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(cache.serialize())
            if (!tmp.renameTo(file)) {
                file.writeText(cache.serialize())
                tmp.delete()
            }
        }
        return duplicate
    }
}
