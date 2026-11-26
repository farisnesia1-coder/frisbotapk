package id.friss.assist

import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val blue = 0xFF1677FF.toInt()
    private val ink = 0xFF10233D.toInt()
    private val muted = 0xFF71839A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (window.decorView.isAttachedToWindow) render()
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(30))
            background = AnimatedBlueBackground()
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        val brand = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(Color.WHITE, 24, 0xFFDCE8F8.toInt())
        }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.ic_friss_robot) },
            LinearLayout.LayoutParams(dp(72), dp(72)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        labels.addView(txt("ASISTEN FRISS", 23f, ink, true))
        labels.addView(txt("Smart tools. One floating assistant.", 12f, muted, false))
        brand.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(brand)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF126BEB.toInt(), 0xFF3998FF.toInt())).apply { cornerRadius = dp(22).toFloat() }
        }
        hero.addView(txt("FRISS SMART PANEL", 20f, Color.WHITE, true))
        hero.addView(txt("Scan objek dari aplikasi mana pun lewat tombol mengambang.", 14f, 0xFFEAF3FF.toInt(), false))
        page.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        page.addView(txt("Persiapan akses", 20f, ink, true).apply { setPadding(0, dp(22), 0, dp(10)) })
        val status = card()
        status.addView(statusRow("Izin overlay", Settings.canDrawOverlays(this)))
        status.addView(statusRow("Izin ubah kecerahan", Settings.System.canWrite(this)))
        status.addView(statusRow("Accessibility screenshot", isAccessibilityEnabled()))
        page.addView(status)

        action(page, "Izinkan overlay", "Agar panel FRISS tampil di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        action(page, "Izinkan kontrol kecerahan", "Mengatur brightness dari panel") {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        action(page, if (isAccessibilityEnabled()) "Accessibility sudah aktif" else "Aktifkan screenshot tanpa MediaProjection",
            if (isAccessibilityEnabled()) "Izin tersimpan. Tidak perlu diaktifkan ulang setiap kali scan." else "Aktifkan layanan aksesibilitas FRISS satu kali melalui pengaturan Android.", true) {
            if (isAccessibilityEnabled()) {
                Toast.makeText(this, "Accessibility FRISS sudah aktif.", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        action(page, "Aktifkan bot", "Tampilkan tombol robot ASISTEN FRISS") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan izin overlay terlebih dahulu.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                val service = Intent(this, FloatingAssistantService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                Toast.makeText(this, "Bot FRISS aktif.", Toast.LENGTH_SHORT).show()
            }
        }
        action(page, "Matikan bot", "Hentikan tombol dan panel mengambang") {
            stopService(Intent(this, FloatingAssistantService::class.java))
        }
        page.addView(txt("FRISS ASSIST  •  Android 11+", 11f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
        setContentView(scroll)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val target = ComponentName(this, AccessibilityCaptureService::class.java)
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val info = service.resolveInfo.serviceInfo
                info.packageName == target.packageName && info.name == target.className
            }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = rounded(Color.WHITE, 22, 0xFFDCE8F8.toInt())
        elevation = dp(4).toFloat()
    }

    private fun statusRow(label: String, enabled: Boolean) = txt(
        "$label  •  ${if (enabled) "Aktif" else "Belum aktif"}", 13f,
        if (enabled) 0xFF16865B.toInt() else 0xFFB7791F.toInt(), true
    ).apply { setPadding(0, dp(8), 0, dp(8)) }

    private fun action(parent: LinearLayout, title: String, description: String, primary: Boolean = false, run: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(if (primary) 0xFFE8F2FF.toInt() else Color.WHITE, 20,
                if (primary) 0xFFBBD6FF.toInt() else 0xFFDCE8F8.toInt())
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { run() }
        }
        item.addView(txt(title, 16f, if (primary) blue else ink, true))
        item.addView(txt(description, 12f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
        parent.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun txt(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}