package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode

class ATRCalculator {
    fun calculate(candles: List<Candle>, period: Int): List<BigDecimal?> {
        if (candles.isEmpty() || period <= 0) return emptyList()
        val trueRanges = mutableListOf<BigDecimal>()
        for (i in candles.indices) {
            val candle = candles[i]
            if (i == 0) {
                trueRanges.add(candle.high.subtract(candle.low).abs())
            } else {
                val prevClose = candles[i - 1].close
                val tr1 = candle.high.subtract(candle.low).abs()
                val tr2 = candle.high.subtract(prevClose).abs()
                val tr3 = candle.low.subtract(prevClose).abs()
                trueRanges.add(maxOf(tr1, tr2, tr3))
            }
        }

        val atrValues = MutableList<BigDecimal?>(candles.size) { null }
        if (trueRanges.size < period) return atrValues

        var sum = trueRanges.take(period).fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        atrValues[period - 1] = sum.divide(BigDecimal(period), 8, RoundingMode.HALF_UP)
        for (i in period until trueRanges.size) {
            sum = sum.subtract(trueRanges[i - period]).add(trueRanges[i])
            atrValues[i] = sum.divide(BigDecimal(period), 8, RoundingMode.HALF_UP)
        }
        return atrValues
    }

    private fun maxOf(a: BigDecimal, b: BigDecimal, c: BigDecimal): BigDecimal {
        return if (a >= b && a >= c) a else if (b >= c) b else c
    }
}
