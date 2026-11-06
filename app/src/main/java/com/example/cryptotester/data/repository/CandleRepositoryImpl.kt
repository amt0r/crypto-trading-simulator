package com.example.cryptotester.data.repository

import com.example.cryptotester.data.local.dao.CandleDao
import com.example.cryptotester.data.mapper.toDomain
import com.example.cryptotester.domain.model.Candle
import com.example.cryptotester.domain.model.CryptoSymbol
import com.example.cryptotester.domain.model.Timeframe
import com.example.cryptotester.domain.repository.CandleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CandleRepositoryImpl @Inject constructor(
    private val candleDao: CandleDao
) : CandleRepository {

    override fun getCandles(
        symbol: CryptoSymbol,
        timeframe: Timeframe,
        upToTimestamp: Long,
        limit: Int
    ): Flow<List<Candle>> {
        return when (timeframe) {
            Timeframe.M15 -> candleDao.getCandles15m(symbol.raw, upToTimestamp, limit)
                .map { entities -> entities.map { it.toDomain() }.reversed() }

            Timeframe.H1 -> {
                val hourStart = (upToTimestamp / 3_600_000L) * 3_600_000L
                combine(
                    candleDao.getCompletedCandles1h(symbol.raw, hourStart, (limit - 1).coerceAtLeast(1)),
                    candleDao.getCandles15mInRange(symbol.raw, hourStart, upToTimestamp)
                ) { completedEntities, forming15mEntities ->
                    val completedList = completedEntities.map { it.toDomain() }.reversed()
                    if (forming15mEntities.isNotEmpty()) {
                        val formingCandle = Candle(
                            symbol = symbol,
                            timestamp = hourStart,
                            open = forming15mEntities.first().open,
                            high = forming15mEntities.maxOf { it.high },
                            low = forming15mEntities.minOf { it.low },
                            close = forming15mEntities.last().close
                        )
                        completedList + formingCandle
                    } else {
                        completedList
                    }
                }
            }

            Timeframe.D1 -> {
                val dayStart = (upToTimestamp / 86_400_000L) * 86_400_000L
                combine(
                    candleDao.getCompletedCandles1d(symbol.raw, dayStart, (limit - 1).coerceAtLeast(1)),
                    candleDao.getCandles15mInRange(symbol.raw, dayStart, upToTimestamp)
                ) { completedEntities, forming15mEntities ->
                    val completedList = completedEntities.map { it.toDomain() }.reversed()
                    if (forming15mEntities.isNotEmpty()) {
                        val formingCandle = Candle(
                            symbol = symbol,
                            timestamp = dayStart,
                            open = forming15mEntities.first().open,
                            high = forming15mEntities.maxOf { it.high },
                            low = forming15mEntities.minOf { it.low },
                            close = forming15mEntities.last().close
                        )
                        completedList + formingCandle
                    } else {
                        completedList
                    }
                }
            }
        }
    }

    override suspend fun getLatest15mCandle(
        symbol: CryptoSymbol,
        upToTimestamp: Long
    ): Candle? {
        return candleDao.getLatest15mCandle(symbol.raw, upToTimestamp)?.toDomain()
    }

    override suspend fun getCandles15mBetween(
        symbol: CryptoSymbol,
        fromTimestamp: Long,
        toTimestamp: Long
    ): List<Candle> {
        return candleDao.getCandles15mBetween(symbol.raw, fromTimestamp, toTimestamp).map { it.toDomain() }
    }

    override suspend fun getTimeRange(): Pair<Long, Long> {
        val min = candleDao.getMinTimestamp() ?: 0L
        val max = candleDao.getMaxTimestamp() ?: 0L
        return Pair(min, max)
    }
}
