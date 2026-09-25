package com.school.hub.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.school.hub.feature.cheatsheets.data.CheatSheetDao
import com.school.hub.feature.cheatsheets.data.CheatSheetEntity

@Database(entities = [CheatSheetEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cheatSheetDao(): CheatSheetDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schoolhub.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
