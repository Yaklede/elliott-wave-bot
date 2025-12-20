package io.github.yaklede.elliott.wave.principle.coin.marketdata

import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import org.springframework.stereotype.Service

@Service
class MarketDataService(
    private val bybitV5Client: BybitV5Client,
    private val candleRepository: CandleRepository,
) {
    suspend fun refreshRecent(
        category: String,
        symbol: String,
        interval: String,
        limit: Int,
    ): List<Candle> {
        val candles = bybitV5Client.getKlines(category, symbol, interval, null, null, limit)
        candleRepository.append(symbol, interval, candles)
        return candles
    }

    fun getRecent(symbol: String, interval: String, limit: Int): List<Candle> =
        candleRepository.getRecent(symbol, interval, limit)

    fun append(symbol: String, interval: String, candles: List<Candle>) {
        candleRepository.append(symbol, interval, candles)
    }

    fun clear(symbol: String, interval: String) {
        candleRepository.clear(symbol, interval)
    }
}
