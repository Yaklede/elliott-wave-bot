package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.ElliottProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode

class ElliottScorer {
    fun scoreWave2(setup: Wave2Setup, htfCandles: List<Candle>, properties: ElliottProperties): BigDecimal {
        val fibScore = fibScore(setup, properties)
        val trendScore = trendScore(htfCandles)
        return fibScore.add(trendScore).divide(BigDecimal("2.0"), 4, RoundingMode.HALF_UP)
    }

    private fun fibScore(setup: Wave2Setup, properties: ElliottProperties): BigDecimal {
        val wave1Size = setup.wave1End.price.subtract(setup.wave1Start.price)
        if (wave1Size == BigDecimal.ZERO) return BigDecimal.ZERO
        val retrace = setup.wave1End.price.subtract(setup.wave2End.price)
            .divide(wave1Size, 4, RoundingMode.HALF_UP)
        val min = properties.fib.wave2PreferredMin
        val max = properties.fib.wave2PreferredMax
        val diff = when {
            retrace < min -> min.subtract(retrace)
            retrace > max -> retrace.subtract(max)
            else -> BigDecimal.ZERO
        }
        val score = BigDecimal.ONE.subtract(diff)
        return score.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
    }

    private fun trendScore(htfCandles: List<Candle>): BigDecimal {
        if (htfCandles.size < 200) return BigDecimal("0.5")
        val sma50 = sma(htfCandles.takeLast(50))
        val sma200 = sma(htfCandles.takeLast(200))
        return if (sma50 > sma200) BigDecimal.ONE else BigDecimal.ZERO
    }

    private fun sma(candles: List<Candle>): BigDecimal {
        val sum = candles.fold(BigDecimal.ZERO) { acc, candle -> acc.add(candle.close) }
        return sum.divide(BigDecimal(candles.size), 8, RoundingMode.HALF_UP)
    }
}

private fun BigDecimal.coerceIn(min: BigDecimal, max: BigDecimal): BigDecimal {
    if (this < min) return min
    if (this > max) return max
    return this
}
