package com.example.cryptotester.domain.simulation

import com.example.cryptotester.domain.model.CryptoSymbol
import com.example.cryptotester.domain.model.Portfolio
import com.example.cryptotester.domain.model.SimulationResult
import com.example.cryptotester.domain.model.SimulationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class SessionManager @Inject constructor(
    private val timeManager: SimulationTimeManager
) {
    private val _session = MutableStateFlow<SimulationSession?>(null)
    val session: StateFlow<SimulationSession?> = _session.asStateFlow()

    fun startSession(startBalance: Double, dbTimeRange: Pair<Long, Long>) {
        val minTime = dbTimeRange.first
        val maxTime = dbTimeRange.second
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val latestStart = maxTime - thirtyDaysMs

        val rawStart = if (latestStart > minTime) {
            Random.nextLong(minTime, latestStart)
        } else {
            minTime
        }
        val startTimestamp = (rawStart / (15 * 60 * 1000L)) * (15 * 60 * 1000L)

        val sessionMultiplier = Random.nextDouble(0.1, 10.0)

        val portfolio = Portfolio(
            usdtBalance = startBalance,
            holdings = CryptoSymbol.entries.associateWith { 0.0 }
        )

        val session = SimulationSession(
            startBalance = startBalance,
            startTimestamp = startTimestamp,
            endTimestamp = maxTime,
            sessionMultiplier = sessionMultiplier,
            portfolio = portfolio
        )

        _session.value = session
        timeManager.initialize(startTimestamp)
    }

    fun updatePortfolio(portfolio: Portfolio) {
        _session.value = _session.value?.copy(portfolio = portfolio)
    }

    fun finishSession(currentPrices: Map<CryptoSymbol, Double>): SimulationResult? {
        val currentSession = _session.value ?: return null
        val currentTime = timeManager.currentTime.value

        val finalBalance = currentSession.portfolio.totalValue(currentPrices)
        val daysElapsed = ((currentTime - currentSession.startTimestamp) / (24.0 * 60 * 60 * 1000)).toInt()
        val pnlPercent = if (currentSession.startBalance > 0) {
            ((finalBalance - currentSession.startBalance) / currentSession.startBalance) * 100.0
        } else {
            0.0
        }

        val result = SimulationResult(
            startBalance = currentSession.startBalance,
            finalBalance = finalBalance,
            daysElapsed = daysElapsed,
            pnlPercent = pnlPercent,
            timestamp = System.currentTimeMillis(),
            startDate = currentSession.startTimestamp,
            endDate = currentTime
        )

        _session.value = null
        return result
    }

    fun isActive(): Boolean = _session.value != null
}
