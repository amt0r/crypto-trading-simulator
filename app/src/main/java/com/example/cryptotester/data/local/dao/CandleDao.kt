package com.example.cryptotester.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.cryptotester.data.local.entity.Candle15mEntity
import com.example.cryptotester.data.local.entity.Candle1hEntity
import com.example.cryptotester.data.local.entity.Candle1dEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CandleDao {

    @Query("SELECT * FROM candle_15m WHERE symbol = :symbol AND timestamp <= :upToTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getCandles15m(symbol: String, upToTimestamp: Long, limit: Int): Flow<List<Candle15mEntity>>

    @Query("SELECT * FROM candle_15m WHERE symbol = :symbol AND timestamp <= :upToTimestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest15mCandle(symbol: String, upToTimestamp: Long): Candle15mEntity?

    @Query("SELECT MIN(timestamp) FROM candle_15m")
    suspend fun getMinTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM candle_15m")
    suspend fun getMaxTimestamp(): Long?

    @Query("SELECT * FROM candle_1h WHERE symbol = :symbol AND timestamp <= :upToTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getCandles1h(symbol: String, upToTimestamp: Long, limit: Int): Flow<List<Candle1hEntity>>

    @Query("SELECT * FROM candle_1h WHERE symbol = :symbol AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getCompletedCandles1h(symbol: String, beforeTimestamp: Long, limit: Int): Flow<List<Candle1hEntity>>

    @Query("SELECT * FROM candle_1d WHERE symbol = :symbol AND timestamp <= :upToTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getCandles1d(symbol: String, upToTimestamp: Long, limit: Int): Flow<List<Candle1dEntity>>

    @Query("SELECT * FROM candle_1d WHERE symbol = :symbol AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getCompletedCandles1d(symbol: String, beforeTimestamp: Long, limit: Int): Flow<List<Candle1dEntity>>

    @Query("SELECT * FROM candle_15m WHERE symbol = :symbol AND timestamp >= :fromTimestamp AND timestamp <= :toTimestamp ORDER BY timestamp ASC")
    fun getCandles15mInRange(symbol: String, fromTimestamp: Long, toTimestamp: Long): Flow<List<Candle15mEntity>>

    @Query("SELECT * FROM candle_15m WHERE symbol = :symbol AND timestamp > :fromTimestamp AND timestamp <= :toTimestamp ORDER BY timestamp ASC")
    suspend fun getCandles15mBetween(symbol: String, fromTimestamp: Long, toTimestamp: Long): List<Candle15mEntity>
}
