package com.school.hub.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.school.hub.feature.cheatsheets.data.CheatSheetDao
import com.school.hub.feature.cheatsheets.data.CheatSheetEntity
import com.school.hub.feature.grades.GradeDao
import com.school.hub.feature.grades.GradeEntity
import com.school.hub.feature.homework.data.HomeworkDao
import com.school.hub.feature.homework.data.HomeworkEntity
import com.school.hub.feature.schedule.data.BellEntity
import com.school.hub.feature.schedule.data.LessonEntity
import com.school.hub.feature.schedule.data.ScheduleDao

@Database(
    entities = [CheatSheetEntity::class, LessonEntity::class, BellEntity::class, HomeworkEntity::class, GradeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cheatSheetDao(): CheatSheetDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun gradeDao(): GradeDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schoolhub.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
