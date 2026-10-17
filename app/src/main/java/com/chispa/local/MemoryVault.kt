package com.chispa.local
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Copia elegida por el usuario. Contiene datos personales: nunca subirla automáticamente. */
object MemoryVault {
 suspend fun backup(ctx:Context,uri:Uri):Int=withContext(Dispatchers.IO){
  LocalMemory(ctx).use{db->
   val messages=db.allMessages()
   val notes=db.notes()
   val json=JSONObject()
    .put("schema",1)
    .put("messages",JSONArray(messages))
    .put("notes",JSONArray(notes))
    .toString(2)
   requireNotNull(ctx.contentResolver.openOutputStream(uri,"w")).writer(Charsets.UTF_8).buffered().use{
    it.write(json)
   }
   messages.size+notes.size
  }
 }
 suspend fun restore(ctx:Context,uri:Uri):Int=withContext(Dispatchers.IO){
  val raw=requireNotNull(ctx.contentResolver.openInputStream(uri)).use{stream->
   stream.readNBytes(1024*1024+1)
  }
  require(raw.size<=1024*1024){"Respaldo demasiado grande (límite 1 MB)"}
  val data=JSONObject(String(raw,Charsets.UTF_8))
  require(data.optInt("schema")==1){"Formato de respaldo incompatible"}
  val m=data.getJSONArray("messages")
  val n=data.getJSONArray("notes")
  require(m.length()<=1500 && n.length()<=500){"Respaldo con demasiados registros"}
  LocalMemory(ctx).use{db->
   db.clearUserData()
   for(i in 0 until m.length())db.addMessage(m.getString(i).take(10000))
   for(i in 0 until n.length())db.addNote(n.getString(i).take(2000))
  }
  m.length()+n.length()
 }
}
