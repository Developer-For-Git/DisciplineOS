package com.discipline.os.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY CASE WHEN scheduledTime IS NULL OR scheduledTime = '' THEN 1 ELSE 0 END, scheduledTime ASC, priority ASC, sortOrder ASC, id ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY CASE WHEN scheduledTime IS NULL OR scheduledTime = '' THEN 1 ELSE 0 END, scheduledTime ASC, priority ASC, sortOrder ASC, id ASC")
    suspend fun getAllTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE category = :category ORDER BY CASE WHEN scheduledTime IS NULL OR scheduledTime = '' THEN 1 ELSE 0 END, scheduledTime ASC, priority ASC, sortOrder ASC, id ASC")
    fun getTasksByCategory(category: String): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("UPDATE tasks SET subtasksJson = :subtasksJson WHERE id = :id")
    suspend fun updateSubtasks(id: Long, subtasksJson: String)

    @Query("UPDATE tasks SET isCompleted = 0")
    suspend fun resetAllTasks()

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()
}

@Dao
interface FuelDao {
    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC")
    fun getAllFuel(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY timestamp DESC")
    suspend fun getAllFuelSync(): List<FuelEntry>

    @Query("SELECT * FROM fuel_entries WHERE id = :id LIMIT 1")
    suspend fun getFuelById(id: Long): FuelEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFuel(fuel: FuelEntry): Long

    @Update
    suspend fun updateFuel(fuel: FuelEntry)

    @Delete
    suspend fun deleteFuel(fuel: FuelEntry)

    @Query("DELETE FROM fuel_entries WHERE id = :id")
    suspend fun deleteFuelById(id: Long)

    @Query("DELETE FROM fuel_entries")
    suspend fun clearAllFuel()
}

@Dao
interface DailyLogDao {
    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    fun getAllLogs(): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs ORDER BY date DESC")
    suspend fun getAllLogsSync(): List<DailyLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DailyLog)

    @Query("DELETE FROM daily_logs")
    suspend fun clearAllLogs()
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY isWatched ASC, createdAt DESC")
    fun getAllVideos(): Flow<List<VideoEntry>>

    @Query("SELECT * FROM videos ORDER BY isWatched ASC, createdAt DESC")
    suspend fun getAllVideosSync(): List<VideoEntry>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): VideoEntry?

    @Query("SELECT * FROM videos WHERE category = :category ORDER BY isWatched ASC, createdAt DESC")
    fun getVideosByCategory(category: String): Flow<List<VideoEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntry>)

    @Update
    suspend fun updateVideo(video: VideoEntry)

    @Query("UPDATE videos SET isWatched = :watched WHERE id = :id")
    suspend fun setVideoWatched(id: Long, watched: Boolean)

    @Delete
    suspend fun deleteVideo(video: VideoEntry)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Query("DELETE FROM videos")
    suspend fun clearAllVideos()
}
