package com.found404.sidelink.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NotificationEntity::class], version = 1, exportSchema = false)
abstract class WearDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: WearDatabase? = null

        fun getDatabase(context: Context): WearDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WearDatabase::class.java,
                    "wear_database"
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
