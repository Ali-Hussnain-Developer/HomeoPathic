package com.example.todolist.data
import androidx.room.Room
import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.todolist.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

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
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Prepopulate database on first creation
                            CoroutineScope(Dispatchers.IO).launch {
                                prepopulateDatabase(context.applicationContext)
                            }
                        }
                    })
                    .build().also { instance = it }
            }
        }

        private suspend fun prepopulateDatabase(context: Context) {
            try {
                val dao = getDatabase(context).taskDao()

                // Check if database is already populated
                if (dao.getTaskCount() > 0) {
                    return
                }

                // Read JSON file from assets folder
                val inputStream = context.assets.open("medical_data.json")
                val reader = InputStreamReader(inputStream)

                // Parse JSON using Gson
                val gson = Gson()
                val taskType = object : TypeToken<List<TaskJson>>() {}.type
                val taskJsonList: List<TaskJson> = gson.fromJson(reader, taskType)

                // Convert to Task objects
                val tasks = taskJsonList.map { taskJson ->
                    Task(
                        title = taskJson.title,
                        description = taskJson.description
                    )
                }

                reader.close()
                inputStream.close()

                // Insert all tasks into database
                if (tasks.isNotEmpty()) {
                    dao.insertAll(tasks)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Helper data class for JSON parsing
        private data class TaskJson(
            val title: String,
            val description: String
        )
    }
}