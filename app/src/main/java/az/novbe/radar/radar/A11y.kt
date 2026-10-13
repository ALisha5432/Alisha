package az.novbe.radar.radar

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityNodeInfo

/** Accessibility xidmətinin qoşulub-qoşulmadığını yoxlayır. */
object A11y {
    fun isEnabled(ctx: Context): Boolean {
        val expected = ctx.packageName + "/" + RadarAccessibilityService::class.java.name
        val enabled = runCatching {
            Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        }.getOrDefault(0)
        if (enabled != 1) return false
        val list = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(list)
        for (s in splitter) if (s.equals(expected, ignoreCase = true)) return true
        return false
    }
}

fun AccessibilityService.rootSafe(): AccessibilityNodeInfo? = runCatching { rootInActiveWindow }.getOrNull()
