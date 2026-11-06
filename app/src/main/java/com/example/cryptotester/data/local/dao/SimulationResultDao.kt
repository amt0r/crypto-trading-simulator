package com.example.cryptotester.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cryptotester.data.local.entity.SimulationResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SimulationResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: SimulationResultEntity)

    @Query("SELECT * FROM simulation_results ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SimulationResultEntity>>

    @Query("DELETE FROM simulation_results")
    suspend fun deleteAll()
}
