package id.friss.assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class FloatingAssistantService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var opacity = 85
    private var bubbleSize = 58
    private val prefs by lazy { getSharedPreferences("friss_settings", MODE_PRIVATE) }
    private val blue = 0xFF1677FF.toInt()
    private val dark = 0xFF10233D.toInt()

    override fun onCreate() {
        super.onCreate()
        createNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        opacity = prefs.getInt("bubble_opacity", 85).coerceIn(0, 100)
        bubbleSize = prefs.getInt("bubble_size", 58).coerceIn(42, 92)
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    private fun createNotification() {
        val id = "friss_assist_running"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, "FRISS ASSIST", NotificationManager.IMPORTANCE_LOW))
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, id) else Notification.Builder(this)
        startForeground(41, builder.setContentTitle("ASISTEN FRISS aktif")
            .setContentText("Panel mengambang siap digunakan")
            .setSmallIcon(android.R.drawable.ic_menu_view).build())
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        if (bubble != null) return
        val view = TextView(this).apply {
            text = "AF"; textSize = 21f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(blue); setStroke(dp(2), Color.WHITE) }
            alpha = opacity / 100f; elevation = dp(8).toFloat()
            setOnClickListener { togglePanel() }; setOnTouchListener(DragTouchListener())
        }
        val lp = WindowManager.LayoutParams(dp(bubbleSize), dp(bubbleSize), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(18); y = dp(180)
        }
        bubble = view; bubbleParams = lp
        windowManager.addView(view, lp)
    }

    private fun togglePanel() {
        if (panel != null) { removePanel(); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(0xF7FFFFFF.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFFD8E7FA.toInt()) }
            elevation = dp(12).toFloat()
        }
        box.addView(TextView(this).apply {
            text = "ASISTEN FRISS"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(blue)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_friss_robot, 0, 0, 0); compoundDrawablePadding = dp(8)
        })
        box.addView(TextView(this).apply { text = "Quick control"; textSize = 12f; setTextColor(0xFF64748B.toInt()) })

        val sizeLabel = TextView(this).apply { text = "Ukuran tombol panel: ${bubbleSize}dp"; textSize = 13f; setTextColor(dark) }
        box.addView(sizeLabel)
        box.addView(SeekBar(this).apply {
            max = 50; progress = bubbleSize - 42
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (!user) return
                    bubbleSize = (42 + p).coerceIn(42, 92); prefs.edit().putInt("bubble_size", bubbleSize).apply()
                    bubbleParams?.let { it.width = dp(bubbleSize); it.height = dp(bubbleSize); try { bubble?.let { v -> windowManager.updateViewLayout(v, it) } } catch (_: Exception) {} }
                    sizeLabel.text = "Ukuran tombol panel: ${bubbleSize}dp"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        val opacityLabel = TextView(this).apply { text = "Opacity logo: $opacity%"; textSize = 13f; setTextColor(dark); setPadding(0, dp(8), 0, 0) }
        box.addView(opacityLabel)
        box.addView(SeekBar(this).apply {
            max = 100; progress = opacity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (!user) return
                    opacity = p.coerceIn(0, 100); prefs.edit().putInt("bubble_opacity", opacity).apply()
                    bubble?.alpha = opacity / 100f; opacityLabel.text = "Opacity logo: $opacity%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        val brightness = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (_: Exception) { 128 }
        val brightnessSeek = SeekBar(this).apply {
            max = 255; progress = brightness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    if (!user) return
                    if (Settings.System.canWrite(this@FloatingAssistantService))
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, p.coerceAtLeast(1))
                    else Toast.makeText(this@FloatingAssistantService, "Aktifkan izin kecerahan dari aplikasi.", Toast.LENGTH_SHORT).show()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        addLabel(box, "Kecerahan layar"); box.addView(brightnessSeek)
        addAction(box, "Google Lens", true) { removePanel(); AccessibilityCaptureService.requestCapture(this) }
        addAction(box, "Sembunyikan panel", false) { removePanel() }
        addAction(box, "Matikan panel", false) { stopSelf() }

        val lp = WindowManager.LayoutParams(dp(280), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleParams?.x ?: dp(18)).coerceAtMost(resources.displayMetrics.widthPixels - dp(290))
            y = (bubbleParams?.y ?: dp(180)) + dp(66)
        }
        panel = box
        try { windowManager.addView(box, lp) } catch (_: Exception) { panel = null }
    }

    private fun addLabel(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply { text = value; textSize = 13f; setTextColor(dark); setPadding(0, dp(8), 0, 0) })
    }

    private fun addAction(parent: LinearLayout, value: String, primary: Boolean, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = value; isAllCaps = false; textSize = 13f
            setTextColor(if (primary) Color.WHITE else dark)
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) blue else 0xFFEAF2FF.toInt())
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(5) })
    }

    private fun removePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var sx = 0; private var sy = 0; private var tx = 0f; private var ty = 0f; private var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = bubbleParams ?: return false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false; return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) moved = true
                    lp.x = sx + dx; lp.y = sy + dy
                    try { windowManager.updateViewLayout(v, lp) } catch (_: Exception) {}
                    return true
                }
                MotionEvent.ACTION_UP -> { if (!moved) v.performClick(); return true }
            }
            return false
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        removePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null; super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null

}