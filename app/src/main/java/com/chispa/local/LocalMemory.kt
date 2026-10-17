package com.chispa.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalMemory(ctx:Context):SQLiteOpenHelper(ctx,"chispa.db",null,1){
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL)")
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase, oldVersion:Int,newVersion:Int){}
    fun addMessage(text:String){ writableDatabase.execSQL("INSERT INTO messages(text) VALUES (?)",arrayOf(text)) }
    fun addNote(text:String){ writableDatabase.execSQL("INSERT INTO notes(text) VALUES (?)",arrayOf(text)) }
    fun messages():List<String>{
        val out=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT text FROM messages ORDER BY id DESC LIMIT 80",null).use { c ->
            while(c.moveToNext()) out.add(c.getString(0))
        }
        return out.reversed()
    }
    fun notes():List<String>{
        val out=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT text FROM notes ORDER BY id DESC LIMIT 15",null).use { c ->
            while(c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }
}
