package az.novbe.radar.radar

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Fonda skan zamanı ekranın üstünə qaranlıq pərdə: gecə otaq işıqlanmasın, ekran yarıda sönməsin.
 * Pərdəyə toxunanda skan dayanır. Accessibility pəncərəsidir — ayrıca icazə tələb etmir.
 * EvoApp-a heç bir toxunuş ötürmür; skan yalnız node əməliyyatları ilə işləyir.
 */
class ScanOverlay(private val svc: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var view: View? = null
    @Volatile var onTouch: (() -> Unit)? = null

    private val watchdog = Runnable { remove() }

    /** [dark] = true: qara pərdə; false: görünməz 1 piksel (yalnız ekranı açıq saxlayır). */
    fun show(dark: Boolean) {
        main.post { add(dark) }
    }

    fun hide() {
        main.post { remove() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(dark: Boolean) {
        if (view != null) return
        val wm = svc.getSystemService(WindowManager::class.java) ?: return
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (!dark) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val size = if (dark) WindowManager.LayoutParams.MATCH_PARENT else 1
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val v: View = if (dark) {
            lp.screenBrightness = 0.01f
            FrameLayout(svc).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    TextView(svc).apply {
                        text = "Növbə Radarı yoxlayır…\nDayandırmaq üçün ekrana toxun"
                        setTextColor(0xFF39424F.toInt())
                        textSize = 14f
                        gravity = Gravity.CENTER
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
                setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_DOWN) onTouch?.invoke()
                    true
                }
            }
        } else {
            View(svc)
        }
        runCatching {
            wm.addView(v, lp)
            view = v
            main.removeCallbacks(watchdog)
            main.postDelayed(watchdog, 120_000)
        }
    }

    private fun remove() {
        main.removeCallbacks(watchdog)
        val v = view ?: return
        view = null
        runCatching { svc.getSystemService(WindowManager::class.java)?.removeView(v) }
    }

    /** OCR üçün ekran şəkli çəkiləndə pərdə müvəqqəti gizlənir. */
    suspend fun <T> hidden(block: suspend () -> T): T {
        val v = view ?: return block()
        withContext(Dispatchers.Main) { v.visibility = View.INVISIBLE }
        delay(250)
        try {
            return block()
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                if (view === v) v.visibility = View.VISIBLE
            }
        }
    }
}
