package com.example.cryptotester.domain

import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.domain.model.CryptoSymbol
import com.example.cryptotester.domain.model.OrderType
import com.example.cryptotester.domain.model.PendingOrder
import com.example.cryptotester.domain.model.TradeSide
import com.example.cryptotester.domain.simulation.PendingOrderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingOrderManagerTest {

    private lateinit var manager: PendingOrderManager

    @Before
    fun setUp() {
        manager = PendingOrderManager()
    }

    @Test
    fun limitBuy_triggersWhenPriceTouchesOrDropsBelowLimit() {
        val order = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.BUY,
            orderType = OrderType.LIMIT,
            percentage = 0.5,
            limitPrice = 50000.0,
            initialPrice = 52000.0,
            displayTargetPrice = 50000.0
        )
        manager.addOrder(order)

        // Candle above limit: no trigger
        val candle1 = Candle(CryptoSymbol.BTC, 1000L, 52000.0, 53000.0, 51000.0, 51500.0)
        val triggered1 = manager.processCandles(listOf(candle1))
        assertTrue(triggered1.isEmpty())
        assertEquals(1, manager.pendingOrders.value.size)

        // Candle dipping to 49500: triggers!
        val candle2 = Candle(CryptoSymbol.BTC, 2000L, 51500.0, 52000.0, 49500.0, 50200.0)
        val triggered2 = manager.processCandles(listOf(candle2))
        assertEquals(1, triggered2.size)
        assertEquals(50000.0, triggered2.first().second, 0.01)
        assertTrue(manager.pendingOrders.value.isEmpty())
    }

    @Test
    fun limitSell_triggersWhenPriceTouchesOrExceedsLimit() {
        val order = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.SELL,
            orderType = OrderType.LIMIT,
            percentage = 1.0,
            limitPrice = 60000.0,
            initialPrice = 55000.0,
            displayTargetPrice = 60000.0
        )
        manager.addOrder(order)

        // Candle reaching 61000: triggers!
        val candle = Candle(CryptoSymbol.BTC, 1000L, 58000.0, 61000.0, 57500.0, 60500.0)
        val triggered = manager.processCandles(listOf(candle))
        assertEquals(1, triggered.size)
        assertEquals(60000.0, triggered.first().second, 0.01)
        assertTrue(manager.pendingOrders.value.isEmpty())
    }

    @Test
    fun trailingStopSell_tracksPeakAndTriggersOnDrop() {
        // Trailing 10% on initial 100
        val order = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.SELL,
            orderType = OrderType.TRAILING_STOP,
            percentage = 1.0,
            trailingPercent = 10.0,
            initialPrice = 100.0,
            peakPrice = 100.0,
            displayTargetPrice = 90.0
        )
        manager.addOrder(order)

        // Price rises to 150 (new peak). Stop price becomes 150 * 0.90 = 135. Low was 140 (above 135)
        val candle1 = Candle(CryptoSymbol.BTC, 1000L, 100.0, 150.0, 140.0, 145.0)
        val triggered1 = manager.processCandles(listOf(candle1))
        assertTrue(triggered1.isEmpty())
        assertEquals(150.0, manager.pendingOrders.value.first().peakPrice!!, 0.01)

        // Price drops to 130: triggers stop at 135!
        val candle2 = Candle(CryptoSymbol.BTC, 2000L, 145.0, 145.0, 130.0, 132.0)
        val triggered2 = manager.processCandles(listOf(candle2))
        assertEquals(1, triggered2.size)
        assertEquals(135.0, triggered2.first().second, 0.01)
        assertTrue(manager.pendingOrders.value.isEmpty())
    }
}
