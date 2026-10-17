package com.chispa.local
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Copiar/recuperar un GGUF elegido por el usuario, sin permisos generales de archivos. */
object ModelVault {
 const val NAME = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
 const val MIN_BYTES = 900_000_000L
 fun file(ctx: Context) = File(requireNotNull(ctx.getExternalFilesDir(null)), NAME)
 fun isPresent(ctx: Context) = file(ctx).isFile && file(ctx).length() >= MIN_BYTES
 private fun hex(md:MessageDigest) = md.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
 suspend fun backup(ctx:Context,uri:Uri,progress:(Long)->Unit):String = withContext(Dispatchers.IO) {
   val source=file(ctx)
   require(isPresent(ctx)) {"No hay modelo completo para respaldar."}
   val md=MessageDigest.getInstance("SHA-256")
   var count=0L
   source.inputStream().buffered().use { input ->
     requireNotNull(ctx.contentResolver.openOutputStream(uri,"w")).use { output ->
       val buffer=ByteArray(256*1024)
       while(true){
         val n=input.read(buffer)
         if(n<0) break
         output.write(buffer,0,n);md.update(buffer,0,n);count+=n
         if(count%(8L*1024*1024)<buffer.size) progress(count)
       }
       output.flush()
     }
   }
   check(count==source.length()) {"Respaldo incompleto."}
   hex(md)
 }
 suspend fun restore(ctx:Context,uri:Uri,progress:(Long)->Unit):String = withContext(Dispatchers.IO) {
   val target=file(ctx)
   val partial=File(target.parentFile,"Qwen2.5-1.5B-Instruct-Q4_K_M.gguf.partial")
   var count=0L
   val md=MessageDigest.getInstance("SHA-256")
   try {
     requireNotNull(ctx.contentResolver.openInputStream(uri)).use { input ->
       partial.outputStream().buffered().use { output ->
         val buffer=ByteArray(256*1024)
         while(true){
           val n=input.read(buffer)
           if(n<0)break
           output.write(buffer,0,n);md.update(buffer,0,n);count+=n
           if(count%(8L*1024*1024)<buffer.size)progress(count)
         }
         output.flush()
       }
     }
     require(count>=MIN_BYTES) {"GGUF incompleto o demasiado pequeño."}
     if(!partial.renameTo(target)){
       check(!target.exists()||target.delete()){"No se pudo reemplazar el modelo previo."}
       check(partial.renameTo(target)){"No se pudo instalar respaldo."}
     }
     hex(md)
   } finally {if(partial.exists())partial.delete()}
 }
}
