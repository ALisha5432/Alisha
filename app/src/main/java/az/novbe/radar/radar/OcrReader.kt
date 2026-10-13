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

    suspend fun read(svc: AccessibilityService): UiTree? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bmp = screenshot(svc) ?: return null
        return recognize(bmp, svc.rootInActiveWindow?.packageName?.toString())
    }

    private suspend fun screenshot(svc: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                svc.takeScreenshot(
                    Display.DEFAULT_DISPLAY, executor,
                    object : AccessibilityService.TakeScreenshotCallback {
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
                    },
                )
            }.onFailure { if (cont.isActive) cont.resume(null) }
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
