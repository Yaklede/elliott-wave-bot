package io.github.yaklede.elliott.wave.principle.coin.marketdata

interface CandleRepository {
    fun getRecent(symbol: String, interval: String, limit: Int): List<Candle>
    fun append(symbol: String, interval: String, candles: List<Candle>)
    fun clear(symbol: String, interval: String)
}
