package az.novbe.radar.radar

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Öyrənmə rejimində toplanan node ağacı dump-ları (paylaşmaq üçün). */
object LearnStore {
    private const val FILE = "learn_dump.txt"
    private const val MAX_BYTES = 900_000L

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun append(ctx: Context, dump: String) {
        val f = file(ctx)
        if (f.exists() && f.length() > MAX_BYTES) return
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        f.appendText("\n===== EKRAN $ts =====\n$dump\n")
    }

    fun count(ctx: Context): Int {
        val f = file(ctx)
        if (!f.exists()) return 0
        return f.useLines { lines -> lines.count { it.startsWith("===== EKRAN") } }
    }

    fun sizeKb(ctx: Context): Long = file(ctx).takeIf { it.exists() }?.length()?.div(1024) ?: 0

    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    /** Paylaşılacaq tam mətn: cihaz məlumatı + bütün ekranlar. */
    fun export(ctx: Context): String {
        val header = buildString {
            append("Növbə Radarı — öyrənmə dump\n")
            append("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            val pkg = az.novbe.radar.data.Prefs.s.evoPackage
            append("EvoApp paketi: ${pkg ?: "?"}\n")
            if (pkg != null) {
                val ver = runCatching { ctx.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
                append("EvoApp versiyası: ${ver ?: "?"}\n")
            }
            append("Ekran: ${ctx.resources.displayMetrics.widthPixels}x${ctx.resources.displayMetrics.heightPixels}\n")
        }
        val body = file(ctx).takeIf { it.exists() }?.readText() ?: "(boşdur — öyrənmə rejimini aç və EvoApp-ı gəz)"
        return header + body
    }

    /**
     * Mətn kimi paylaşmaq üçün: başlıq + son ekranlar ([maxChars]-a qədər).
     * Android bir anda çox böyük mətni ötürə bilmir, ona görə tam versiya fayl kimi paylaşılır.
     */
    fun exportTail(ctx: Context, maxChars: Int = 120_000): String {
        val full = export(ctx)
        if (full.length <= maxChars) return full
        val headerEnd = full.indexOf("\n=====").takeIf { it > 0 } ?: 0
        val header = full.substring(0, headerEnd)
        var tail = full.takeLast(maxChars - header.length - 120)
        val cut = tail.indexOf("\n===== EKRAN")
        if (cut > 0) tail = tail.substring(cut)
        return header + "\n(… köhnə ekranlar buraxıldı — hamısı üçün \"Fayl kimi paylaş\" istifadə et)\n" + tail
    }

    /** Paylaşmaq üçün keş qovluğunda fayl yaradır. */
    fun exportFile(ctx: Context): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "evoapp-dump.txt")
        f.writeText(export(ctx))
        return f
    }
}
