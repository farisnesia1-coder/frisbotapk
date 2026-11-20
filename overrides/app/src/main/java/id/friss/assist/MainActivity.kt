package id.friss.assist

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val blue = Color.rgb(22, 119, 255)
    private val ink = Color.rgb(17, 37, 64)
    private val muted = Color.rgb(103, 122, 148)
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(Color.rgb(244, 248, 255))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.WHITE, 24, 0xFFE1EBFA.toInt())
        }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_friss_robot)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(62), dp(62)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        brandText.addView(text("ASISTEN FRISS", 21f, ink, true))
        brandText.addView(text("Asisten pintar dalam satu panel", 12f, muted, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        brand.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(brand)

        page.addView(text("Kontrol cepat", 19f, ink, true).apply {
            setPadding(dp(2), dp(26), 0, dp(4))
        })
        page.addView(text("Atur izin dan aktifkan fitur yang lu butuhkan.", 13f, muted, false)
            .apply { setPadding(dp(2), 0, 0, dp(14)) })

        val statusCard = card()
        statusCard.addView(statusRow("Izin tampil di atas aplikasi", Settings.canDrawOverlays(this)))
        statusCard.addView(statusRow("Izin kontrol kecerahan", Settings.System.canWrite(this)))
        page.addView(statusCard)

        actionCard(page, "Izinkan overlay", "Agar panel FRISS bisa muncul di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        actionCard(page, "Izinkan kontrol kecerahan", "Berikan izin ubah pengaturan sistem") {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        actionCard(page, "Aktifkan FRISS ASSIST", "Tampilkan tombol robot mengambang di layar", true) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Izinkan overlay terlebih dahulu.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                val service = Intent(this, FloatingAssistantService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
                Toast.makeText(this, "FRISS ASSIST aktif.", Toast.LENGTH_SHORT).show()
            }
        }
        actionCard(page, "FRISS Lens", "Pilih bagian layar untuk dibaca dan dicari", true) {
            try { startActivity(Intent(this, LensCaptureActivity::class.java)) }
            catch (e: Exception) { Toast.makeText(this, "FRISS Lens gagal dibuka: ${e.message}", Toast.LENGTH_LONG).show() }
        }
        actionCard(page, "Matikan floating assistant", "Sembunyikan tombol robot dari layar") {
            stopService(Intent(this, FloatingAssistantService::class.java))
            Toast.makeText(this, "FRISS ASSIST dimatikan.", Toast.LENGTH_SHORT).show()
        }

        page.addView(text("FRISS ASSIST  •  Versi 1.0", 11f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
        setContentView(scroll)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(10), dp(15), dp(10))
        background = rounded(Color.WHITE, 20, 0xFFE1EBFA.toInt())
    }

    private fun statusRow(label: String, granted: Boolean) = text(
        "$label   •   ${if (granted) "Diizinkan" else "Belum diizinkan"}",
        13f, if (granted) Color.rgb(18, 137, 86) else Color.rgb(190, 120, 24), true
    ).apply { setPadding(0, dp(8), 0, dp(8)) }

    private fun actionCard(parent: LinearLayout, title: String, desc: String, primary: Boolean = false, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(if (primary) Color.rgb(232, 241, 255) else Color.WHITE, 20, 0xFFE1EBFA.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        item.addView(text(title, 15f, if (primary) blue else ink, true))
        item.addView(text(desc, 12f, muted, false).apply { setPadding(0, dp(5), 0, 0) })
        parent.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}