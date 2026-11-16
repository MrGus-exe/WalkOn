package com.example.walkon.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.walkon.model.Walk

@Database(entities = [Walk::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun walkDao(): WalkDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        // Singleton: cria o banco uma única vez e reutiliza.
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "walkon.db"
                ).build().also { instance = it }
            }
    }
}
