package com.example.cryptotester.domain.repository

import com.example.cryptotester.domain.model.SimulationResult
import kotlinx.coroutines.flow.Flow

interface SimulationResultRepository {
    suspend fun saveResult(result: SimulationResult)
    fun getAllResults(): Flow<List<SimulationResult>>
    suspend fun clearAllResults()
}
