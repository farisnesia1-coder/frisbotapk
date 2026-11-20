package id.friss.assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast

class LensCaptureService : Service() {
 private var projection:MediaProjection?=null
 private var virtualDisplay:VirtualDisplay?=null
 private var reader:ImageReader?=null
 private val handler=Handler(Looper.getMainLooper())
 private var pending=false

 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  when(intent?.action){
   ACTION_START -> startProjection(intent)
   ACTION_CAPTURE -> captureFrame()
   ACTION_STOP -> stopSelf()
   else -> if(projection==null)stopSelf()
  }
  return START_NOT_STICKY
 }
 private fun startProjection(intent:Intent){
  startForegroundNotification()
  if(projection!=null)return
  val code=intent.getIntExtra(EXTRA_RESULT_CODE,0)
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(EXTRA_RESULT_DATA,Intent::class.java) else intent.getParcelableExtra(EXTRA_RESULT_DATA)
  if(data==null){stopSelf();return}
  try{
   val mgr=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   projection=mgr.getMediaProjection(code,data)
   projection?.registerCallback(object:MediaProjection.Callback(){
    override fun onStop(){isActive=false;getSharedPreferences("friss_settings",MODE_PRIVATE).edit().putBoolean("lens_active",false).apply();stopSelf()}
   },handler)
   val wm=getSystemService(WINDOW_SERVICE) as WindowManager
   val metrics=DisplayMetrics()
   @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
   reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,3)
   virtualDisplay=projection?.createVirtualDisplay("FRISS-LENS-SESSION",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,
    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)
   isActive=true
   getSharedPreferences("friss_settings",MODE_PRIVATE).edit().putBoolean("lens_active",true).apply()
   notifyStatus("Sesi aktif — gunakan Google Lens dari panel")
  }catch(e:Exception){isActive=false;Toast.makeText(this,"Gagal mengaktifkan Lens: ${e.message}",Toast.LENGTH_LONG).show();stopSelf()}
 }
 private fun captureFrame(){
  if(projection==null||reader==null){Toast.makeText(this,"Aktifkan izin Lens dari aplikasi FRISS ASSIST dulu.",Toast.LENGTH_LONG).show();return}
  if(pending)return
  pending=true
  handler.postDelayed({
   try{
    val image=reader?.acquireLatestImage()
    if(image==null){Toast.makeText(this,"Layar belum tertangkap. Coba lagi.",Toast.LENGTH_SHORT).show();return@postDelayed}
    val plane=image.planes[0];val w=image.width;val h=image.height
    val paddedW=plane.rowStride/plane.pixelStride
    val padded=Bitmap.createBitmap(paddedW,h,Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)
    val bitmap=Bitmap.createBitmap(padded,0,0,w,h)
    image.close();padded.recycle()
    LensCaptureStore.screenshot?.recycle();LensCaptureStore.screenshot=bitmap
    startActivity(Intent(this,LensSelectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   }catch(e:Exception){Toast.makeText(this,"Gagal mengambil layar: ${e.message}",Toast.LENGTH_LONG).show()}
   finally{pending=false}
  },500L)
 }
 private fun startForegroundNotification(){
  val id="friss_lens_capture"
  if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(id,"FRISS Lens capture",NotificationManager.IMPORTANCE_LOW))
  val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,id) else Notification.Builder(this)
  val n=b.setContentTitle("FRISS Lens").setContentText("Menyiapkan sesi Lens").setSmallIcon(android.R.drawable.ic_menu_search).build()
  if(Build.VERSION.SDK_INT>=29)startForeground(82,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(82,n)
 }
 private fun notifyStatus(message:String){
  val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,"friss_lens_capture") else Notification.Builder(this)
  getSystemService(NotificationManager::class.java).notify(82,b.setContentTitle("FRISS Lens").setContentText(message).setSmallIcon(android.R.drawable.ic_menu_search).build())
 }
 override fun onDestroy(){
  isActive=false;getSharedPreferences("friss_settings",MODE_PRIVATE).edit().putBoolean("lens_active",false).apply()
  handler.removeCallbacksAndMessages(null);virtualDisplay?.release();reader?.close()
  try{projection?.stop()}catch(_:Exception){};projection=null;super.onDestroy()
 }
 override fun onBind(intent:Intent?)=null
 companion object{
  const val ACTION_START="id.friss.assist.LENS_START"
  const val ACTION_CAPTURE="id.friss.assist.LENS_CAPTURE"
  const val ACTION_STOP="id.friss.assist.LENS_STOP"
  const val EXTRA_RESULT_CODE="projection_result_code"
  const val EXTRA_RESULT_DATA="projection_result_data"
  @Volatile var isActive=false
  fun requestCapture(context:Context){
   if(!isActive){Toast.makeText(context,"Aktifkan izin Lens dari aplikasi FRISS ASSIST dulu.",Toast.LENGTH_LONG).show();return}
   context.startService(Intent(context,LensCaptureService::class.java).setAction(ACTION_CAPTURE))
  }
 }
}