package com.example.cryptotester.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptotester.domain.model.SimulationResult
import com.example.cryptotester.domain.repository.SimulationResultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val simulationResultRepository: SimulationResultRepository
) : ViewModel() {

    val results: Flow<List<SimulationResult>> = simulationResultRepository.getAllResults()

    fun onClearAllStats() {
        viewModelScope.launch {
            simulationResultRepository.clearAllResults()
        }
    }
}
