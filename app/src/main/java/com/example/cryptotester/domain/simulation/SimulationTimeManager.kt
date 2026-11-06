package com.example.cryptotester.domain.simulation

import com.example.cryptotester.domain.model.Timeframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulationTimeManager @Inject constructor() {

    private val _currentTime = MutableStateFlow(0L)
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    fun initialize(startTimestamp: Long) {
        _currentTime.value = startTimestamp
    }

    fun advance(timeframe: Timeframe) {
        _currentTime.value += timeframe.durationMs
    }

    fun advance(durationMs: Long) {
        _currentTime.value += durationMs
    }

    fun hasReachedEnd(maxTimestamp: Long): Boolean {
        return _currentTime.value >= maxTimestamp
    }
}
