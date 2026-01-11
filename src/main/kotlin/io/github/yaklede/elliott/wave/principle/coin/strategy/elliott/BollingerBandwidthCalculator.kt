package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

class BollingerBandwidthCalculator {
    fun bandwidths(
        candles: List<Candle>,
        period: Int,
        stdDevMultiplier: Double,
    ): List<BigDecimal?> {
        if (period <= 0) return candles.map { null }
        val closes = candles.map { it.close.toDouble() }
        val result = MutableList<BigDecimal?>(candles.size) { null }
        for (i in candles.indices) {
            if (i + 1 < period) continue
            val window = closes.subList(i + 1 - period, i + 1)
            val mean = window.average()
            val variance = window.fold(0.0) { acc, v ->
                val diff = v - mean
                acc + diff * diff
            } / window.size
            val stdDev = sqrt(variance)
            if (mean <= 0.0) continue
            val upper = mean + stdDevMultiplier * stdDev
            val lower = mean - stdDevMultiplier * stdDev
            val bandwidth = (upper - lower) / mean
            result[i] = BigDecimal(bandwidth).setScale(8, RoundingMode.HALF_UP)
        }
        return result
    }
}
