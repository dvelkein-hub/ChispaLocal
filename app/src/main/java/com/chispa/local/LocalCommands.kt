package com.chispa.local
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale
object LocalCommands{
 private val remind=Regex("(?i)^recu[eé]rdame en\\s+(\\d{1,5})\\s+minutos?\\s+(?:que\\s+)?(.+)$")
 private val arithmetic=Regex("^(-?\\d+(?:[.,]\\d+)?)\\s*([+*/x×-])\\s*(-?\\d+(?:[.,]\\d+)?)$")
 fun isCommand(s:String):Boolean{
  val t=s.trim().lowercase(Locale.ROOT).trim(' ','¿','?','.')
  return remind.matches(s.trim())||t.startsWith("recuerda que ")||
    t=="mis notas"||t=="que recuerdas"||t=="qué recuerdas"||
    t.startsWith("buscar nota ")||t=="hora"||t=="fecha"||
    t.contains("qué hora")||t.contains("bateria")||t.contains("batería")||
    t=="abre ajustes"||t=="abrir ajustes"||arithmetic.matches(t)
 }
 fun run(ctx:Context,db:LocalMemory,msg:String):String{
  val m=remind.matchEntire(msg.trim())
  if(m!=null)return try{Reminders.create(ctx,m.groupValues[1].toInt(),m.groupValues[2].take(240))}
    catch(e:Exception){"No se pudo programar el recordatorio: EX_MSG".replace("EX_MSG",e.message?:"error")}
  val t=msg.trim().lowercase(Locale.ROOT)
  if(t.startsWith("buscar nota ")){
   val notes=db.searchNotes(msg.trim().substring(12).take(90))
   return if(notes.isEmpty())"No encontré ninguna nota." else notes.joinToString("\n• ",prefix="• ")
  }
  if(t=="abre ajustes"||t=="abrir ajustes")return try{
   ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   "Abrí Ajustes de Android."
  }catch(e:Exception){"No pude abrir Ajustes."}
  return Rules.respond(ctx,db,msg)
 }
}
