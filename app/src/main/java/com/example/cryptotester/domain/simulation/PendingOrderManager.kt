package com.example.cryptotester.domain.simulation

import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.domain.model.OrderType
import com.example.cryptotester.domain.model.PendingOrder
import com.example.cryptotester.domain.model.TradeSide
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingOrderManager @Inject constructor() {

    private val _pendingOrders = MutableStateFlow<List<PendingOrder>>(emptyList())
    val pendingOrders: StateFlow<List<PendingOrder>> = _pendingOrders.asStateFlow()

    fun addOrder(order: PendingOrder) {
        _pendingOrders.value = _pendingOrders.value + order
    }

    fun removeOrder(orderId: String) {
        _pendingOrders.value = _pendingOrders.value.filter { it.id != orderId }
    }

    fun clear() {
        _pendingOrders.value = emptyList()
    }

    /**
     * Checks all pending orders against the given candles (in chronological order).
     * Returns a list of triggered orders along with their real execution price.
     */
    fun processCandles(candles: List<Candle>): List<Pair<PendingOrder, Double>> {
        if (_pendingOrders.value.isEmpty() || candles.isEmpty()) return emptyList()

        val triggered = mutableListOf<Pair<PendingOrder, Double>>()
        val currentOrders = _pendingOrders.value.toMutableList()

        for (candle in candles) {
            val iterator = currentOrders.listIterator()
            while (iterator.hasNext()) {
                val order = iterator.next()
                if (order.symbol != candle.symbol) continue

                when (order.orderType) {
                    OrderType.MARKET -> {
                        triggered.add(order to candle.close)
                        iterator.remove()
                    }

                    OrderType.LIMIT -> {
                        val limitPrice = order.limitPrice ?: continue
                        if (order.side == TradeSide.BUY) {
                            if (candle.low <= limitPrice) {
                                val execPrice = if (candle.open <= limitPrice) candle.open else limitPrice
                                triggered.add(order to execPrice)
                                iterator.remove()
                            }
                        } else {
                            if (candle.high >= limitPrice) {
                                val execPrice = if (candle.open >= limitPrice) candle.open else limitPrice
                                triggered.add(order to execPrice)
                                iterator.remove()
                            }
                        }
                    }

                    OrderType.TRAILING_STOP -> {
                        val trailingPct = order.trailingPercent ?: 2.0
                        if (order.side == TradeSide.SELL) {
                            val isNowActive = order.isActivated || (order.activationPrice == null) || (candle.high >= order.activationPrice)
                            if (!isNowActive) {
                                // Still waiting for activation price
                                continue
                            }

                            val basePrice = if (order.isActivated) (order.peakPrice ?: candle.high) else (order.activationPrice ?: candle.high)
                            val peak = maxOf(basePrice, candle.high)
                            val stopPrice = peak * (1.0 - trailingPct / 100.0)

                            if (candle.low <= stopPrice) {
                                val execPrice = minOf(candle.open, stopPrice)
                                triggered.add(order to execPrice)
                                iterator.remove()
                            } else {
                                iterator.set(order.copy(isActivated = true, peakPrice = peak))
                            }
                        } else {
                            // Trailing Stop BUY
                            val isNowActive = order.isActivated || (order.activationPrice == null) || (candle.low <= order.activationPrice)
                            if (!isNowActive) {
                                // Still waiting for activation price
                                continue
                            }

                            val basePrice = if (order.isActivated) (order.peakPrice ?: candle.low) else (order.activationPrice ?: candle.low)
                            val trough = minOf(basePrice, candle.low)
                            val stopPrice = trough * (1.0 + trailingPct / 100.0)

                            if (candle.high >= stopPrice) {
                                val execPrice = maxOf(candle.open, stopPrice)
                                triggered.add(order to execPrice)
                                iterator.remove()
                            } else {
                                iterator.set(order.copy(isActivated = true, peakPrice = trough))
                            }
                        }
                    }
                }
            }
        }

        _pendingOrders.value = currentOrders
        return triggered
    }
}
