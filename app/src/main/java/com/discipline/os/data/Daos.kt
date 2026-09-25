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

@Dao
interface RoadmapDao {
    @Query("SELECT * FROM roadmaps ORDER BY isPinned DESC, updatedAt DESC, id ASC")
    fun getAllRoadmaps(): Flow<List<Roadmap>>

    @Query("SELECT * FROM roadmaps ORDER BY isPinned DESC, updatedAt DESC, id ASC")
    suspend fun getAllRoadmapsSync(): List<Roadmap>

    @Query("SELECT * FROM roadmaps WHERE id = :id LIMIT 1")
    suspend fun getRoadmapById(id: Long): Roadmap?

    @Query("SELECT * FROM roadmap_nodes WHERE roadmapId = :roadmapId ORDER BY stepOrder ASC, id ASC")
    fun getNodesForRoadmap(roadmapId: Long): Flow<List<RoadmapNode>>

    @Query("SELECT * FROM roadmap_nodes WHERE roadmapId = :roadmapId ORDER BY stepOrder ASC, id ASC")
    suspend fun getNodesForRoadmapSync(roadmapId: Long): List<RoadmapNode>

    @Query("SELECT * FROM roadmap_nodes ORDER BY roadmapId ASC, stepOrder ASC, id ASC")
    fun getAllNodes(): Flow<List<RoadmapNode>>

    @Query("SELECT * FROM roadmap_nodes ORDER BY roadmapId ASC, stepOrder ASC, id ASC")
    suspend fun getAllNodesSync(): List<RoadmapNode>

    @Query("SELECT * FROM roadmap_nodes WHERE id = :id LIMIT 1")
    suspend fun getNodeById(id: Long): RoadmapNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoadmap(roadmap: Roadmap): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<RoadmapNode>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: RoadmapNode): Long

    @Update
    suspend fun updateRoadmap(roadmap: Roadmap)

    @Update
    suspend fun updateNode(node: RoadmapNode)

    @Query("UPDATE roadmap_nodes SET isCompleted = :completed WHERE id = :id")
    suspend fun setNodeCompleted(id: Long, completed: Boolean)

    @Query("UPDATE roadmap_nodes SET isCurrent = 0 WHERE roadmapId = :roadmapId")
    suspend fun clearCurrentForRoadmap(roadmapId: Long)

    @Query("UPDATE roadmap_nodes SET isCurrent = 1 WHERE id = :nodeId")
    suspend fun setCurrentNode(nodeId: Long)

    @Query("UPDATE roadmap_nodes SET checklistJson = :json WHERE id = :id")
    suspend fun updateNodeChecklist(id: Long, json: String)

    @Query("UPDATE roadmaps SET currentLevel = :currentLevel, progressPercentage = :progress, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRoadmapProgress(id: Long, currentLevel: String, progress: Float, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteRoadmap(roadmap: Roadmap)

    @Delete
    suspend fun deleteNode(node: RoadmapNode)

    @Query("DELETE FROM roadmaps WHERE id = :id")
    suspend fun deleteRoadmapById(id: Long)

    @Query("DELETE FROM roadmap_nodes WHERE id = :id")
    suspend fun deleteNodeById(id: Long)

    @Query("DELETE FROM roadmaps")
    suspend fun clearAllRoadmaps()
}

