package id.friss.assist

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
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
            restoreAssistant()
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
                        restoreAssistant()
                        return
                    }
                    LensCaptureStore.screenshot?.recycle()
                    LensCaptureStore.screenshot = bitmap
                    startActivity(Intent(this@AccessibilityCaptureService, LensSelectionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
                } catch (e: Exception) {
                    Toast.makeText(this@AccessibilityCaptureService, "Capture gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    restoreAssistant()
                }
            }

            override fun onFailure(errorCode: Int) {
                Toast.makeText(this@AccessibilityCaptureService, "Android menolak screenshot (kode $errorCode).", Toast.LENGTH_LONG).show()
                restoreAssistant()
            }
        })
    }

    private fun restoreAssistant() {
        val intent = Intent(this, FloatingAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
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
                Toast.makeText(context, "Aktifkan Layanan Aksesibilitas FRISS ASSIST di Pengaturan.", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            service.captureCurrentScreen()
        }
    }
}