package az.novbe.radar.radar

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import az.novbe.radar.core.Box
import az.novbe.radar.core.UiTree
import az.novbe.radar.core.UiTreeBuilder

/** Canlı AccessibilityNodeInfo ağacını core-un UiTree-sinə çevirir. */
object Snapshot {
    private const val MAX_NODES = 5000

    fun capture(root: AccessibilityNodeInfo?, screenW: Int, screenH: Int): UiTree? {
        if (root == null) return null
        val b = UiTreeBuilder(root.packageName?.toString(), screenW, screenH)
        val tmp = Rect()
        walk(root, -1, b, tmp)
        return b.build().takeIf { it.size > 0 }
    }

    private fun walk(node: AccessibilityNodeInfo?, parent: Int, b: UiTreeBuilder, tmp: Rect) {
        if (node == null || b.count >= MAX_NODES) return
        node.getBoundsInScreen(tmp)
        val box = Box(tmp.left, tmp.top, tmp.right, tmp.bottom)
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            node.hintText?.toString()?.takeIf { it.isNotBlank() } else null
        val id = b.add(
            parent = parent,
            cls = node.className?.toString(),
            viewId = runCatching { node.viewIdResourceName }.getOrNull(),
            text = text,
            desc = desc,
            hint = hint,
            box = box,
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            visible = node.isVisibleToUser,
            password = node.isPassword,
        )
        val n = node.childCount
        for (i in 0 until n) {
            val c = runCatching { node.getChild(i) }.getOrNull() ?: continue
            walk(c, id, b, tmp)
            if (c !== node) runCatching { c.recycle() }
        }
    }
}
