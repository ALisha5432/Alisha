package az.novbe.radar.core

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Test" düyməsi üçün saxta "Available Shifts" ekranı. Əsl ekran kimi node ağacıdır,
 * ona görə də bütün zəncir (parser → tarix → filtr → bildiriş) eyni kodla yoxlanılır.
 */
object Demo {
    const val W = 720
    const val H = 1600
    const val TABLE = "TEST Masa E2"
    const val INITIALS = "TS"

    /** EvoApp üslubunda saat: "16:00", "0:00". */
    private fun evo(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** [startMin]–[endMin]: istifadəçinin filtri — test növbəsi ona uyğun yaradılır. */
    fun tree(date: LocalDate, startMin: Int = 16 * 60, endMin: Int = 0): UiTree {
        val match = "${evo(startMin)} - ${evo(endMin)}"
        val other = if (match == "8:00 - 16:00") "9:00 - 11:00" else "8:00 - 16:00"
        val b = UiTreeBuilder("demo.evoapp", W, H)
        val root = b.add(-1, "android.widget.FrameLayout", null, null, null, null, Box(0, 0, W, H))
        fun text(parent: Int, s: String, l: Int, t: Int, r: Int, bt: Int) =
            b.add(parent, "android.widget.TextView", null, s, null, null, Box(l, t, r, bt))

        val month = date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + date.year
        text(root, "Available Shifts", 250, 110, 470, 150)
        b.add(root, "android.widget.ImageButton", null, null, "Previous month", null, Box(20, 205, 65, 255), clickable = true)
        text(root, month, 270, 212, 445, 250)
        b.add(root, "android.widget.ImageButton", null, null, "Next month", null, Box(645, 205, 695, 255), clickable = true)

        val list = b.add(root, "androidx.recyclerview.widget.RecyclerView", null, null, null, null, Box(0, 262, W, 1375), scrollable = true)
        text(list, date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), 45, 280, 85, 302)
        text(list, date.dayOfMonth.toString(), 42, 325, 87, 362)
        // Uyğun gələn növbə (filtr saatları)
        text(list, INITIALS, 155, 275, 205, 310)
        text(list, TABLE, 155, 332, 355, 355)
        text(list, "DEALER", 155, 363, 225, 380)
        text(list, match, 417, 320, 534, 342)
        text(list, "8h", 414, 366, 440, 388)
        text(list, "RESPOND", 555, 334, 660, 374)
        // Uyğun gəlməyən növbə — filtr onu atmalıdır
        text(list, "MC", 155, 460, 200, 495)
        text(list, "A2 Sample RO", 155, 517, 300, 540)
        text(list, "DEALER", 155, 548, 225, 565)
        text(list, other, 410, 505, 510, 527)
        text(list, "8h", 388, 551, 412, 573)
        text(list, "RESPOND", 555, 519, 660, 559)
        return b.build()
    }
}
