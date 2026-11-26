package id.friss.assist

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URLEncoder

class LensResultOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val query = intent?.getStringExtra(EXTRA_QUERY)?.trim().orEmpty()
        if (query.isBlank()) { stopSelf(); return START_NOT_STICKY }
        showResults(query)
        return START_NOT_STICKY
    }

    private fun showResults(query: String) {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 22, 0xFFD7E6FA.toInt())
            clipToOutline = true
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF126BEB.toInt(), 0xFF3998FF.toInt())).apply {
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        val title = TextView(this).apply {
            text = "FRISS LENS  ·  HASIL"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "×"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0); setOnClickListener { stopSelf() }
        })
        title.setOnTouchListener(object : View.OnTouchListener {
            private var sx = 0f; private var sy = 0f; private var startX = 0; private var startY = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val lp = params ?: return false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; startX = lp.x; startY = lp.y; return true }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (e.rawX - sx).toInt()
                        lp.y = startY + (e.rawY - sy).toInt()
                        try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = query; textSize = 14f; setTextColor(0xFF29415F.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(8)); maxLines = 2
        })
        val web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(10), dp(7), dp(10), dp(10)) }
        footer.addView(Button(this).apply {
            text = "Pilih area lagi"; isAllCaps = false
            setOnClickListener {
                stopSelf()
                AccessibilityCaptureService.requestCapture(this@LensResultOverlayService)
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        footer.addView(Button(this).apply {
            text = "Tutup"; isAllCaps = false
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        root.addView(footer)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            minOf(dp(350), resources.displayMetrics.widthPixels - dp(24)),
            minOf(dp(570), resources.displayMetrics.heightPixels - dp(100)),
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(90) }
        params = lp; panel = root
        try { wm.addView(root, lp) } catch (_: Exception) { stopSelf() }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
    companion object { const val EXTRA_QUERY = "friss_lens_query" }
}