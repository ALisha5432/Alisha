package az.novbe.radar.core

/** Ekran şəkillərindəki (720x1600) koordinatlara əsaslanan saxta ağaclar. */
object Fixtures {
    const val W = 720
    const val H = 1600

    class T(val text: String?, val box: Box, val desc: String? = null, val clickable: Boolean = false)

    fun t(text: String, l: Int, t: Int, r: Int, b: Int) = T(text, Box(l, t, r, b))
    fun icon(l: Int, t: Int, r: Int, b: Int, desc: String? = null) = T(null, Box(l, t, r, b), desc, clickable = true)

    /** Hamısı bir siyahı konteynerinin altında, düz (flat) quruluş. */
    fun flat(header: List<T>, list: List<T>, listBox: Box = Box(0, 262, W, 1375), pkg: String = "evo.test"): UiTree {
        val b = UiTreeBuilder(pkg, W, H)
        val root = b.add(-1, "android.widget.FrameLayout", null, null, null, null, Box(0, 0, W, H))
        for (h in header) b.add(root, if (h.text == null) "android.widget.ImageButton" else "android.widget.TextView", null, h.text, h.desc, null, h.box, clickable = h.clickable)
        val lst = b.add(root, "androidx.recyclerview.widget.RecyclerView", null, null, null, null, listBox, scrollable = true)
        for (i in list) b.add(lst, "android.widget.TextView", null, i.text, i.desc, null, i.box, clickable = i.clickable)
        b.add(root, "android.widget.TextView", null, "21", null, null, Box(645, 1405, 665, 1420))
        return b.build()
    }

    /** Hər kart öz konteynerində (RecyclerView sətri kimi). */
    fun nested(header: List<T>, rows: List<List<T>>, listBox: Box = Box(0, 262, W, 1375)): UiTree {
        val b = UiTreeBuilder("evo.test", W, H)
        val root = b.add(-1, "android.widget.FrameLayout", null, null, null, null, Box(0, 0, W, H))
        for (h in header) b.add(root, if (h.text == null) "android.widget.ImageButton" else "android.widget.TextView", null, h.text, h.desc, null, h.box, clickable = h.clickable)
        val lst = b.add(root, "androidx.recyclerview.widget.RecyclerView", null, null, null, null, listBox, scrollable = true)
        for (row in rows) {
            val box = row.map { it.box }.reduce { a, c -> a.union(c) }
            val r = b.add(lst, "android.view.ViewGroup", null, null, null, null, box)
            for (i in row) b.add(r, "android.widget.TextView", null, i.text, i.desc, null, i.box)
        }
        return b.build()
    }

    val availableHeader = listOf(
        t("Available Shifts", 250, 110, 470, 150),
        icon(20, 205, 65, 255),
        t("October 2026", 270, 212, 445, 250),
        icon(645, 205, 695, 255),
    )

