package com.example.cryptotester.domain

import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.domain.model.CryptoSymbol
import com.example.cryptotester.domain.model.OrderType
import com.example.cryptotester.domain.model.PendingOrder
import com.example.cryptotester.domain.model.Portfolio
import com.example.cryptotester.domain.model.TradeOrder
import com.example.cryptotester.domain.model.TradeSide
import com.example.cryptotester.domain.simulation.PendingOrderManager
import com.example.cryptotester.domain.usecase.ExecuteTradeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrderExecutionExtendedTest {

    private lateinit var orderManager: PendingOrderManager
    private val executeTradeUseCase = ExecuteTradeUseCase()

    @Before
    fun setUp() {
        orderManager = PendingOrderManager()
    }

    @Test
    fun testMarketBuy_executesWithFee() {
        val portfolio = Portfolio(usdtBalance = 1000.0)
        val order = TradeOrder(CryptoSymbol.BTC, TradeSide.BUY, percentage = 0.5)
        val executionPrice = 50000.0

        val result = executeTradeUseCase(order, portfolio, executionPrice)

        // 50% of 1000 = 500 USDT spent
        // Fee = 500 * 0.001 = 0.5 USDT
        // Net USDT to buy = 499.5 USDT
        // Amount = 499.5 / 50000 = 0.00999 BTC
        assertEquals(executionPrice, result.executionPrice, 0.001)
        assertEquals(0.5, result.fee, 0.001)
        assertEquals(0.00999, result.amount, 0.00001)
        assertEquals(500.0, result.updatedPortfolio.usdtBalance, 0.001)
        assertEquals(0.00999, result.updatedPortfolio.getHolding(CryptoSymbol.BTC), 0.00001)
    }

    @Test
    fun testMarketSell_executesWithFee() {
        val portfolio = Portfolio(
            usdtBalance = 100.0,
            holdings = mapOf(CryptoSymbol.BTC to 1.0)
        )
        val order = TradeOrder(CryptoSymbol.BTC, TradeSide.SELL, percentage = 1.0)
        val executionPrice = 60000.0

        val result = executeTradeUseCase(order, portfolio, executionPrice)

        // Gross USDT = 1.0 * 60000 = 60000
        // Fee = 60000 * 0.001 = 60 USDT
        // Net USDT = 59940 USDT
        assertEquals(60.0, result.fee, 0.001)
        assertEquals(1.0, result.amount, 0.001)
        assertEquals(60040.0, result.updatedPortfolio.usdtBalance, 0.001)
        assertEquals(0.0, result.updatedPortfolio.getHolding(CryptoSymbol.BTC), 0.001)
    }

    @Test
    fun testLimitBuyAndSell() {
        val limitBuy = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.BUY,
            orderType = OrderType.LIMIT,
            percentage = 0.5,
            limitPrice = 40000.0,
            initialPrice = 45000.0,
            displayTargetPrice = 40000.0
        )
        val limitSell = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.SELL,
            orderType = OrderType.LIMIT,
            percentage = 1.0,
            limitPrice = 50000.0,
            initialPrice = 45000.0,
            displayTargetPrice = 50000.0
        )
        orderManager.addOrder(limitBuy)
        orderManager.addOrder(limitSell)

        // Candle between 41000 and 49000 (neither limit reached)
        val c1 = Candle(CryptoSymbol.BTC, 1000L, 45000.0, 49000.0, 41000.0, 46000.0)
        val trig1 = orderManager.processCandles(listOf(c1))
        assertTrue(trig1.isEmpty())
        assertEquals(2, orderManager.pendingOrders.value.size)

        // Candle dipping to 39500 (triggers BUY)
        val c2 = Candle(CryptoSymbol.BTC, 2000L, 46000.0, 47000.0, 39500.0, 42000.0)
        val trig2 = orderManager.processCandles(listOf(c2))
        assertEquals(1, trig2.size)
        assertEquals(limitBuy.id, trig2.first().first.id)
        assertEquals(40000.0, trig2.first().second, 0.01)

        // Candle spiking to 51000 (triggers SELL)
        val c3 = Candle(CryptoSymbol.BTC, 3000L, 42000.0, 51000.0, 42000.0, 49000.0)
        val trig3 = orderManager.processCandles(listOf(c3))
        assertEquals(1, trig3.size)
        assertEquals(limitSell.id, trig3.first().first.id)
        assertEquals(50000.0, trig3.first().second, 0.01)
        assertTrue(orderManager.pendingOrders.value.isEmpty())
    }

    @Test
    fun testTrailingStopSell_withActivationPriceAndDelta() {
        // Current price = 50000.
        // User sets: Activation Price = 55000, Trailing Delta = 2%
        val order = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.SELL,
            orderType = OrderType.TRAILING_STOP,
            percentage = 1.0,
            trailingPercent = 2.0, // 2% delta
            activationPrice = 55000.0,
            displayActivationPrice = 55000.0,
            isActivated = false,
            initialPrice = 50000.0,
            displayTargetPrice = 55000.0
        )
        orderManager.addOrder(order)

        // Step 1: Price drops to 48000 and rises to 54000.
        // High is 54000 < 55000 (Activation Price). Order must NOT activate!
        val c1 = Candle(CryptoSymbol.BTC, 1000L, 50000.0, 54000.0, 48000.0, 52000.0)
        val trig1 = orderManager.processCandles(listOf(c1))
        assertTrue(trig1.isEmpty())
        assertFalse(orderManager.pendingOrders.value.first().isActivated)

        // Step 2: Price surges to 60000!
        // High 60000 >= 55000 -> Order ACTIVATES! Peak becomes 60000.
        // Stop price is 60000 * (1 - 0.02) = 58800.
        // Candle low was 59000 (> 58800), so no trigger yet.
        val c2 = Candle(CryptoSymbol.BTC, 2000L, 52000.0, 60000.0, 59000.0, 59800.0)
        val trig2 = orderManager.processCandles(listOf(c2))
        assertTrue(trig2.isEmpty())
        val activeOrder = orderManager.pendingOrders.value.first()
        assertTrue(activeOrder.isActivated)
        assertEquals(60000.0, activeOrder.peakPrice!!, 0.01)

        // Step 3: Price drops to 58500 (which is <= stop price 58800, i.e. 2% drop from 60000 peak)!
        // Order must TRIGGER!
        val c3 = Candle(CryptoSymbol.BTC, 3000L, 59800.0, 59900.0, 58500.0, 58600.0)
        val trig3 = orderManager.processCandles(listOf(c3))
        assertEquals(1, trig3.size)
        assertEquals(58800.0, trig3.first().second, 0.01)
        assertTrue(orderManager.pendingOrders.value.isEmpty())
    }

    @Test
    fun testTrailingStopBuy_withActivationPriceAndDelta() {
        // Current price = 50000.
        // User sets: Activation Price = 45000, Trailing Delta = 1%
        val order = PendingOrder(
            symbol = CryptoSymbol.BTC,
            side = TradeSide.BUY,
            orderType = OrderType.TRAILING_STOP,
            percentage = 1.0,
            trailingPercent = 1.0, // 1% delta
            activationPrice = 45000.0,
            displayActivationPrice = 45000.0,
            isActivated = false,
            initialPrice = 50000.0,
            displayTargetPrice = 45000.0
        )
        orderManager.addOrder(order)

        // Step 1: Price drops to 46000 (> 45000). Not activated.
        val c1 = Candle(CryptoSymbol.BTC, 1000L, 50000.0, 51000.0, 46000.0, 47000.0)
        val trig1 = orderManager.processCandles(listOf(c1))
        assertTrue(trig1.isEmpty())
        assertFalse(orderManager.pendingOrders.value.first().isActivated)

        // Step 2: Price plunges to 40000 (<= 45000). Activates! Trough becomes 40000.
        // Stop price is 40000 * (1 + 0.01) = 40400.
        // Candle high was 40300 (< 40400), not triggered yet.
        val c2 = Candle(CryptoSymbol.BTC, 2000L, 47000.0, 40300.0, 40000.0, 40100.0)
        val trig2 = orderManager.processCandles(listOf(c2))
        assertTrue(trig2.isEmpty())
        assertTrue(orderManager.pendingOrders.value.first().isActivated)
        assertEquals(40000.0, orderManager.pendingOrders.value.first().peakPrice!!, 0.01)

        // Step 3: Price bounces to 40600 (>= 40400). Triggers BUY!
        val c3 = Candle(CryptoSymbol.BTC, 3000L, 40100.0, 40600.0, 40050.0, 40500.0)
        val trig3 = orderManager.processCandles(listOf(c3))
        assertEquals(1, trig3.size)
        assertEquals(40400.0, trig3.first().second, 0.01)
        assertTrue(orderManager.pendingOrders.value.isEmpty())
    }
}
