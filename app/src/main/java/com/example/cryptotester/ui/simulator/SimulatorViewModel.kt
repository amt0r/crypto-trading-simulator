package com.example.cryptotester.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.domain.model.CryptoSymbol
import com.example.cryptotester.domain.model.OrderType
import com.example.cryptotester.domain.model.PendingOrder
import com.example.cryptotester.domain.model.SimulationResult
import com.example.cryptotester.domain.model.Timeframe
import com.example.cryptotester.domain.model.TradeOrder
import com.example.cryptotester.domain.model.TradeSide
import com.example.cryptotester.domain.repository.CandleRepository
import com.example.cryptotester.domain.repository.SimulationResultRepository
import com.example.cryptotester.domain.simulation.PendingOrderManager
import com.example.cryptotester.domain.simulation.SessionManager
import com.example.cryptotester.domain.simulation.SimulationTimeManager
import com.example.cryptotester.domain.usecase.CalculateRsiUseCase
import com.example.cryptotester.domain.usecase.ExecuteTradeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimulatorUiState(
    val selectedSymbol: CryptoSymbol = CryptoSymbol.BTC,
    val selectedTimeframe: Timeframe = Timeframe.H1,
    val selectedOrderType: OrderType = OrderType.MARKET,
    val limitPriceInput: String = "",
    val activationPriceInput: String = "",
    val trailingPercentInput: String = "2.0",
    val sliderPosition: Float = 0.5f,
    val candles: List<Candle> = emptyList(),
    val obfuscatedCandles: List<Candle> = emptyList(),
    val rsiValues: List<Double> = emptyList(),
    val usdtBalance: Double = 0.0,
    val cryptoBalance: Double = 0.0,
    val sessionMultiplier: Double = 1.0,
    val currentPrice: Double = 0.0,
    val obfuscatedPrice: Double = 0.0,
    val canSell: Boolean = false,
    val canBuy: Boolean = false,
    val showResult: Boolean = false,
    val simulationResult: SimulationResult? = null,
    val tradeMessage: String? = null,
    val isEndReached: Boolean = false,
    val sessionTimeFormatted: String = "0 h 0 min",
    val pendingOrders: List<PendingOrder> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SimulatorViewModel @Inject constructor(
    private val candleRepository: CandleRepository,
    private val simulationResultRepository: SimulationResultRepository,
    private val sessionManager: SessionManager,
    private val timeManager: SimulationTimeManager,
    private val pendingOrderManager: PendingOrderManager,
    private val calculateRsiUseCase: CalculateRsiUseCase,
    private val executeTradeUseCase: ExecuteTradeUseCase
) : ViewModel() {

    private val _selectedSymbol = MutableStateFlow(CryptoSymbol.BTC)
    private val _selectedTimeframe = MutableStateFlow(Timeframe.H1)
    private val _selectedOrderType = MutableStateFlow(OrderType.MARKET)
    private val _limitPriceInput = MutableStateFlow("")
    private val _activationPriceInput = MutableStateFlow("")
    private val _trailingPercentInput = MutableStateFlow("2.0")
    private val _sliderPosition = MutableStateFlow(0.5f)
    private val _tradeMessage = MutableStateFlow<String?>(null)
    private val _showResult = MutableStateFlow(false)
    private val _simulationResult = MutableStateFlow<SimulationResult?>(null)
    private val _isEndReached = MutableStateFlow(false)

    // Reactive candle loading: keeps initial 200 candles and accumulates history up to 1000 (5x)
    private val candles: StateFlow<List<Candle>> = combine(
        _selectedSymbol,
        _selectedTimeframe,
        timeManager.currentTime,
        sessionManager.session
    ) { symbol, timeframe, time, session ->
        val startTs = session?.startTimestamp ?: time
        val elapsedCandles = if (timeframe.durationMs > 0) {
            ((time - startTs) / timeframe.durationMs).coerceAtLeast(0L)
        } else 0L
        val dynamicLimit = (200 + elapsedCandles.toInt()).coerceIn(200, 1000)
        Triple(symbol, timeframe, time) to dynamicLimit
    }.flatMapLatest { (params, limit) ->
        val (symbol, timeframe, time) = params
        candleRepository.getCandles(symbol, timeframe, time, limit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // RSI calculated on background thread using original prices
    private val rsiValues: StateFlow<List<Double>> = candles
        .map { candleList ->
            if (candleList.size > 14) calculateRsiUseCase(candleList) else emptyList()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<SimulatorUiState> = combine(
        combine(
            _selectedSymbol,
            _selectedTimeframe,
            _selectedOrderType,
            _limitPriceInput,
            _activationPriceInput
        ) { symbol, timeframe, orderType, limitPrice, activationPrice ->
            Triple(symbol, timeframe, orderType) to (limitPrice to activationPrice)
        },
        combine(
            _trailingPercentInput,
            _sliderPosition,
            candles,
            rsiValues,
            pendingOrderManager.pendingOrders
        ) { trailingPct, slider, candleList, rsi, pending ->
            Pair(trailingPct, slider) to Triple(candleList, rsi, pending)
        },
        combine(
            sessionManager.session,
            timeManager.currentTime,
            _tradeMessage,
            _showResult,
            _simulationResult
        ) { session, currTime, tradeMsg, showRes, simResult ->
            Triple(session, currTime, tradeMsg) to Pair(showRes, simResult)
        },
        _isEndReached
    ) { (symTfOrder, limitAct), (trailingSlider, candleRsiPending), (sessionTimeMsg, showSimRes), endReached ->
        val (symbol, timeframe, orderType) = symTfOrder
        val (limitPrice, activationPrice) = limitAct
        val (trailingPct, slider) = trailingSlider
        val (candleList, rsi, pendingOrders) = candleRsiPending
        val (session, currTime, tradeMsg) = sessionTimeMsg
        val (showRes, simResult) = showSimRes

        val multiplier = session?.sessionMultiplier ?: 1.0
        val portfolio = session?.portfolio
        val usdtBalance = portfolio?.usdtBalance ?: 0.0
        val cryptoBalance = portfolio?.getHolding(symbol) ?: 0.0

        val latestPrice = candleList.lastOrNull()?.close ?: 0.0
        val obfuscatedPrice = latestPrice * multiplier

        // Obfuscate candle prices for display
        val obfuscatedCandles = candleList.map { candle ->
            candle.copy(
                open = candle.open * multiplier,
                high = candle.high * multiplier,
                low = candle.low * multiplier,
                close = candle.close * multiplier
            )
        }

        // Format simulation elapsed time
        val elapsedMs = if (session != null) (currTime - session.startTimestamp).coerceAtLeast(0L) else 0L
        val sessionTimeFormatted = formatSimulationTime(elapsedMs)

        SimulatorUiState(
            selectedSymbol = symbol,
            selectedTimeframe = timeframe,
            selectedOrderType = orderType,
            limitPriceInput = limitPrice,
            activationPriceInput = activationPrice,
            trailingPercentInput = trailingPct,
            sliderPosition = slider,
            candles = candleList,
            obfuscatedCandles = obfuscatedCandles,
            rsiValues = rsi,
            usdtBalance = usdtBalance,
            cryptoBalance = cryptoBalance,
            sessionMultiplier = multiplier,
            currentPrice = latestPrice,
            obfuscatedPrice = obfuscatedPrice,
            canSell = cryptoBalance > 0.0,
            canBuy = usdtBalance > 0.0,
            showResult = showRes,
            simulationResult = simResult,
            tradeMessage = tradeMsg,
            isEndReached = endReached,
            sessionTimeFormatted = sessionTimeFormatted,
            pendingOrders = pendingOrders
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SimulatorUiState())

    fun onSymbolSelected(symbol: CryptoSymbol) {
        _selectedSymbol.value = symbol
    }

    fun onTimeframeSelected(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun onOrderTypeSelected(type: OrderType) {
        _selectedOrderType.value = type
        val mult = sessionManager.session.value?.sessionMultiplier ?: 1.0
        val latest = candles.value.lastOrNull()?.close ?: 0.0
        val defaultPrice = "%.2f".format(latest * mult)

        if (type == OrderType.LIMIT && _limitPriceInput.value.isEmpty()) {
            _limitPriceInput.value = defaultPrice
        }
        if (type == OrderType.TRAILING_STOP && _activationPriceInput.value.isEmpty()) {
            _activationPriceInput.value = defaultPrice
        }
    }

    fun onLimitPriceChanged(value: String) {
        _limitPriceInput.value = value
    }

    fun onActivationPriceChanged(value: String) {
        _activationPriceInput.value = value
    }

    fun onTrailingPercentChanged(value: String) {
        _trailingPercentInput.value = value
    }

    fun onPriceSelectedFromChart(price: Double) {
        val formatted = "%.2f".format(price)
        _limitPriceInput.value = formatted
        _activationPriceInput.value = formatted
    }

    fun onSliderChanged(value: Float) {
        _sliderPosition.value = value
    }

    fun onAdvance15m() = advanceTime(15 * 60 * 1000L)
    fun onAdvance1h() = advanceTime(60 * 60 * 1000L)
    fun onAdvance1d() = advanceTime(24 * 60 * 60 * 1000L)

    private fun advanceTime(durationMs: Long) {
        viewModelScope.launch {
            val session = sessionManager.session.value ?: return@launch
            val previousTime = timeManager.currentTime.value
            val targetTime = previousTime + durationMs

            // Advance time in simulation manager
            timeManager.advance(durationMs)

            // Check if any pending orders should trigger
            val passedCandles = candleRepository.getCandles15mBetween(
                _selectedSymbol.value,
                previousTime,
                targetTime
            )
            val triggered = pendingOrderManager.processCandles(passedCandles)
            for ((order, execPrice) in triggered) {
                val currentPortfolio = sessionManager.session.value?.portfolio ?: continue
                val tradeOrder = TradeOrder(order.symbol, order.side, order.percentage)
                val result = executeTradeUseCase(tradeOrder, currentPortfolio, execPrice)
                sessionManager.updatePortfolio(result.updatedPortfolio)

                val displayExecPrice = execPrice * session.sessionMultiplier
                _tradeMessage.value = "${order.orderType.label} ${order.side} executed @ %.2f".format(displayExecPrice)
            }

            if (timeManager.hasReachedEnd(session.endTimestamp)) {
                _isEndReached.value = true
                onFinish()
            }
        }
    }

    fun onBuy() {
        viewModelScope.launch {
            val session = sessionManager.session.value ?: return@launch
            val symbol = _selectedSymbol.value
            val percentage = _sliderPosition.value.toDouble()
            if (percentage <= 0.0) return@launch

            val currentTime = timeManager.currentTime.value
            val latest15m = candleRepository.getLatest15mCandle(symbol, currentTime)
                ?: return@launch
            val currentPrice = latest15m.close

            when (_selectedOrderType.value) {
                OrderType.MARKET -> {
                    val order = TradeOrder(symbol, TradeSide.BUY, percentage)
                    val result = executeTradeUseCase(order, session.portfolio, currentPrice)
                    sessionManager.updatePortfolio(result.updatedPortfolio)

                    val obfuscatedExecPrice = currentPrice * session.sessionMultiplier
                    _tradeMessage.value = "Bought %.6f %s @ %.2f".format(
                        result.amount, symbol.displayName, obfuscatedExecPrice
                    )
                }

                OrderType.LIMIT -> {
                    val userPrice = _limitPriceInput.value.toDoubleOrNull() ?: (currentPrice * session.sessionMultiplier)
                    val realLimitPrice = userPrice / session.sessionMultiplier
                    val order = PendingOrder(
                        symbol = symbol,
                        side = TradeSide.BUY,
                        orderType = OrderType.LIMIT,
                        percentage = percentage,
                        limitPrice = realLimitPrice,
                        initialPrice = currentPrice,
                        displayTargetPrice = userPrice
                    )
                    pendingOrderManager.addOrder(order)
                    _tradeMessage.value = "Limit BUY order placed @ %.2f".format(userPrice)
                }

                OrderType.TRAILING_STOP -> {
                    val trailingPct = _trailingPercentInput.value.toDoubleOrNull() ?: 2.0
                    val userActPrice = _activationPriceInput.value.toDoubleOrNull()
                    val realActPrice = if (userActPrice != null && userActPrice > 0.0) {
                        userActPrice / session.sessionMultiplier
                    } else null

                    val isImmediatelyActive = realActPrice == null || currentPrice <= realActPrice

                    val order = PendingOrder(
                        symbol = symbol,
                        side = TradeSide.BUY,
                        orderType = OrderType.TRAILING_STOP,
                        percentage = percentage,
                        trailingPercent = trailingPct,
                        activationPrice = realActPrice,
                        displayActivationPrice = userActPrice,
                        isActivated = isImmediatelyActive,
                        initialPrice = currentPrice,
                        peakPrice = currentPrice,
                        displayTargetPrice = userActPrice ?: (currentPrice * session.sessionMultiplier)
                    )
                    pendingOrderManager.addOrder(order)
                    val actMsg = if (userActPrice != null) " (Act: %.2f)".format(userActPrice) else ""
                    _tradeMessage.value = "Trailing Stop BUY (%.1f%%)%s placed".format(trailingPct, actMsg)
                }
            }

            launch {
                kotlinx.coroutines.delay(3500)
                _tradeMessage.value = null
            }
        }
    }

    fun onSell() {
        viewModelScope.launch {
            val session = sessionManager.session.value ?: return@launch
            val symbol = _selectedSymbol.value
            val percentage = _sliderPosition.value.toDouble()
            if (percentage <= 0.0) return@launch

            val currentTime = timeManager.currentTime.value
            val latest15m = candleRepository.getLatest15mCandle(symbol, currentTime)
                ?: return@launch
            val currentPrice = latest15m.close

            when (_selectedOrderType.value) {
                OrderType.MARKET -> {
                    val order = TradeOrder(symbol, TradeSide.SELL, percentage)
                    val result = executeTradeUseCase(order, session.portfolio, currentPrice)
                    sessionManager.updatePortfolio(result.updatedPortfolio)

                    val obfuscatedExecPrice = currentPrice * session.sessionMultiplier
                    _tradeMessage.value = "Sold %.6f %s @ %.2f".format(
                        result.amount, symbol.displayName, obfuscatedExecPrice
                    )
                }

                OrderType.LIMIT -> {
                    val userPrice = _limitPriceInput.value.toDoubleOrNull() ?: (currentPrice * session.sessionMultiplier)
                    val realLimitPrice = userPrice / session.sessionMultiplier
                    val order = PendingOrder(
                        symbol = symbol,
                        side = TradeSide.SELL,
                        orderType = OrderType.LIMIT,
                        percentage = percentage,
                        limitPrice = realLimitPrice,
                        initialPrice = currentPrice,
                        displayTargetPrice = userPrice
                    )
                    pendingOrderManager.addOrder(order)
                    _tradeMessage.value = "Limit SELL order placed @ %.2f".format(userPrice)
                }

                OrderType.TRAILING_STOP -> {
                    val trailingPct = _trailingPercentInput.value.toDoubleOrNull() ?: 2.0
                    val userActPrice = _activationPriceInput.value.toDoubleOrNull()
                    val realActPrice = if (userActPrice != null && userActPrice > 0.0) {
                        userActPrice / session.sessionMultiplier
                    } else null

                    val isImmediatelyActive = realActPrice == null || currentPrice >= realActPrice

                    val order = PendingOrder(
                        symbol = symbol,
                        side = TradeSide.SELL,
                        orderType = OrderType.TRAILING_STOP,
                        percentage = percentage,
                        trailingPercent = trailingPct,
                        activationPrice = realActPrice,
                        displayActivationPrice = userActPrice,
                        isActivated = isImmediatelyActive,
                        initialPrice = currentPrice,
                        peakPrice = currentPrice,
                        displayTargetPrice = userActPrice ?: (currentPrice * session.sessionMultiplier)
                    )
                    pendingOrderManager.addOrder(order)
                    val actMsg = if (userActPrice != null) " (Act: %.2f)".format(userActPrice) else ""
                    _tradeMessage.value = "Trailing Stop SELL (%.1f%%)%s placed".format(trailingPct, actMsg)
                }
            }

            launch {
                kotlinx.coroutines.delay(3500)
                _tradeMessage.value = null
            }
        }
    }

    fun onCancelPendingOrder(orderId: String) {
        pendingOrderManager.removeOrder(orderId)
    }

    fun onFinish() {
        viewModelScope.launch {
            val session = sessionManager.session.value ?: return@launch
            val currentTime = timeManager.currentTime.value

            val currentPrices = CryptoSymbol.entries.associate { symbol ->
                val candle = candleRepository.getLatest15mCandle(symbol, currentTime)
                symbol to (candle?.close ?: 0.0)
            }

            pendingOrderManager.clear()
            val result = sessionManager.finishSession(currentPrices) ?: return@launch
            simulationResultRepository.saveResult(result)

            _simulationResult.value = result
            _showResult.value = true
        }
    }

    fun onResultDismissed() {
        _showResult.value = false
        _simulationResult.value = null
    }

    fun clearTradeMessage() {
        _tradeMessage.value = null
    }

    private fun formatSimulationTime(elapsedMs: Long): String {
        val totalMinutes = elapsedMs / (60 * 1000L)
        val minutes = (totalMinutes % 60).toInt()
        val totalHours = totalMinutes / 60
        val hours = (totalHours % 24).toInt()
        val totalDays = totalHours / 24
        val days = (totalDays % 30).toInt()
        val months = (totalDays / 30).toInt()

        val parts = mutableListOf<String>()
        if (months > 0) parts.add("$months m")
        if (days > 0) parts.add("$days d")
        parts.add("$hours h")
        parts.add("$minutes min")

        return parts.joinToString(" ")
    }
}
