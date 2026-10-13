package az.novbe.radar.core

import kotlin.math.abs

/** Öyrənmə rejimində yadda saxlanan "Available Shifts" keçidi. */
data class NodeSig(val viewId: String?, val text: String?, val desc: String?, val cls: String?) {

    fun idMatches(n: UiNode): Boolean = viewId != null && n.viewId == viewId

    fun textMatches(n: UiNode): Boolean =
        (text != null && (textMatch(text, n.text) || textMatch(text, n.desc))) ||
            (desc != null && textMatch(desc, n.desc))

    fun serialize(): String = listOf(viewId, text, desc, cls).joinToString(SEP) { it ?: NULL }

    override fun toString(): String = buildString {
        viewId?.let { append("id=").append(it.substringAfter(":id/")).append(' ') }
        text?.let { append("\"").append(it).append("\" ") }
        desc?.let { append("desc=\"").append(it).append("\" ") }
        cls?.let { append(it.substringAfterLast('.')) }
    }.trim()

    companion object {
        private const val SEP = "\u0001"
        private const val NULL = "\u0000"

        fun parse(s: String?): NodeSig? {
            if (s.isNullOrEmpty()) return null
            val p = s.split(SEP)
            if (p.size != 4) return null
            fun v(i: Int) = p[i].takeIf { it != NULL }
            return NodeSig(v(0), v(1), v(2), v(3))
        }

        /** Rəqəmlər dəyişə bilər ("4 SHIFTS" → "2 SHIFTS"), ona görə onları \d+ edirik. */
        fun pattern(s: String): Regex {
            val sb = StringBuilder("^")
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) {
                    while (i < s.length && s[i].isDigit()) i++
                    sb.append("""\d+""")
                    continue
                }
                if (c.isWhitespace()) {
                    while (i < s.length && s[i].isWhitespace()) i++
                    sb.append("""\s*""")
                    continue
                }
                if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) sb.append('\\')
                sb.append(c)
                i++
            }
            sb.append("$")
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }

        private fun textMatch(p: String, v: String?): Boolean = v != null && pattern(p.trim()).matches(v.trim())
    }
}

/** Naviqasiya üçün yalnız oxuma ilə tapılan hədəflər (heç biri RESPOND deyil). */
object Nav {
    /** "4 SHIFTS AVAILABLE" sətri — Available Shifts-ə keçid. */
    fun availableEntry(page: Page): Line? {
        val r = page.region
        return page.lines
            .filter { it.kind == Kind.AVAIL_COUNT && it.box.cy >= r.top && it.box.cy <= r.bottom }
            .minByOrNull { it.box.top }
            ?: page.lines
                .filter {
                    it.box.top > r.top && it.kind != Kind.BUTTON &&
                        (it.kind == Kind.TITLE_AVAILABLE || it.text.contains("available shift", true) || it.text.contains("open shift", true))
                }
                .minByOrNull { it.box.top }
    }

    /** Ay başlığının sağındakı (irəli) və ya solundakı (geri) ox. */
    fun monthArrow(tree: UiTree, monthLine: Line, forward: Boolean): Int? {
        val m = monthLine.box
        val excluded = (tree.ancestorIds(monthLine.nodeId) + monthLine.nodeId).toHashSet()
        val rowTop = m.top - m.height
        val rowBottom = m.bottom + m.height
        val maxW = if (tree.width > 0) tree.width / 4 else 300
        val cands = tree.nodes.filter { n ->
            n.id !in excluded && !n.box.isEmpty && n.box.cy in rowTop..rowBottom &&
                n.box.height <= m.height * 4 && n.box.width <= maxW &&
                (if (forward) n.box.left >= m.right - 4 else n.box.right <= m.left + 4) &&
                (n.text.isNullOrBlank() || Patterns.classify(n.text) == Kind.SYMBOL) &&
                !Safety.isForbidden((n.text ?: "") + " " + (n.desc ?: ""))
        }
        if (cands.isEmpty()) return null
        val words = if (forward) listOf("next", "forward", "right", "chevron", "›", ">") else listOf("prev", "back", "left", "chevron", "‹", "<")
        fun hinted(n: UiNode) = words.any { w -> (n.desc ?: "").contains(w, true) || (n.text ?: "").contains(w, true) }
        val pool = cands.filter { it.clickable }.ifEmpty { cands }
        val dist = { n: UiNode -> if (forward) n.box.left - m.right else m.left - n.box.right }
        return pool.minWithOrNull(compareBy<UiNode> { if (hinted(it)) 0 else 1 }.thenBy { dist(it) }.thenBy { -it.box.area })?.id
    }

