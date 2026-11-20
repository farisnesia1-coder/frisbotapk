package id.friss.assist
// Lens permission setup is intentionally hosted in the main app, not the floating panel.
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.media.projection.MediaProjectionManager

class MainActivity : Activity() {
 private val blue=Color.rgb(22,119,255); private val ink=Color.rgb(17,37,64); private val muted=Color.rgb(103,122,148)
 private lateinit var content:LinearLayout; private val projectionRequest=8201
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render()}
 override fun onResume(){super.onResume();if(::content.isInitialized)render()}
 private fun render(){
  val page=LinearLayout(this).apply{orientation=1;setPadding(dp(22),dp(24),dp(22),dp(34));background=AnimatedBlueBackground()}
  val scroll=ScrollView(this).apply{isFillViewport=true;addView(page)}
  val brand=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(22),dp(24),dp(22),dp(24));background=rounded(Color.WHITE,24,0xFFE1EBFA.toInt())}
  brand.addView(ImageView(this).apply{setImageResource(R.drawable.ic_friss_robot);scaleType=ImageView.ScaleType.FIT_CENTER},LinearLayout.LayoutParams(dp(76),dp(76)))
  val bt=LinearLayout(this).apply{orientation=1;setPadding(dp(14),0,0,0)}
  bt.addView(txt("ASISTEN FRISS",24f,ink,true));bt.addView(txt("Smart tools. One floating assistant.",14f,muted,false))
  brand.addView(bt,LinearLayout.LayoutParams(0,-2,1f));page.addView(brand)
  val hero=LinearLayout(this).apply{orientation=1;setPadding(dp(18),dp(18),dp(18),dp(18));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xFF126BEB.toInt(),0xFF3998FF.toInt())).apply{cornerRadius=dp(22).toFloat()}}
  hero.addView(txt("FRISS SMART PANEL",22f,Color.WHITE,true))
  hero.addView(txt("Akses Lens dan kontrol asisten langsung dari panel mengambang.",15f,0xFFEAF3FF.toInt(),false))
  page.addView(hero,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
  page.addView(txt("Status perangkat",22f,ink,true).apply{setPadding(dp(2),dp(24),0,dp(4))})
  page.addView(txt("Semua akses penting dalam satu tempat.",14f,muted,false).apply{setPadding(dp(2),0,0,dp(14))})
  val status=card()
  status.addView(statusRow("Izin tampil di atas aplikasi",Settings.canDrawOverlays(this)))
  status.addView(statusRow("Izin kontrol kecerahan",Settings.System.canWrite(this)))
  status.addView(statusRow("Sesi Lens aktif",LensCaptureService.isActive));page.addView(status)
  action(page,"Izinkan overlay","Agar panel FRISS bisa muncul di atas aplikasi lain"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
  action(page,"Izinkan kontrol kecerahan","Berikan izin ubah pengaturan sistem"){startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:$packageName")))}
  action(page,"Aktifkan izin Lens","Konfirmasi berbagi layar Android dilakukan di sini. Selama sesi aktif, panel tidak meminta izin lagi setiap pencarian.",true){
   val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   @Suppress("DEPRECATION") startActivityForResult(mgr.createScreenCaptureIntent(),projectionRequest)
  }
  action(page,"Nonaktifkan sesi Lens","Akhiri sesi berbagi layar sampai lu mengaktifkannya lagi"){
   stopService(Intent(this,LensCaptureService::class.java));LensCaptureService.isActive=false;render()
   Toast.makeText(this,"Sesi Lens dihentikan.",Toast.LENGTH_SHORT).show()
  }
  action(page,"Aktifkan FRISS ASSIST","Tampilkan tombol robot mengambang di layar",true){
   if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Izinkan overlay terlebih dahulu.",Toast.LENGTH_SHORT).show();startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
   else{val s=Intent(this,FloatingAssistantService::class.java);if(Build.VERSION.SDK_INT>=26)startForegroundService(s) else startService(s);Toast.makeText(this,"FRISS ASSIST aktif.",Toast.LENGTH_SHORT).show()}
  }
  action(page,"Matikan floating assistant","Sembunyikan tombol robot dari layar"){stopService(Intent(this,FloatingAssistantService::class.java))}
  page.addView(txt("FRISS ASSIST  •  Versi 1.1",11f,muted,false).apply{gravity=Gravity.CENTER;setPadding(0,dp(24),0,0)})
  content=page;setContentView(scroll)
 }
 @Deprecated("Deprecated API retained for compatibility")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=projectionRequest)return
  if(resultCode!=RESULT_OK||data==null){Toast.makeText(this,"Izin Lens belum diaktifkan.",Toast.LENGTH_SHORT).show();return}
  val s=Intent(this,LensCaptureService::class.java).apply{action=LensCaptureService.ACTION_START;putExtra(LensCaptureService.EXTRA_RESULT_CODE,resultCode);putExtra(LensCaptureService.EXTRA_RESULT_DATA,data)}
  if(Build.VERSION.SDK_INT>=26)startForegroundService(s) else startService(s)
  Toast.makeText(this,"Izin Lens aktif. Tombol Google Lens di panel siap digunakan.",Toast.LENGTH_LONG).show();render()
 }
 private fun card()=LinearLayout(this).apply{orientation=1;setPadding(dp(18),dp(14),dp(18),dp(14));background=rounded(Color.WHITE,24,0xFFDCE8F8.toInt());elevation=dp(5).toFloat();clipToOutline=true}
 private fun statusRow(s:String,ok:Boolean)=txt("$s  •  ${if(ok)"Aktif" else "Belum aktif"}",13f,if(ok)Color.rgb(18,137,86) else Color.rgb(190,120,24),true).apply{setPadding(0,dp(8),0,dp(8))}
 private fun action(p:LinearLayout,t:String,d:String,primary:Boolean=false,fn:()->Unit){val v=LinearLayout(this).apply{orientation=1;setPadding(dp(22),dp(21),dp(22),dp(21));background=rounded(if(primary)Color.rgb(229,240,255) else Color.WHITE,24,if(primary)0xFFB8D4FF.toInt() else 0xFFDCE8F8.toInt());elevation=dp(if(primary)5 else 3).toFloat();minimumHeight=dp(82);isClickable=true;isFocusable=true;setOnClickListener{fn()}}
  v.addView(txt(t,18f,if(primary)blue else ink,true));v.addView(txt(d,14f,muted,false).apply{setPadding(0,dp(5),0,0)});p.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(13)})}
 private fun txt(s:String,z:Float,c:Int,b:Boolean)=TextView(this).apply{text=s;textSize=z;setTextColor(c);typeface=Typeface.create(if(b)"sans-serif-medium" else "sans-serif",if(b)Typeface.BOLD else Typeface.NORMAL);letterSpacing=if(b)0.01f else 0f;includeFontPadding=true}
 private fun rounded(c:Int,r:Int,s:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat();setStroke(dp(1),s)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}