package az.novbe.radar.core

/** Ekrandakı düzbucaqlı (piksel). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val cx: Int get() = (left + right) / 2
    val cy: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val area: Long get() = if (isEmpty) 0L else width.toLong() * height.toLong()

    fun union(o: Box): Box = Box(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))

    override fun toString(): String = "[$left,$top][$right,$bottom]"
}

/** Accessibility node-un (və ya OCR sətrinin) Android-dan asılı olmayan surəti. */
class UiNode(
    val id: Int,
    val parent: Int,
    val depth: Int,
    val cls: String?,
    val viewId: String?,
    val text: String?,
    val desc: String?,
    val hint: String?,
    val box: Box,
    val clickable: Boolean,
    val scrollable: Boolean,
    val visible: Boolean,
    val password: Boolean,
) {
    val children: MutableList<Int> = ArrayList()
    val shortCls: String get() = cls?.substringAfterLast('.')?.ifEmpty { null } ?: "?"
}

/** Node-lar pre-order (valideyn uşaqlardan əvvəl) sırası ilə saxlanılır. */
class UiTree(val nodes: List<UiNode>, val packageName: String?, val screenW: Int, val screenH: Int) {
    val size: Int get() = nodes.size

    operator fun get(i: Int): UiNode = nodes[i]

    fun ancestorIds(i: Int): List<Int> {
        val out = ArrayList<Int>()
        var p = nodes[i].parent
        while (p >= 0) {
            out += p
            p = nodes[p].parent
        }
        return out
    }

    fun subtreeIds(i: Int): List<Int> {
        val out = ArrayList<Int>()
        val stack = ArrayDeque<Int>()
        stack.addLast(i)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            out += n
            val ch = nodes[n].children
            for (k in ch.indices.reversed()) stack.addLast(ch[k])
        }
        return out
    }

    /** Ekranın eni/hündürlüyü məlum deyilsə, node-ların özündən təxmin edilir. */
    val width: Int get() = if (screenW > 0) screenW else (nodes.maxOfOrNull { it.box.right } ?: 0)
    val height: Int get() = if (screenH > 0) screenH else (nodes.maxOfOrNull { it.box.bottom } ?: 0)
}

class UiTreeBuilder(private val pkg: String?, private val w: Int, private val h: Int) {
    private val nodes = ArrayList<UiNode>()

    val count: Int get() = nodes.size

    fun add(
        parent: Int,
        cls: String?,
        viewId: String?,
        text: String?,
        desc: String?,
        hint: String?,
        box: Box,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        visible: Boolean = true,
        password: Boolean = false,
    ): Int {
        val id = nodes.size
        val depth = if (parent >= 0) nodes[parent].depth + 1 else 0
        nodes += UiNode(id, parent, depth, cls, viewId, text, desc, hint, box, clickable, scrollable, visible, password)
        if (parent >= 0) nodes[parent].children += id
        return id
    }

    fun build(): UiTree = UiTree(nodes, pkg, w, h)
}
