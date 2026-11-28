package id.friss.assist

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.content.Context
import android.view.View
import android.view.MotionEvent
import android.graphics.Rect
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class AiOverlayPanelService : Service() {
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var basePanelHeight = 0
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendButton: Button
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<Pair<String, String>>()
    private val blue = 0xFF1677FF.toInt()
    private val ink = 0xFF10233D.toInt()
    private val muted = 0xFF71839A.toInt()
    private var waiting = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun showPanel() {
        if (root != null) return
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(10))
            background = rounded(Color.WHITE, 26, 0xFFD8E7FA.toInt())
            elevation = dp(20).toFloat()
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("FRISS AI", 19f, ink, true))
        heading.addView(text("Asisten pintar di atas aplikasi", 11f, muted, false))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = Button(this).apply {
            text = "Ukuran"; isAllCaps = false; textSize = 12f
            setTextColor(ink); backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFEAF2FF.toInt())
            setOnClickListener { cyclePanelSize() }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(72), dp(40)).apply { rightMargin = dp(6) })
        val close = Button(this).apply {
            text = "Tutup"; isAllCaps = false; textSize = 12f
            setTextColor(ink); backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFEAF2FF.toInt())
            setOnClickListener { stopSelf() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(76), dp(40)))
        page.addView(header)

        val readScreen = Button(this).apply {
            text = "Baca Layar (tanpa OCR)"; isAllCaps = false; textSize = 13f
            setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnClickListener {
                isEnabled = false
                text = "Membaca..."
                AccessibilityCaptureService.requestReadScreen(applicationContext) { screenText ->
                    main.post {
                        isEnabled = true
                        text = "Baca Layar (tanpa OCR)"
                        if (screenText.isBlank()) {
                            addMessage("assistant", "Gua belum menemukan teks yang bisa diakses di aplikasi itu. Bisa jadi aplikasinya tidak mengekspos teks ke Accessibility.")
                        } else {
                            input.setText("Tolong analisis dan jelaskan isi layar ini:\n\n" + screenText.take(10000))
                            sendMessage()
                        }
                    }
                }
            }
        }
        page.addView(readScreen, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })

        val lens = Button(this).apply {
            text = "⌕   Cari dengan Google Lens"; isAllCaps = false; textSize = 13f
            setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnClickListener {
                FloatingAssistantService.hidePanelForLens()
                stopSelf()
                main.postDelayed({
                    AccessibilityCaptureService.requestCapture(applicationContext)
                }, 400)
            }
        }
        page.addView(lens, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })

        scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(12))
        }
        scroll.addView(messages)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(6); bottomMargin = dp(6)
        })
        addMessage("assistant", "Halo! Gua FRISS AI. Ada yang bisa gua bantu? 😄")

        val composer = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFFF2F6FC.toInt(), 18, 0xFFE0EAF7.toInt())
            setPadding(dp(8), dp(5), dp(6), dp(5))
        }
        input = EditText(this).apply {
            hint = "Tulis pesan..."
            textSize = 14f
            setTextColor(ink); setHintTextColor(muted)
            setSingleLine(false); maxLines = 3; minLines = 1
            background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
            }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = Button(this).apply {
            text = "Kirim"; isAllCaps = false; textSize = 12f
            setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnClickListener { sendMessage() }
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(72), dp(42)))
        page.addView(composer)

        val resizeGrip = TextView(this).apply {
            text = "━━━━━━━━   Tarik untuk ubah ukuran   ━━━━━━━━"
            textSize = 10f; gravity = Gravity.CENTER; setTextColor(muted)
            setPadding(0, dp(8), 0, dp(2))
            setOnTouchListener { _, event -> handleResizeTouch(event); true }
        }
        page.addView(resizeGrip, LinearLayout.LayoutParams(-1, dp(30)))

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val compactWidth = minOf((screenWidth * 0.88f).toInt(), dp(420)).coerceAtLeast(dp(260))
        val compactHeight = (screenHeight * 0.58f).toInt().coerceAtLeast(dp(320))
        val lp = WindowManager.LayoutParams(
            compactWidth,
            compactHeight,
            overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        basePanelHeight = lp.height
        panelParams = lp
        root = page
        try {
            wm.addView(page, lp)
            page.viewTreeObserver.addOnGlobalLayoutListener {
                val visible = Rect()
                page.getWindowVisibleDisplayFrame(visible)
                val screenHeight = resources.displayMetrics.heightPixels
                val keyboardVisible = screenHeight - visible.bottom > dp(180)
                val params = panelParams ?: return@addOnGlobalLayoutListener
                val targetHeight = if (keyboardVisible) minOf(basePanelHeight, (visible.height() - dp(16)).coerceAtLeast(dp(260))) else basePanelHeight
                if (params.height != targetHeight) {
                    params.height = targetHeight
                    try { wm.updateViewLayout(page, params) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) { root = null; panelParams = null; stopSelf() }
    }

    private var sizePreset = 0
    private fun cyclePanelSize() {
        val heights = floatArrayOf(0.48f, 0.62f, 0.78f)
        sizePreset = (sizePreset + 1) % heights.size
        basePanelHeight = (resources.displayMetrics.heightPixels * heights[sizePreset]).toInt().coerceAtLeast(dp(320))
        panelParams?.let { it.height = basePanelHeight; try { root?.let { view -> wm.updateViewLayout(view, it) } } catch (_: Exception) {} }
        Toast.makeText(this, "Ukuran panel: ${arrayOf("Kecil", "Sedang", "Besar")[sizePreset]}", Toast.LENGTH_SHORT).show()
    }

    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var resizeStartWidth = 0
    private var resizeStartHeight = 0
    private fun handleResizeTouch(event: MotionEvent) {
        val params = panelParams ?: return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartX = event.rawX; resizeStartY = event.rawY
                resizeStartWidth = params.width; resizeStartHeight = basePanelHeight
            }
            MotionEvent.ACTION_MOVE -> {
                val maxWidth = minOf(resources.displayMetrics.widthPixels - dp(24), dp(480))
                val minWidth = dp(260)
                params.width = (resizeStartWidth + ((event.rawX - resizeStartX) * 2).toInt()).coerceIn(minWidth, maxWidth)
                basePanelHeight = (resizeStartHeight + ((event.rawY - resizeStartY) * 2).toInt()).coerceIn(dp(320), (resources.displayMetrics.heightPixels * 0.92f).toInt())
                val visible = Rect(); root?.getWindowVisibleDisplayFrame(visible)
                val keyboardVisible = resources.displayMetrics.heightPixels - visible.bottom > dp(180)
                params.height = if (keyboardVisible) minOf(basePanelHeight, (visible.height() - dp(16)).coerceAtLeast(dp(260))) else basePanelHeight
                try { root?.let { wm.updateViewLayout(it, params) } } catch (_: Exception) {}
            }
        }
    }

    private fun sendMessage() {
        if (waiting) return
        val prompt = input.text?.toString()?.trim().orEmpty()
        if (prompt.isEmpty()) return
        input.setText("")
        hideKeyboard()
        addMessage("user", prompt)
        history.add("user" to prompt)
        waiting = true
        sendButton.isEnabled = false
        sendButton.text = "..."
        val loading = addMessage("assistant", "FRISS AI sedang mengetik  ···")
        executor.execute {
            try {
                val apiKey = getSharedPreferences("friss_ai", MODE_PRIVATE)
                    .getString("enrai_api_key", "").orEmpty().trim()
                if (apiKey.isBlank()) {
                    throw Exception("API key Enrai belum diatur. Buka FRISS ASSIST lalu pilih Atur API Enrai.")
                }
                val system = "Kamu adalah FRISS AI, asisten Android buatan Faris. Jawab dalam bahasa Indonesia yang santai, natural, jelas, dan ringkas. Gunakan gua/lu seperlunya dan emoji secukupnya. Jangan mengarang fakta. Untuk soal latihan, jelaskan proses berpikir ringkas dan pembahasannya. Jika konteksnya ujian aktif, arahkan untuk belajar konsep, bukan mengerjakan ujian."
                val apiMessages = JSONArray().put(
                    JSONObject().put("role", "system").put("content", system)
                )
                history.takeLast(10).forEach { (role, value) ->
                    apiMessages.put(JSONObject().put("role", role).put("content", value))
                }
                val payload = JSONObject()
                    .put("model", "Chatgpt")
                    .put("messages", apiMessages)
                    .put("temperature", 0.7)
                val conn = (URL("https://enrai.web.id/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code !in 200..299) {
                    val detail = try {
                        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                    } catch (_: Exception) { "" }
                    throw Exception(if (detail.isNotBlank()) detail else "Server Enrai membalas HTTP $code")
                }
                val answer = extractAnswer(body)
                if (answer.isBlank()) throw Exception("Respons Enrai kosong atau formatnya belum dikenali.")
                main.post {
                    removeMessage(loading)
                    addMessage("assistant", answer)
                    history.add("assistant" to answer)
                    finishRequest()
                }
            } catch (e: Exception) {
                main.post {
                    removeMessage(loading)
                    addMessage("assistant", "Gagal menghubungi AI: ${e.message ?: "koneksi bermasalah"}. Coba lagi sebentar ya.")
                    finishRequest()
                }
            }
        }
    }

    private fun extractAnswer(raw: String): String {
        val json = JSONObject(raw)
        val choices = json.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
        val content = message?.opt("content")
        return when (content) {
            is String -> content.trim()
            is JSONArray -> {
                val parts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i)?.optString("text").orEmpty()
                    if (part.isNotBlank()) parts.add(part)
                }
                parts.joinToString("\n").trim()
            }
            else -> ""
        }
    }

    private fun addMessage(role: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (role == "user") Gravity.END else Gravity.START
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        val bubble = TextView(this).apply {
            text = value
            textSize = 13.5f
            setTextColor(if (role == "user") Color.WHITE else ink)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(if (role == "user") blue else 0xFFF0F5FC.toInt(), 16,
                if (role == "user") blue else 0xFFE3ECF7.toInt())
            maxWidth = (resources.displayMetrics.widthPixels * 0.70f).toInt()
        }
        row.addView(bubble)
        messages.addView(row, LinearLayout.LayoutParams(-1, -2))
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return row
    }

    private fun removeMessage(view: View) {
        (view.parent as? LinearLayout)?.removeView(view)
    }

    private fun finishRequest() {
        waiting = false
        sendButton.isEnabled = true
        sendButton.text = "Kirim"
    }

    private fun hideKeyboard() {
        try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(input.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        panelParams = null
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
