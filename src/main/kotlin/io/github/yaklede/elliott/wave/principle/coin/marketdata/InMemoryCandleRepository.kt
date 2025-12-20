package io.github.yaklede.elliott.wave.principle.coin.marketdata

import java.util.concurrent.ConcurrentHashMap

class InMemoryCandleRepository : CandleRepository {
    private val storage = ConcurrentHashMap<String, MutableList<Candle>>()

    override fun getRecent(symbol: String, interval: String, limit: Int): List<Candle> {
        val key = key(symbol, interval)
        val list = storage[key] ?: return emptyList()
        synchronized(list) {
            return list.takeLast(limit)
        }
    }

    override fun append(symbol: String, interval: String, candles: List<Candle>) {
        if (candles.isEmpty()) return
        val key = key(symbol, interval)
        val list = storage.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            val existingTimes = list.map { it.timeOpenMs }.toHashSet()
            candles.forEach { candle ->
                if (!existingTimes.contains(candle.timeOpenMs)) {
                    list.add(candle)
                }
            }
            list.sortBy { it.timeOpenMs }
        }
    }

    override fun clear(symbol: String, interval: String) {
        storage.remove(key(symbol, interval))
    }

    private fun key(symbol: String, interval: String): String = "${symbol.uppercase()}|$interval"
}
