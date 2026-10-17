package com.chispa.local

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Motor provisional determinista. No es una IA conversacional. */
object Rules {
    fun respond(ctx:Context,db:LocalMemory,text:String):String {
        val t=text.trim().lowercase(Locale.ROOT).trim(' ','?','¿','.')
        val note=Regex("(?i)^recuerda que\\s+(.+)").find(text)
        if(note!=null) {
            db.addNote(note.groupValues[1])
            return "Lo he guardado en tu teléfono."
        }
        if(t=="mis notas" || t=="qué recuerdas" || t=="que recuerdas"){
            val n=db.notes()
            return if(n.isEmpty()) "Aún no tengo notas." else n.joinToString("\\n")
        }
        if(t.contains("batería")||t.contains("bateria")){
            val bm=ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return "Batería: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%."
        }
        if(t=="hora"||t.contains("qué hora")||t=="fecha"){
            return SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("es","EC")).format(Date())
        }
        if(t=="ayuda")return "Puedo conversar con reglas simples, calcular, consultar batería y hora, y guardar notas con 'recuerda que...'. La IA local y los recordatorios llegarán en versiones posteriores."
        val match=Regex("^(-?\\d+(?:[.,]\\d+)?)\\s*([+*/x×-])\\s*(-?\\d+(?:[.,]\\d+)?)$").matchEntire(t)
        if(match!=null){
            val a=match.groupValues[1].replace(',','.').toDouble()
            val b=match.groupValues[3].replace(',','.').toDouble()
            if(match.groupValues[2]=="/" && b==0.0) return "No se puede dividir entre cero."
            val r=when(match.groupValues[2]){"+"->a+b;"-"->a-b;"*" ,"x","×"->a*b;else->a/b}
            return if(r.isFinite()) "Resultado: ${if(r==r.toLong().toDouble())r.toLong().toString() else r.toString()}" else "No pude calcularlo."
        }
        if(t in listOf("hola","hey","buenas"))return "¡Hola! Soy Chispa y vivo en tu Android."
        return "Todavía no tengo un modelo de lenguaje instalado. Prueba 'ayuda'."
    }
}
