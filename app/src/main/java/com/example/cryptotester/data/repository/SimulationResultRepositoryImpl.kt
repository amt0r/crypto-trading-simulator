package com.example.cryptotester.data.repository

import com.example.cryptotester.data.local.dao.SimulationResultDao
import com.example.cryptotester.data.mapper.toDomain
import com.example.cryptotester.data.mapper.toEntity
import com.example.cryptotester.domain.model.SimulationResult
import com.example.cryptotester.domain.repository.SimulationResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulationResultRepositoryImpl @Inject constructor(
    private val dao: SimulationResultDao
) : SimulationResultRepository {

    override suspend fun saveResult(result: SimulationResult) {
        dao.insert(result.toEntity())
    }

    override fun getAllResults(): Flow<List<SimulationResult>> {
        return dao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun clearAllResults() {
        dao.deleteAll()
    }
}