    /**
     * Siyahının özü. Məzmun sətirlərinin çoxunu əhatə edən scroll node-larından ən kiçiyi seçilir:
     * siyahı üfüqi ay səhifələyicisinin (pager) içindədirsə, səhifələyici yox, siyahı sürüşdürülsün.
     */
    fun mainScrollable(tree: UiTree, page: Page): Int? {
        val r = page.region
        val scrolls = tree.nodes.filter { it.scrollable && !it.box.isEmpty }
        if (scrolls.isEmpty()) return null
        val content = page.lines.filter { it.box.cy >= r.top && it.box.cy <= r.bottom && it.nodeId < tree.size }
        if (content.isNotEmpty()) {
            val cov = scrolls.map { n ->
                val sub = tree.subtreeIds(n.id).toHashSet()
                n to content.count { it.nodeId in sub }
            }.filter { it.second > 0 }
            val max = cov.maxOfOrNull { it.second } ?: 0
            if (max > 0) return cov.filter { it.second * 5 >= max * 4 }.minByOrNull { it.first.box.area }?.first?.id
        }
        return scrolls
            .filter { it.box.bottom > r.top + 20 && it.box.cy >= r.top }
            .maxByOrNull { it.box.area }?.id
            ?: scrolls.maxByOrNull { it.box.area }?.id
    }

    /** Toxunmaq olarmı? Node-un və onun altındakı bütün mətnlərdə qadağan söz olmamalıdır. */
    fun safeTarget(tree: UiTree, nodeId: Int): Int? {
        var target = nodeId
        while (target >= 0 && !tree[target].clickable) target = tree[target].parent
        if (target < 0) target = nodeId
        val texts = tree.subtreeIds(target).flatMap { Lines.ownStrings(tree[it]) }
        if (texts.any { Safety.isForbidden(it) }) return null
        val n = tree[target]
        val screenArea = tree.width.toLong() * tree.height.toLong()
        if (screenArea > 0 && n.box.area > screenArea * 0.45) return null
        return target
    }

    fun findSig(tree: UiTree, sig: NodeSig): Int? {
        val visible = tree.nodes.filter { !it.box.isEmpty }
        if (sig.viewId != null) {
            val byId = visible.filter { sig.idMatches(it) }
            if (byId.isNotEmpty()) {
                val withText = byId.filter { n -> tree.subtreeIds(n.id).any { sig.textMatches(tree[it]) } }
                return (withText.ifEmpty { byId }).minByOrNull { it.box.top }?.id
            }
        }
        return visible.filter { sig.textMatches(it) }.minByOrNull { it.box.top }?.id
    }

    /** Ay [from]-dan [to]-ya neçə addımdır (müsbət = irəli). */
    fun monthSteps(from: java.time.YearMonth, to: java.time.YearMonth): Int =
        (to.year - from.year) * 12 + (to.monthValue - from.monthValue)

    fun nearlySame(a: Box, b: Box, tol: Int = 6): Boolean =
        abs(a.left - b.left) <= tol && abs(a.top - b.top) <= tol && abs(a.right - b.right) <= tol && abs(a.bottom - b.bottom) <= tol
}
