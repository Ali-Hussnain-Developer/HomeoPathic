package com.example.todolist.data
import androidx.room.Room
import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.todolist.model.Task

@Database(entities = [Task::class], version = 1)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_db"
                ).build().also { instance = it }
            }
        }
    }
}
