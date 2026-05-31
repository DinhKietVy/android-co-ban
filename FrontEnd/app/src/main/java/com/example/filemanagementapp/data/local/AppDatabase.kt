package com.example.filemanagementapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.filemanagementapp.data.local.ai.AiAnalysisCacheDao
import com.example.filemanagementapp.data.local.ai.AiAnalysisCacheEntity
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheDao
import com.example.filemanagementapp.data.local.explorer.DirectoryCacheEntity
import com.example.filemanagementapp.data.local.favorite.FavoriteItemDao
import com.example.filemanagementapp.data.local.favorite.FavoriteItemEntity

@Database(
    entities = [AiAnalysisCacheEntity::class, FavoriteItemEntity::class, DirectoryCacheEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aiAnalysisCacheDao(): AiAnalysisCacheDao
    abstract fun favoriteItemDao(): FavoriteItemDao
    abstract fun directoryCacheDao(): DirectoryCacheDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "file_management_app.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
