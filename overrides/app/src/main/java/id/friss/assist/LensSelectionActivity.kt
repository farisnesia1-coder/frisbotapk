package id.friss.assist

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class LensSelectionActivity : Activity() {
    private var screenshot: Bitmap? = null
    private lateinit var selector: ScreenAreaSelector
    private var processing = false
    private var handedToResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenshot = LensCaptureStore.screenshot
        val image = screenshot
        if (image == null) { finish(); return }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF4F8FF.toInt()) }
        root.addView(TextView(this).apply {
            text = "PILIH AREA LAYAR"; textSize = 19f; setTextColor(0xFF126BEB.toInt())
            setPadding(dp(18), dp(18), dp(18), dp(6)); typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Geser kotak biru ke objek atau teks yang ingin dicari."
            textSize = 13f; setTextColor(0xFF52647C.toInt()); setPadding(dp(18), 0, dp(18), dp(12))
        })
        selector = ScreenAreaSelector(this, image)
        root.addView(selector, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(14), dp(10), dp(14), dp(14)) }
        actions.addView(Button(this).apply {
            text = "X  Batal"; isAllCaps = false; setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
        actions.addView(Button(this).apply {
            text = "✓  Cari"; isAllCaps = false; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1677FF.toInt())
            setOnClickListener { processSelection() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        root.addView(actions)
        setContentView(root)
    }

    private fun processSelection() {
        if (processing) return
        val source = screenshot ?: return
        val rect = selector.getCropRect(source.width, source.height)
        if (rect.width() < 12 || rect.height() < 12) {
            Toast.makeText(this, "Area terlalu kecil.", Toast.LENGTH_SHORT).show()
            return
        }
        processing = true
        val crop = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { result ->
                val query = result.text.trim().ifBlank { "Pencarian objek visual" }
                recognizer.close()
                crop.recycle()
                showFloatingResults(query)
            }
            .addOnFailureListener {
                recognizer.close()
                crop.recycle()
                Toast.makeText(this, "Pembacaan teks gagal. Coba pilih area yang lebih jelas.", Toast.LENGTH_LONG).show()
                processing = false
            }
    }

    private fun showFloatingResults(query: String) {
        handedToResult = true
        startService(Intent(this, LensResultOverlayService::class.java).putExtra(LensResultOverlayService.EXTRA_QUERY, query))
        finish()
    }

    override fun onDestroy() {
        if (!handedToResult) {
            val restore = Intent(this, FloatingAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restore) else startService(restore)
        }
        screenshot?.recycle()
        screenshot = null
        LensCaptureStore.screenshot = null
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class ScreenAreaSelector(context: android.content.Context, private val bitmap: Bitmap) : View(context) {
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt() }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1677FF.toInt(); style = Paint.Style.STROKE; strokeWidth = 5f }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val selection = RectF()
        private val imageBounds = RectF()
        private var lastX = 0f; private var lastY = 0f; private var resizing = false
        private val handleRadius = 22f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dw = bitmap.width * scale; val dh = bitmap.height * scale
            val left = (width - dw) / 2f; val top = (height - dh) / 2f
            imageBounds.set(left, top, left + dw, top + dh)
            canvas.drawColor(0xFF17263A.toInt())
            canvas.drawBitmap(bitmap, null, imageBounds, imagePaint)
            if (selection.isEmpty) selection.set(left + dw * .15f, top + dh * .25f, left + dw * .85f, top + dh * .60f)
            canvas.save()
            canvas.clipRect(imageBounds)
            canvas.drawRect(imageBounds, shadePaint)
            val save = canvas.save()
            canvas.clipRect(selection)
            canvas.drawBitmap(bitmap, null, imageBounds, imagePaint)
            canvas.restoreToCount(save)
            canvas.drawRoundRect(selection, 8f, 8f, borderPaint)
            canvas.drawCircle(selection.left, selection.top, handleRadius, handlePaint)
            canvas.drawCircle(selection.right, selection.top, handleRadius, handlePaint)
            canvas.drawCircle(selection.left, selection.bottom, handleRadius, handlePaint)
            canvas.drawCircle(selection.right, selection.bottom, handleRadius, handlePaint)
            canvas.restore()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y
                    resizing = kotlin.math.abs(event.x - selection.right) < handleRadius * 1.8f &&
                        kotlin.math.abs(event.y - selection.bottom) < handleRadius * 1.8f
                    return true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    if (resizing) {
                        selection.right = (selection.right + dx).coerceIn(selection.left + 70f, imageBounds.right)
                        selection.bottom = (selection.bottom + dy).coerceIn(selection.top + 70f, imageBounds.bottom)
                    } else {
                        val w = selection.width(); val h = selection.height()
                        val l = (selection.left + dx).coerceIn(imageBounds.left, imageBounds.right - w)
                        val t = (selection.top + dy).coerceIn(imageBounds.top, imageBounds.bottom - h)
                        selection.set(l, t, l + w, t + h)
                    }
                    lastX = event.x; lastY = event.y; invalidate(); return true
                }
                MotionEvent.ACTION_UP -> return true
            }
            return true
        }

        fun getCropRect(sourceWidth: Int, sourceHeight: Int): android.graphics.Rect {
            val sx = sourceWidth / imageBounds.width(); val sy = sourceHeight / imageBounds.height()
            val l = ((selection.left - imageBounds.left) * sx).toInt().coerceIn(0, sourceWidth - 1)
            val t = ((selection.top - imageBounds.top) * sy).toInt().coerceIn(0, sourceHeight - 1)
            val r = ((selection.right - imageBounds.left) * sx).toInt().coerceIn(l + 1, sourceWidth)
            val b = ((selection.bottom - imageBounds.top) * sy).toInt().coerceIn(t + 1, sourceHeight)
            return android.graphics.Rect(l, t, r, b)
        }
    }
}