package az.novbe.radar.core

/** Öyrənmə rejimi üçün node ağacının yığcam mətn təsviri. */
object Dump {
    fun tree(tree: UiTree): String {
        val keep = BooleanArray(tree.size)
        for (i in tree.size - 1 downTo 0) {
            val n = tree[i]
            val self = !n.text.isNullOrBlank() || !n.desc.isNullOrBlank() || !n.hint.isNullOrBlank() ||
                n.viewId != null || n.clickable || n.scrollable || n.password
            if (self || n.children.any { keep[it] }) keep[i] = true
        }
        val sb = StringBuilder()
        for (i in 0 until tree.size) {
            if (!keep[i]) continue
            val n = tree[i]
            sb.append("  ".repeat(minOf(n.depth, 40)))
            sb.append(n.shortCls)
            n.viewId?.let { sb.append(" #").append(it.substringAfter(":id/")) }
            n.text?.takeIf { it.isNotEmpty() }?.let { sb.append(" t=\"").append(esc(it)).append('"') }
            n.desc?.takeIf { it.isNotEmpty() }?.let { sb.append(" d=\"").append(esc(it)).append('"') }
            n.hint?.takeIf { it.isNotEmpty() }?.let { sb.append(" h=\"").append(esc(it)).append('"') }
            sb.append(' ').append(n.box)
            if (n.clickable) sb.append(" C")
            if (n.scrollable) sb.append(" S")
            if (n.password) sb.append(" P")
            if (!n.visible) sb.append(" (gizli)")
            sb.append('\n')
        }
        return sb.toString()
    }

    fun page(p: Page): String = buildString {
        append("Parser: ekran=").append(p.screen)
        append(" · ay=").append(p.month ?: "?")
        append(" · sahə=").append(p.region)
        append(" · kart=").append(p.cards.size).append('\n')
        for (t in p.tokens) {
            when (t) {
                is DateTok -> append("  TARİX ").append(t.day).append(' ').append(t.dow ?: "").append(" y=").append(t.y)
                is CardTok -> {
                    val c = t.card
                    append("  KART [").append(c.table).append(" | ").append(c.role).append(" | ")
                        .append(c.timeText).append(" | ").append(c.duration).append(" | ").append(c.initials)
                        .append(if (c.hasButton) " | düymə" else "").append("] y=").append(t.y)
                }
                is StatusTok -> append("  STATUS ").append(t.status).append(' ').append(t.text).append(" y=").append(t.y)
            }
            append('\n')
        }
    }

    private fun esc(s: String): String = s.replace("\n", "\\n").replace("\"", "'").let { if (it.length > 300) it.take(300) + "…" else it }
}
