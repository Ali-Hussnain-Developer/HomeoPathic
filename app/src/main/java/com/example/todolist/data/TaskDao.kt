package com.example.todolist.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.todolist.model.Task
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): Task

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasks(): LiveData<List<Task>>

    @Insert
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Insert
    suspend fun insertAll(tasks: List<Task>)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    suspend fun getAllTasksSync(): List<Task>
}

