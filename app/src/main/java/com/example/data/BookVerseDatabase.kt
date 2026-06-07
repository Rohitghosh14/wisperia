package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Book::class,
        Bookmark::class,
        Highlight::class,
        StickyNote::class,
        ActivityLog::class,
        HubItem::class,
        UserGamification::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BookVerseDatabase : RoomDatabase() {
    abstract fun dao(): BookVerseDao

    companion object {
        @Volatile
        private var INSTANCE: BookVerseDatabase? = null

        fun getDatabase(context: Context): BookVerseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookVerseDatabase::class.java,
                    "bookverse_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
