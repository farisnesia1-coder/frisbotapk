package id.friss.assist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class AccessibilityCaptureService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    // Reads exposed UI text only after an explicit user tap. No screenshot or OCR.
    // Try every visible application window instead of trusting the first window returned
    // by Android (which may be an empty/system window).
    private fun readCurrentWindowText(onResult: (String) -> Unit) {
        try {
            val candidates = mutableListOf<Pair<Int, AccessibilityNodeInfo>>()
            try {
                for (window in windows) {
                    if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    val node = try { window.root } catch (_: Exception) { null } ?: continue
                    val owner = node.packageName?.toString()
                    if (owner.isNullOrBlank() || owner == packageName) {
                        node.recycle()
                        continue
                    }
                    val priority = (if (window.isActive) 100000 else 0) +
                        (if (window.isFocused) 50000 else 0) + window.layer
                    candidates.add(priority to node)
                }
            } catch (_: Exception) {
                // Some Android builds restrict window enumeration; active-root fallback below.
            }

            try {
                val active = rootInActiveWindow
                if (active != null && active.packageName?.toString() != packageName) {
                    candidates.add(Int.MAX_VALUE to active)
                } else {
                    active?.recycle()
                }
            } catch (_: Exception) {}

            var bestText = ""
            val ordered = candidates.sortedByDescending { it.first }
            for ((_, targetRoot) in ordered) {
                try {
                    val output = LinkedHashSet<String>()
                    var visited = 0
                    fun walk(node: AccessibilityNodeInfo, depth: Int) {
                        if (depth > 20 || visited >= 2500 || output.sumOf { it.length } >= 20000) return
                        visited++
                        fun add(value: CharSequence?) {
                            val valueText = value?.toString()?.trim().orEmpty()
                            if (valueText.isNotBlank()) output.add(valueText)
                        }
                        add(node.text)
                        add(node.contentDescription)
                        if (Build.VERSION.SDK_INT >= 26) add(node.hintText)
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(node.paneTitle)
                            add(node.tooltipText)
                        }
                        for (index in 0 until node.childCount) {
                            if (visited >= 2500 || output.sumOf { it.length } >= 20000) break
                            val child = try { node.getChild(index) } catch (_: Exception) { null } ?: continue
                            try { walk(child, depth + 1) } finally { child.recycle() }
                        }
                    }
                    walk(targetRoot, 0)
                    val extracted = output.joinToString("\n").take(20000)
                    if (extracted.length > bestText.length) bestText = extracted
                } catch (_: Exception) {
                    // Continue to the next visible app window.
                } finally {
                    try { targetRoot.recycle() } catch (_: Exception) {}
                }
                if (bestText.length >= 20000) break
            }
            onResult(bestText)
        } catch (_: Exception) {
            onResult("")
        }
    }

    fun requestReadScreen(onResult: (String) -> Unit) {
        readCurrentWindowText(onResult)
    }

    fun captureCurrentScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Screenshot aksesibilitas membutuhkan Android 11+.", Toast.LENGTH_LONG).show()
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hardware = result.hardwareBuffer
                    val wrapped = Bitmap.wrapHardwareBuffer(hardware, result.colorSpace)
                    val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                    hardware.close()
                    wrapped?.recycle()
                    if (bitmap == null) {
                        Toast.makeText(this@AccessibilityCaptureService, "Gagal membaca screenshot.", Toast.LENGTH_SHORT).show()
                        return
                    }
                    LensCaptureStore.screenshot?.recycle()
                    LensCaptureStore.screenshot = bitmap
                    val intent = Intent(this@AccessibilityCaptureService, LensSelectionOverlayService::class.java)
                        .setAction(LensSelectionOverlayService.ACTION_SHOW)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundServiceIfNeeded(intent) else startService(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@AccessibilityCaptureService, "Capture gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(errorCode: Int) {
                Toast.makeText(this@AccessibilityCaptureService, "Android menolak screenshot (kode $errorCode).", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun startForegroundServiceIfNeeded(intent: Intent) {
        // Short-lived overlay work is started as a regular service while the accessibility service is active.
        startService(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: AccessibilityCaptureService? = null

        fun requestReadScreen(context: Context, onResult: (String) -> Unit) {
            val service = instance
            if (service == null) {
                Toast.makeText(context, "Aktifkan layanan Accessibility FRISS terlebih dahulu.", Toast.LENGTH_LONG).show()
                onResult("")
                return
            }
            service.requestReadScreen(onResult)
        }

        fun requestCapture(context: Context) {
            val service = instance
            if (service == null) {
                // Jangan melempar pengguna ke Settings setiap kali scan.
                // Android dapat membutuhkan waktu untuk menghubungkan ulang service.
                Toast.makeText(context, "Layanan FRISS belum siap. Tunggu sebentar lalu coba scan lagi; izin tidak perlu diaktifkan ulang.", Toast.LENGTH_LONG).show()
                return
            }
            service.captureCurrentScreen()
        }
    }
}