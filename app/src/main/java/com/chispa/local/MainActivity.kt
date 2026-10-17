package com.chispa.local

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*

class MainActivity : Activity() {
    private lateinit var history: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var entry: EditText
    private lateinit var memory: LocalMemory
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(9,19,38)
        memory=LocalMemory(this)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(18),dp(16),dp(12))
            setBackgroundColor(Color.rgb(9,19,38))
        }
        setContentView(root)
        fun title(s:String,size:Float=16f)=TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); setPadding(0,dp(8),0,dp(8)) }
        root.addView(title("✦ CHISPA LOCAL",24f))
        root.addView(title("v0.1 • Chat básico sin internet",13f))
        val show=Button(this).apply {
            text="Mostrar personaje flotante"
            setOnClickListener { startOverlay() }
        }
        root.addView(show)
        root.addView(Button(this).apply { text="Ocultar personaje"; setOnClickListener { stopService(Intent(this@MainActivity,OverlayService::class.java)) } })
        root.addView(title("Prueba: hola, 2+2, batería, hora, recuerda que me gusta el azul, mis notas",12f))
        scroll=ScrollView(this)
        history=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(history)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        entry=EditText(this).apply {
            hint="Habla con Chispa..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
            imeOptions=EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_SEND){ send();true } else false }
        }
        bottom.addView(entry,LinearLayout.LayoutParams(0,dp(54),1f))
        bottom.addView(Button(this).apply { text="Enviar"; setOnClickListener { send() } })
        root.addView(bottom)
        memory.messages().forEach { addLine(it) }
        if(history.childCount==0) addLine("Chispa: ¡Hola! Soy un prototipo original. Escribe ayuda para ver mis funciones.")
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),12)
        }
    }
    private fun addLine(message:String){
        val v=TextView(this).apply { text=message; textSize=15f; setTextColor(Color.WHITE); setPadding(dp(8),dp(9),dp(8),dp(9)) }
        history.addView(v)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
    private fun send(){
        val msg=entry.text.toString().trim()
        if(msg.isEmpty())return
        entry.setText("")
        val a="Tú: $msg"
        val b="Chispa: ${Rules.respond(this,memory,msg)}"
        memory.addMessage(a);memory.addMessage(b)
        addLine(a);addLine(b)
    }
    private fun startOverlay(){
        if(!Settings.canDrawOverlays(this)){
            Toast.makeText(this,"Activa mostrar sobre otras apps y vuelve a tocar el botón",Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            return
        }
        try {
            val i=Intent(this,OverlayService::class.java)
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i)
        } catch(e:Exception){ Toast.makeText(this,"No se pudo iniciar: ${e.message}",Toast.LENGTH_LONG).show() }
    }
}
