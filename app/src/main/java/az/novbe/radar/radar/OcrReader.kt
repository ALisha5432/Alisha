package az.novbe.radar.radar

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import az.novbe.radar.core.Box
import az.novbe.radar.core.UiTree
import az.novbe.radar.core.UiTreeBuilder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Ehtiyat yol: node-lardan mətn alınmayanda ekran şəklini çəkib cihazın özündə OCR edir.
 * Nəticə adi UiTree kimi qaytarılır ki, eyni Parser işləsin.
 */
object OcrReader {
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * [wholeScreen]: tam ekran şəkli onun içində çəkilir (skan zamanı qara pərdəni gizlətmək üçün).
     * Android 14+-da əvvəlcə yalnız aktiv pəncərənin (EvoApp) şəkli çəkilir — pərdə ona düşmür.
     */
    suspend fun read(
        svc: AccessibilityService,
        wholeScreen: suspend (suspend () -> Bitmap?) -> Bitmap? = { it() },
    ): UiTree? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val root = svc.rootSafe()
        val bmp = root?.let { windowShot(svc, it.windowId) } ?: wholeScreen { screenshot(svc) } ?: return null
        return recognize(bmp, root?.packageName?.toString())
    }

    private suspend fun windowShot(svc: AccessibilityService, windowId: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return suspendCancellableCoroutine { cont ->
            runCatching { svc.takeScreenshotOfWindow(windowId, executor, callback(cont)) }
                .onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    private suspend fun screenshot(svc: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { cont ->
            runCatching { svc.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback(cont)) }
                .onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    private fun callback(cont: CancellableContinuation<Bitmap?>) = object : AccessibilityService.TakeScreenshotCallback {
        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
            val hb = result.hardwareBuffer
            val bmp = runCatching {
                Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
            }.getOrNull()
            runCatching { hb.close() }
            if (cont.isActive) cont.resume(bmp)
        }

        override fun onFailure(errorCode: Int) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private suspend fun recognize(bmp: Bitmap, pkg: String?): UiTree? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bmp, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val b = UiTreeBuilder(pkg, bmp.width, bmp.height)
                val root = b.add(-1, "ocr.Screen", null, null, null, null, Box(0, 0, bmp.width, bmp.height))
                for (block in text.textBlocks) for (line in block.lines) {
                    val r = line.boundingBox ?: continue
                    b.add(root, "ocr.Line", null, line.text, null, null, Box(r.left, r.top, r.right, r.bottom))
                }
                if (cont.isActive) cont.resume(b.build())
            }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }
}
