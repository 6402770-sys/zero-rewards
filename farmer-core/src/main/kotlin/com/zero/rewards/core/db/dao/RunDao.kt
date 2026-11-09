package com.zero.rewards.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zero.rewards.core.db.entity.RunEntity
import com.zero.rewards.core.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity): Long

    @Update
    suspend fun updateRun(run: RunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("SELECT * FROM runs WHERE accountId = :accountId ORDER BY startedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(accountId: String, limit: Int = 20): Flow<List<RunEntity>>

    @Query("SELECT * FROM tasks WHERE runId = :runId ORDER BY startedAtEpochMs ASC")
    suspend fun tasksForRun(runId: Long): List<TaskEntity>
}
