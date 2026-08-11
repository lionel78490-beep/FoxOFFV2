package com.projectfox.foxoff.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Insert
    suspend fun insert(session: SleepSessionEntity): Long

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Query("SELECT * FROM sleep_sessions ORDER BY startedAt DESC")
    fun getAll(): Flow<List<SleepSessionEntity>>
}
