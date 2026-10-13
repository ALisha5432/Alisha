package az.novbe.radar.core

/**
 * Scroll zamanı ardıcıl səhifələri bir siyahıya birləşdirir.
 * Səhifələr üst-üstə düşür, ona görə ortaq hissəni tapıb yalnız yeniləri əlavə edirik.
 */
object Stitch {
    fun same(a: Tok, b: Tok): Boolean = when {
        a is DateTok && b is DateTok -> a.day == b.day
        a is CardTok && b is CardTok -> a.card.sameAs(b.card)
        a is StatusTok && b is StatusTok -> a.status == b.status && a.text == b.text
        else -> false
    }

    /** Səhifənin kənarlarındakı yarımçıq (masa adı oxunmayan) kartları atır. */
    fun trim(page: List<Tok>): List<Tok> {
        var s = 0
        var e = page.size
        while (s < e && (page[s] as? CardTok)?.card?.complete == false) s++
        while (e > s && (page[e - 1] as? CardTok)?.card?.complete == false) e--
        return page.subList(s, e)
    }

    data class Overlap(val k: Int, val skip: Int)

    /**
     * [a]-nın sonu ilə [b]-nin əvvəli (ən çox [maxSkip] element buraxmaqla) arasındakı ən uzun ortaq hissə.
     * Tarix başlığı heç vaxt buraxılmır, buraxma olanda isə ən azı 2 element üst-üstə düşməlidir:
     * eyni masa ardıcıl günlərdə səhifə sərhədinə düşəndə kartlar səhv günə yazılmasın.
     */
    fun overlap(a: List<Tok>, b: List<Tok>, maxSkip: Int = 2): Overlap {
        var best = Overlap(0, 0)
        for (skip in 0..minOf(maxSkip, maxOf(b.size - 1, 0))) {
            if (skip > 0 && b[skip - 1] is DateTok) break
            val maxK = minOf(a.size, b.size - skip)
            val minK = if (skip > 0) 2 else 1
            for (k in maxK downTo minK) {
                if (k <= best.k) break
                var ok = true
                for (j in 0 until k) {
                    if (!same(a[a.size - k + j], b[skip + j])) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    best = Overlap(k, skip)
                    break
                }
            }
        }
        return best
    }

    /** Aşağı scroll edilmiş səhifəni [acc]-a əlavə edir. Yeni elementlərin sayını qaytarır. */
    fun forward(acc: MutableList<Tok>, rawPage: List<Tok>): Int {
        val page = trim(rawPage)
        if (page.isEmpty()) return 0
        if (acc.isEmpty()) {
            acc.addAll(page)
            return page.size
        }
        val o = overlap(acc, page)
        for (j in 0 until o.k) {
            val ai = acc.size - o.k + j
            val p = page[o.skip + j]
            val q = acc[ai]
            if (p is CardTok && q is CardTok && p.card.richness() > q.card.richness()) acc[ai] = p
        }
        val rest = if (o.k == 0) page else page.subList(o.skip + o.k, page.size)
        acc.addAll(rest)
        return rest.size
    }

    /**
     * İstənilən istiqamətdə birləşdirmə (istifadəçi özü yuxarı-aşağı scroll edəndə).
     * Ortaq hissə tapılmasa null qaytarır.
     */
    fun merge(acc: List<Tok>, rawPage: List<Tok>): List<Tok>? {
        val page = trim(rawPage)
        if (acc.isEmpty()) return page
        if (page.isEmpty()) return acc
        if (indexOf(acc, page) >= 0) return acc
        val down = overlap(acc, page)
        val up = overlap(page, acc)
        return when {
            down.k > 0 && down.k >= up.k -> acc + page.subList(down.skip + down.k, page.size)
            up.k > 0 -> page + acc.subList(up.skip + up.k, acc.size)
            else -> null
        }
    }

    private fun indexOf(acc: List<Tok>, page: List<Tok>): Int {
        if (page.size > acc.size) return -1
        outer@ for (i in 0..acc.size - page.size) {
            for (j in page.indices) if (!same(acc[i + j], page[j])) continue@outer
            return i
        }
        return -1
    }
}
