package az.novbe.radar.radar

import android.content.Context
import android.content.Intent
import az.novbe.radar.data.Prefs

/** EvoApp paketini tapır: istifadəçi seçibsə onu, yoxsa adına görə təxmin edir. */
object EvoFinder {
    private val HINTS = listOf("evo", "evolution")
    private val NAME_HINTS = listOf("evoapp", "evo app", "evolution")

    fun packageName(ctx: Context): String? {
        Prefs.s.evoPackage?.let { if (isLaunchable(ctx, it)) return it }
        return guess(ctx)?.also { Prefs.update { s -> s.copy(evoPackage = it) } }
    }

    fun isLaunchable(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    data class App(val pkg: String, val label: String)

    fun launchableApps(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            App(pkg, ri.loadLabel(pm)?.toString() ?: pkg)
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    private fun guess(ctx: Context): String? {
        val apps = launchableApps(ctx).filterNot { it.pkg.startsWith("az.novbe.radar") }
        apps.firstOrNull { a -> a.label.trim().equals("evo", true) || NAME_HINTS.any { a.label.contains(it, true) } }
            ?.let { return it.pkg }
        // Paket adının bir hissəsi "evo" ilə başlamalıdır (com.evolution.app) — "revolut" kimi təsadüfi uyğunluq yox
        return apps.firstOrNull { a -> a.pkg.lowercase().split('.').any { part -> HINTS.any { part.startsWith(it) } } }?.pkg
    }
}
