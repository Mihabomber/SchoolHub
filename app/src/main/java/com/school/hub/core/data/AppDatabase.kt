package com.school.hub.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cheatSheetDao(): CheatSheetDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun gradeDao(): GradeDao

    companion object {
        /** 2 -> 3: оценки получают uuid/updatedAt/dirty и уходят в общий обмен класса. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `grades_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subject` TEXT NOT NULL, `value` INTEGER NOT NULL, `weight` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, `uuid` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `originDevice` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `grades_new` (`id`, `subject`, `value`, `weight`, `date`, `uuid`, `updatedAt`, `deleted`, `dirty`, `originDevice`) " +
                        "SELECT `id`, `subject`, `value`, `weight`, `date`, 'grade-' || `id`, `date` * 86400000, 0, 1, '' FROM `grades`"
                )
                db.execSQL("DROP TABLE `grades`")
                db.execSQL("ALTER TABLE `grades_new` RENAME TO `grades`")
            }
        }

        /**
         * Данные пользователя не удаляются при обновлении.
         * Разрушительная миграция разрешена только со старой схемы 1 (для неё нет пути миграции,
         * так было и раньше). Для всех новых версий схемы нужно добавлять Migration.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schoolhub.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}
