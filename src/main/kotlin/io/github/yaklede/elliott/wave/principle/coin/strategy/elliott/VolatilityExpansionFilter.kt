package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.VolExpansionProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode

class VolatilityExpansionFilter(
    private val properties: VolExpansionProperties,
    private val calculator: BollingerBandwidthCalculator = BollingerBandwidthCalculator(),
) {
    fun passes(candles: List<Candle>): Boolean {
        if (!properties.enabled) return true
        if (candles.size < properties.period + 2) return true
        val bandwidths = calculator.bandwidths(
            candles = candles,
            period = properties.period,
            stdDevMultiplier = properties.stdDevMultiplier.toDouble(),
        ).filterNotNull()
        if (bandwidths.size < properties.lookback) return true

        val lookbackWindow = bandwidths.takeLast(properties.lookback)
        val threshold = quantile(lookbackWindow, properties.compressionQuantile)
        val compressionWindow = lookbackWindow.takeLast(properties.recentCompressionBars)
        val hadCompression = compressionWindow.any { it < threshold }
        if (!hadCompression) return false

        if (properties.requireRising) {
            val current = lookbackWindow.lastOrNull() ?: return false
            val prev = lookbackWindow.getOrNull(lookbackWindow.size - 2) ?: return false
            if (current <= prev) return false
        }
        return true
    }

    private fun quantile(values: List<BigDecimal>, q: BigDecimal): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        val clamped = q.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
        val sorted = values.sorted()
        val index = (clamped.toDouble() * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index].setScale(8, RoundingMode.HALF_UP)
    }
}
