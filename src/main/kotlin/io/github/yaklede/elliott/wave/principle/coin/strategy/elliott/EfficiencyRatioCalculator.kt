package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode

class EfficiencyRatioCalculator {
    fun compute(candles: List<Candle>, period: Int): BigDecimal? {
        if (period <= 0) return null
        if (candles.size < period + 1) return null
        val endIndex = candles.lastIndex
        val startIndex = endIndex - period
        val end = candles[endIndex].close
        val start = candles[startIndex].close
        val numerator = end.subtract(start).abs()

        var denominator = BigDecimal.ZERO
        for (i in startIndex + 1..endIndex) {
            val diff = candles[i].close.subtract(candles[i - 1].close).abs()
            denominator = denominator.add(diff)
        }
        if (denominator <= BigDecimal.ZERO) return BigDecimal.ZERO
        val er = numerator.divide(denominator, 6, RoundingMode.HALF_UP)
        return er.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
    }
}

private fun BigDecimal.coerceIn(min: BigDecimal, max: BigDecimal): BigDecimal {
    if (this < min) return min
    if (this > max) return max
    return this
}
