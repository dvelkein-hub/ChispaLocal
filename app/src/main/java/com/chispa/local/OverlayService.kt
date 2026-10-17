package com.chispa.local

import android.app.*
import android.content.*
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import kotlin.math.abs

class OverlayService: Service(){
    private var floating:View?=null
    private lateinit var manager:WindowManager
    private val channel="chispa_active"
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){
        super.onCreate()
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel,"Chispa activa",NotificationManager.IMPORTANCE_LOW))
        val pi=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notice=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Chispa está contigo").setContentText("Pulsa para abrir el chat")
            .setContentIntent(pi).setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(50,notice,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(50,notice)
        if(!android.provider.Settings.canDrawOverlays(this)){stopSelf();return}
        manager=getSystemService(WINDOW_SERVICE) as WindowManager
        val params=WindowManager.LayoutParams(dp(82),dp(82),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply{
            gravity=Gravity.TOP or Gravity.START
            x=resources.displayMetrics.widthPixels-dp(104)
            y=dp(175)
        }
        val face=object:View(this){
            val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c:Canvas){
                val s=width.toFloat()
                paint.color=Color.rgb(12,27,51)
                c.drawRoundRect(0f,0f,s,s,18f,18f,paint)
                paint.color=Color.rgb(52,227,225)
                c.drawRoundRect(12f,12f,s-12f,s-12f,18f,18f,paint)
                paint.color=Color.rgb(22,68,143)
                c.drawRect(20f,34f,s-20f,58f,paint)
                paint.color=Color.WHITE
                c.drawRect(26f,39f,35f,48f,paint);c.drawRect(s-35f,39f,s-26f,48f,paint)
                paint.color=Color.rgb(255,142,176)
                c.drawRect(s/2-5f,52f,s/2+5f,56f,paint)
            }
        }
        val slop=ViewConfiguration.get(this).scaledTouchSlop
        var sx=0f;var sy=0f;var px=0;var py=0;var moved=false
        face.setOnTouchListener { _,e ->
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{ sx=e.rawX;sy=e.rawY;px=params.x;py=params.y;moved=false;true }
                MotionEvent.ACTION_MOVE->{
                    if(abs(e.rawX-sx)>slop||abs(e.rawY-sy)>slop)moved=true
                    if(moved){
                        params.x=(px+(e.rawX-sx).toInt()).coerceAtLeast(0)
                        params.y=(py+(e.rawY-sy).toInt()).coerceAtLeast(0)
                        manager.updateViewLayout(face,params)
                    };true
                }
                MotionEvent.ACTION_UP->{
                    if(!moved) startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    true
                }
                else->false
            }
        }
        try{manager.addView(face,params);floating=face}catch(e:Exception){stopSelf()}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int)=START_STICKY
    override fun onDestroy(){
        floating?.let{try{manager.removeView(it)}catch(_:Exception){}}
        super.onDestroy()
    }
}
