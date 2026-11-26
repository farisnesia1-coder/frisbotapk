package id.friss.assist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AccessibilityCaptureService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

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