package id.friss.assist

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class LensSelectionOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var screenshot: Bitmap? = null
    private var selector: AreaSelector? = null
    private var processing = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) showSelector()
        return START_NOT_STICKY
    }

    private fun showSelector() {
        removeOverlay()
        screenshot = LensCaptureStore.screenshot
        val bitmap = screenshot
        if (bitmap == null) { stopSelf(); return }

        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.BLACK)
        val area = AreaSelector(bitmap)
        selector = area
        frame.addView(area, FrameLayout.LayoutParams(-1, -1))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xF7FFFFFF.toInt())
                cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        bottom.addView(TextView(this).apply {
            text = "PILIH AREA LAYAR"
            textSize = 16f
            setTextColor(0xFF126BEB.toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        bottom.addView(TextView(this).apply {
            text = "Geser kotak biru ke teks atau objek yang ingin dipindai."
            textSize = 12f
            setTextColor(0xFF52647C.toInt())
            setPadding(0, dp(3), 0, dp(8))
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Batal"; isAllCaps = false
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        actions.addView(Button(this).apply {
            text = "Pindai"; isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1677FF.toInt())
            setOnClickListener { processSelection() }
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        bottom.addView(actions)
        frame.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        root = frame
        try { wm.addView(frame, lp) } catch (e: Exception) {
            Toast.makeText(this, "Overlay tidak bisa ditampilkan. Periksa izin overlay.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun processSelection() {
        if (processing) return
        val source = screenshot ?: return
        val rect = selector?.getCropRect(source.width, source.height) ?: return
        if (rect.width() < 12 || rect.height() < 12) {
            Toast.makeText(this, "Area terlalu kecil.", Toast.LENGTH_SHORT).show()
            return
        }
        processing = true
        val crop = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        Toast.makeText(this, "Memindai area…", Toast.LENGTH_SHORT).show()
        val input = InputImage.fromBitmap(crop, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        recognizer.process(input)
            .addOnSuccessListener { textResult ->
                labeler.process(input)
                    .addOnSuccessListener { labels ->
                        val text = textResult.text.trim()
                        val visual = labels.sortedByDescending { it.confidence }.take(3).joinToString(" ") { it.text }
                        val query = when {
                            text.isNotBlank() && visual.isNotBlank() -> "$text $visual"
                            text.isNotBlank() -> text
                            visual.isNotBlank() -> visual
                            else -> ""
                        }
                        recognizer.close()
                        labeler.close()
                        crop.recycle()
                        if (query.isBlank()) {
                            Toast.makeText(this, "Teks atau objek belum dikenali. Coba pilih area yang lebih jelas.", Toast.LENGTH_LONG).show()
                            processing = false
                        } else {
                            val resultIntent = Intent(this, LensResultOverlayService::class.java)
                                .putExtra(LensResultOverlayService.EXTRA_QUERY, query)
                            startService(resultIntent)
                            stopSelf()
                        }
                    }
                    .addOnFailureListener {
                        val query = textResult.text.trim()
                        recognizer.close()
                        labeler.close()
                        crop.recycle()
                        if (query.isBlank()) {
                            Toast.makeText(this, "Pemindaian gagal. Coba lagi.", Toast.LENGTH_LONG).show()
                            processing = false
                        } else {
                            startService(Intent(this, LensResultOverlayService::class.java)
                                .putExtra(LensResultOverlayService.EXTRA_QUERY, query))
                            stopSelf()
                        }
                    }
            }
            .addOnFailureListener {
                recognizer.close()
                labeler.close()
                crop.recycle()
                Toast.makeText(this, "Pemindaian gagal. Coba pilih area lagi.", Toast.LENGTH_LONG).show()
                processing = false
            }
    }

    private inner class AreaSelector(private val bitmap: Bitmap) : View(this@LensSelectionOverlayService) {
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1680FF.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat()
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val selection = RectF()
        private val imageBounds = RectF()
        private var lastX = 0f
        private var lastY = 0f
        private var moving = false
        private val handleRadius = dp(12).toFloat()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val left = (width - dw) / 2f
            val top = (height - dh) / 2f
            imageBounds.set(left, top, left + dw, top + dh)
            canvas.drawColor(0xFF101B2B.toInt())
            canvas.drawBitmap(bitmap, null, imageBounds, imagePaint)
            if (selection.isEmpty) {
                val w = dw * .68f; val h = dh * .28f
                selection.set(left + (dw - w) / 2f, top + dh * .30f, left + (dw + w) / 2f, top + dh * .30f + h)
            }
            canvas.save()
            canvas.clipRect(imageBounds)
            canvas.drawRect(imageBounds, shadePaint)
            val save = canvas.save()
            canvas.clipRect(selection)
            canvas.drawBitmap(bitmap, null, imageBounds, imagePaint)
            canvas.restoreToCount(save)
            canvas.drawRoundRect(selection, dp(8).toFloat(), dp(8).toFloat(), borderPaint)
            canvas.drawCircle(selection.left, selection.top, handleRadius, handlePaint)
            canvas.drawCircle(selection.right, selection.top, handleRadius, handlePaint)
            canvas.drawCircle(selection.left, selection.bottom, handleRadius, handlePaint)
            canvas.drawCircle(selection.right, selection.bottom, handleRadius, handlePaint)
            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    moving = selection.contains(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (moving) {
                        val w = selection.width(); val h = selection.height()
                        val l = (selection.left + dx).coerceIn(imageBounds.left, imageBounds.right - w)
                        val t = (selection.top + dy).coerceIn(imageBounds.top, imageBounds.bottom - h)
                        selection.set(l, t, l + w, t + h)
                    } else {
                        selection.right = (selection.right + dx).coerceIn(selection.left + dp(70), imageBounds.right)
                        selection.bottom = (selection.bottom + dy).coerceIn(selection.top + dp(70), imageBounds.bottom)
                    }
                    lastX = event.x; lastY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> return true
            }
            return true
        }

        fun getCropRect(sourceWidth: Int, sourceHeight: Int): Rect {
            val sx = sourceWidth / imageBounds.width()
            val sy = sourceHeight / imageBounds.height()
            val l = ((selection.left - imageBounds.left) * sx).toInt().coerceIn(0, sourceWidth - 1)
            val t = ((selection.top - imageBounds.top) * sy).toInt().coerceIn(0, sourceHeight - 1)
            val r = ((selection.right - imageBounds.left) * sx).toInt().coerceIn(l + 1, sourceWidth)
            val b = ((selection.bottom - imageBounds.top) * sy).toInt().coerceIn(t + 1, sourceHeight)
            return Rect(l, t, r, b)
        }
    }

    private fun removeOverlay() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removeOverlay()
        selector = null
        screenshot?.let { if (LensCaptureStore.screenshot === it) LensCaptureStore.screenshot = null; it.recycle() }
        screenshot = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object { const val ACTION_SHOW = "id.friss.assist.SHOW_LENS_SELECTOR" }
}