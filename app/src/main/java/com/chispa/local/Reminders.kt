package com.chispa.local
import android.app.*
import android.content.*
import android.os.SystemClock

/** Avisos aproximados que sobreviven a reinicios con ayuda de SQLite. */
object Reminders {
 const val CHANNEL="chispa_reminders"
 fun schedule(ctx:Context,id:Int,due:Long,note:String){
   val intent=Intent(ctx,ReminderReceiver::class.java).apply{
     putExtra("id",id);putExtra("note",note)
   }
   val pi=PendingIntent.getBroadcast(ctx,id,intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
      .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pi)
 }
 fun create(ctx:Context,minutes:Int,note:String):String{
   require(minutes in 1..10080){"Admito de 1 minuto a 7 días."}
   val due=System.currentTimeMillis()+minutes*60000L
   val id=(SystemClock.elapsedRealtime() and 0x7fffffff).toInt()
   LocalMemory(ctx).use{it.saveReminder(id,due,note)}
   schedule(ctx,id,due,note)
   return "Recordatorio guardado. Android puede retrasar ligeramente la alerta."
 }
 fun notify(ctx:Context,id:Int,note:String){
   val manager=ctx.getSystemService(NotificationManager::class.java)
   manager.createNotificationChannel(NotificationChannel(CHANNEL,
      "Recordatorios de Chispa",NotificationManager.IMPORTANCE_HIGH))
   val n=Notification.Builder(ctx,CHANNEL)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("Chispa te recuerda").setContentText(note)
      .setStyle(Notification.BigTextStyle().bigText(note))
      .setAutoCancel(true).build()
   manager.notify(id,n)
   LocalMemory(ctx).use{it.markReminderDone(id)}
 }
}
class ReminderReceiver:BroadcastReceiver(){
 override fun onReceive(ctx:Context,intent:Intent){
   val id=intent.getIntExtra("id",-1)
   val note=intent.getStringExtra("note") ?:return
   if(id>=0)Reminders.notify(ctx,id,note)
 }
}
class RestartReceiver:BroadcastReceiver(){
 override fun onReceive(ctx:Context,intent:Intent){
  if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return
  LocalMemory(ctx).use{db->
   for((id,due,note) in db.pendingReminders()){
    Reminders.schedule(ctx,id,maxOf(due,System.currentTimeMillis()+60000L),note)
   }
  }
 }
}