    // Available Shifts ekranı (1-ci şəkil)
    val cardA2T = listOf(
        t("A2 Sample RO", 155, 272, 300, 298), t("DEALER", 155, 305, 225, 322),
        t("8:00 - 16:00", 410, 262, 510, 282), t("8h", 388, 308, 412, 330), t("RESPOND", 555, 277, 660, 317),
    )
    val cardD1 = listOf(
        t("LB", 155, 400, 205, 436), t("D1 Sampl...able BJ 2", 155, 457, 370, 483), t("DEALER", 155, 489, 225, 506),
        t("10:00 - 12:00", 420, 446, 548, 468), t("2h", 415, 492, 440, 514), t("RESPOND", 555, 460, 660, 500),
    )
    val cardE2 = listOf(
        t("KA", 155, 585, 205, 620), t("E2 Big Wheel TUR", 155, 642, 355, 665), t("DEALER", 155, 673, 225, 690),
        t("16:00 - 0:00", 417, 630, 534, 652), t("8h", 414, 676, 440, 698), t("RESPOND", 555, 644, 660, 684),
    )
    val date19 = listOf(t("Mon", 45, 770, 85, 792), t("19", 45, 815, 82, 852))
    val cardA2C = listOf(
        t("MC", 155, 768, 200, 805), t("A2 Widet...ablex BJ", 155, 827, 375, 849), t("DEALER", 155, 858, 225, 875),
        t("9:00 - 11:00", 432, 814, 547, 836), t("2h", 430, 860, 455, 882), t("RESPOND", 555, 828, 660, 868),
    )
    val date24 = listOf(t("Sat", 47, 955, 80, 977), t("24", 42, 1000, 87, 1037))
    val cardD2a = listOf(
        t("ND", 160, 955, 195, 988), t("D2 Sample TUR RL", 155, 1010, 355, 1033), t("DEALER", 155, 1042, 225, 1059),
        t("8:00 - 16:00", 418, 998, 533, 1020), t("8h", 415, 1044, 440, 1066), t("RESPOND", 555, 1012, 660, 1052),
    )
    val date25 = listOf(t("Sun", 47, 1138, 82, 1160), t("25", 42, 1185, 87, 1222))
    val cardD2b = listOf(
        t("PE", 160, 1140, 195, 1172), t("D2 Sample TUR RL", 155, 1195, 355, 1218), t("DEALER", 155, 1226, 225, 1243),
        t("8:00 - 9:00", 423, 1182, 528, 1204), t("1h", 420, 1228, 440, 1250), t("RESPOND", 555, 1196, 660, 1236),
    )
    val tail = listOf(t("Mon", 47, 1322, 82, 1344), t("PE", 160, 1325, 195, 1356))

    fun availableFlat(): UiTree = flat(
        availableHeader,
        cardA2T + cardD1 + cardE2 + date19 + cardA2C + date24 + cardD2a + date25 + cardD2b + tail,
    )

    fun availableNested(): UiTree = nested(
        availableHeader,
        listOf(cardA2T, cardD1, cardE2, date19 + cardA2C, date24 + cardD2a, date25 + cardD2b, tail),
    )

    // My Schedule ekranı (2-ci şəkil)
    val scheduleHeader = listOf(
        t("My Schedule", 45, 112, 228, 148),
        icon(38, 205, 75, 255, "calendar"),
        icon(125, 210, 150, 250),
        t("October 2026", 318, 212, 492, 250),
        icon(655, 210, 685, 250),
    )
    val scheduleList = listOf(
        t("Tue", 40, 320, 75, 340), t("13", 38, 365, 75, 405),
        t("D1 Sample RL", 139, 365, 284, 388), t("DEALER", 139, 395, 207, 412), t("16:00 - 0:00", 371, 352, 487, 374),
        t("8h", 367, 398, 392, 420), t("SCHEDULED", 541, 375, 650, 397),
        t("1 SHIFT AVAILABLE", 139, 532, 356, 557),
        t("Wed", 38, 632, 77, 652), t("14", 40, 675, 73, 712),
        t("D1 Sample RL", 139, 677, 284, 700), t("DEALER", 139, 707, 207, 724), t("16:00 - 0:00", 371, 664, 487, 686),
        t("8h", 367, 710, 392, 732), t("SCHEDULED", 541, 687, 650, 709),
        t("Thu", 42, 788, 75, 808), t("15", 40, 832, 73, 870), t("4 SHIFTS AVAILABLE", 139, 843, 371, 868),
        t("Fri", 45, 943, 68, 963), t("16", 40, 988, 73, 1025), t("4 SHIFTS AVAILABLE", 139, 999, 371, 1024),
        t("Sat", 42, 1098, 72, 1118), t("17", 40, 1145, 73, 1182), t("REST DAY", 139, 1155, 250, 1180),
        t("Sun", 40, 1255, 75, 1275), t("18", 40, 1300, 73, 1337),
        t("D1 Sample RL", 139, 1300, 284, 1323), t("16:00 - 0:00", 371, 1287, 487, 1309), t("SCHEDULED", 534, 1308, 610, 1330),
    )

    fun scheduleFlat(): UiTree = flat(scheduleHeader, scheduleList, Box(0, 262, W, 1375))
}
