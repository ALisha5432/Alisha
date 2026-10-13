package az.novbe.radar.core

/** Ekrandakı bir mətn sətri: hansı node-dan gəldiyi, yeri və növü ilə. */
data class Line(val text: String, val kind: Kind, val box: Box, val nodeId: Int, val order: Int)

object Lines {
    private val SPACES = Regex("""[\t\r   ]+""")
    private val SEPARATORS = Regex("""\s*[,;|·•]\s*""")

    fun extract(tree: UiTree): List<Line> {
        val own = Array(tree.size) { ownStrings(tree[it]) }
        val out = ArrayList<Line>()
        for (i in 0 until tree.size) {
            val strs = own[i]
            if (strs.isEmpty()) continue
            val n = tree[i]
            if (n.children.isNotEmpty() && mergesDescendants(tree, own, i, strs)) continue
            for (s in strs) for (part in split(s)) {
                out += Line(part, Patterns.classify(part), n.box, i, out.size)
            }
        }
        return out
    }

    fun ownStrings(n: UiNode): List<String> {
        val list = ArrayList<String>(3)
        n.text?.let { list += it }
        n.desc?.let { list += it }
        if (n.text.isNullOrBlank()) n.hint?.let { list += it }
        return list.map { clean(it) }.filter { it.isNotEmpty() }.distinct()
    }

    fun clean(s: String): String =
        s.split('\n').joinToString("\n") { it.replace(SPACES, " ").trim() }.trim()

    /**
     * Konteynerin öz mətni (məs. Flutter/RN birləşdirilmiş label) uşaqların mətnlərini təkrarlayırsa,
     * onu atırıq ki, eyni kart iki dəfə sayılmasın.
     */
    private fun mergesDescendants(tree: UiTree, own: Array<List<String>>, i: Int, strs: List<String>): Boolean {
        val desc = tree.subtreeIds(i).drop(1).flatMap { own[it] }
        if (desc.isEmpty()) return false
        return strs.all { s ->
            val covered = desc.filter { it.length >= 2 && s.contains(it) }.sumOf { it.length }
            covered >= s.length * 0.6
        }
    }

    fun split(s: String): List<String> {
        val out = ArrayList<String>()
        for (raw in s.split('\n')) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (Patterns.isPureTime(l) || !Patterns.TIME.containsMatchIn(l)) {
                out += l
                continue
            }
            val parts = l.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) {
                out += parts
            } else {
                val m = Patterns.TIME.find(l)!!
                val before = l.substring(0, m.range.first).trim()
                val after = l.substring(m.range.last + 1).trim()
                if (before.isNotEmpty()) out += before
                out += m.value.trim()
                if (after.isNotEmpty()) out += after
            }
        }
        return out
    }
}
