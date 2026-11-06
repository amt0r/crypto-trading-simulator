package com.example.cryptotester.domain

import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.domain.model.CryptoSymbol
import org.junit.Assert.assertEquals
import org.junit.Test

class CandleAggregationTest {

    @Test
    fun testForming1hCandle_from15mCandles() {
        val hourStart = 1672524000000L // e.g. 22:00:00 UTC

        // 4 x 15m candles in the hour 22:00..23:00
        val c1 = Candle(CryptoSymbol.BTC, hourStart, open = 16500.0, high = 16550.0, low = 16480.0, close = 16520.0)
        val c2 = Candle(CryptoSymbol.BTC, hourStart + 15 * 60 * 1000L, open = 16520.0, high = 16600.0, low = 16510.0, close = 16580.0)
        val c3 = Candle(CryptoSymbol.BTC, hourStart + 30 * 60 * 1000L, open = 16580.0, high = 16590.0, low = 16450.0, close = 16470.0)
        val c4 = Candle(CryptoSymbol.BTC, hourStart + 45 * 60 * 1000L, open = 16470.0, high = 16510.0, low = 16460.0, close = 16500.0)

        // At 22:00 (1 candle elapsed in hour):
        val formingAt1 = buildFormingCandle(CryptoSymbol.BTC, hourStart, listOf(c1))
        assertEquals(16500.0, formingAt1.open, 0.001)
        assertEquals(16550.0, formingAt1.high, 0.001)
        assertEquals(16480.0, formingAt1.low, 0.001)
        assertEquals(16520.0, formingAt1.close, 0.001)
        assertEquals(hourStart, formingAt1.timestamp)

        // At 22:15 (2 candles elapsed):
        val formingAt2 = buildFormingCandle(CryptoSymbol.BTC, hourStart, listOf(c1, c2))
        assertEquals(16500.0, formingAt2.open, 0.001)
        assertEquals(16600.0, formingAt2.high, 0.001) // new high from c2
        assertEquals(16480.0, formingAt2.low, 0.001)
        assertEquals(16580.0, formingAt2.close, 0.001) // c2 close

        // At 22:30 (3 candles elapsed):
        val formingAt3 = buildFormingCandle(CryptoSymbol.BTC, hourStart, listOf(c1, c2, c3))
        assertEquals(16500.0, formingAt3.open, 0.001)
        assertEquals(16600.0, formingAt3.high, 0.001)
        assertEquals(16450.0, formingAt3.low, 0.001) // new low from c3
        assertEquals(16470.0, formingAt3.close, 0.001) // c3 close

        // At 22:45 (4 candles elapsed - end of hour):
        val formingAt4 = buildFormingCandle(CryptoSymbol.BTC, hourStart, listOf(c1, c2, c3, c4))
        assertEquals(16500.0, formingAt4.open, 0.001)
        assertEquals(16600.0, formingAt4.high, 0.001)
        assertEquals(16450.0, formingAt4.low, 0.001)
        assertEquals(16500.0, formingAt4.close, 0.001) // c4 close
    }

    @Test
    fun testForming1dCandle_from15mCandles() {
        val dayStart = 1672531200000L // 2023-01-01 00:00:00 UTC

        val c1 = Candle(CryptoSymbol.ETH, dayStart, open = 1200.0, high = 1210.0, low = 1195.0, close = 1205.0)
        val c2 = Candle(CryptoSymbol.ETH, dayStart + 4 * 3600 * 1000L, open = 1205.0, high = 1250.0, low = 1200.0, close = 1240.0)
        val c3 = Candle(CryptoSymbol.ETH, dayStart + 12 * 3600 * 1000L, open = 1240.0, high = 1245.0, low = 1180.0, close = 1220.0)

        val formingDay = buildFormingCandle(CryptoSymbol.ETH, dayStart, listOf(c1, c2, c3))
        assertEquals(1200.0, formingDay.open, 0.001)
        assertEquals(1250.0, formingDay.high, 0.001) // max of all day highs
        assertEquals(1180.0, formingDay.low, 0.001) // min of all day lows
        assertEquals(1220.0, formingDay.close, 0.001) // latest close
        assertEquals(dayStart, formingDay.timestamp)
    }

    @Test
    fun testDynamicLimitCalculation_accumulatesHistoryUpTo1000() {
        val duration15m = 15 * 60 * 1000L
        val startTs = 1000000000L

        // At start: elapsed is 0, limit must be 200
        val limitAtStart = calculateDynamicLimit(startTs, startTs, duration15m)
        assertEquals(200, limitAtStart)

        // After advancing 50 candles: limit is 250 (all 200 initial kept + 50 new)
        val timeAfter50 = startTs + 50 * duration15m
        val limitAfter50 = calculateDynamicLimit(startTs, timeAfter50, duration15m)
        assertEquals(250, limitAfter50)

        // After advancing 800 candles: limit is 1000 (capped at 5x 200)
        val timeAfter800 = startTs + 800 * duration15m
        val limitAfter800 = calculateDynamicLimit(startTs, timeAfter800, duration15m)
        assertEquals(1000, limitAfter800)

        // After advancing 2000 candles: limit remains capped at 1000
        val timeAfter2000 = startTs + 2000 * duration15m
        val limitAfter2000 = calculateDynamicLimit(startTs, timeAfter2000, duration15m)
        assertEquals(1000, limitAfter2000)
    }

    private fun buildFormingCandle(symbol: CryptoSymbol, periodStart: Long, candles15m: List<Candle>): Candle {
        return Candle(
            symbol = symbol,
            timestamp = periodStart,
            open = candles15m.first().open,
            high = candles15m.maxOf { it.high },
            low = candles15m.minOf { it.low },
            close = candles15m.last().close
        )
    }

    private fun calculateDynamicLimit(startTimestamp: Long, currentTime: Long, durationMs: Long): Int {
        val elapsedCandles = ((currentTime - startTimestamp) / durationMs).coerceAtLeast(0L)
        return (200 + elapsedCandles.toInt()).coerceIn(200, 1000)
    }
}
