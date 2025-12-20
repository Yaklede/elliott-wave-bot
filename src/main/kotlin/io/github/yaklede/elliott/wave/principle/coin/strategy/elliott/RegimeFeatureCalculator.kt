package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode

class RegimeFeatureCalculator(
    private val atrCalculator: ATRCalculator = ATRCalculator(),
) {
    fun calculate(
        candles: List<Candle>,
        htfCandles: List<Candle>,
        volumePeriod: Int,
        atrPeriod: Int,
        slopeLookbackBars: Int,
    ): RegimeFeatures? {
        if (candles.isEmpty()) return null
        val close = candles.last().close
        if (close <= BigDecimal.ZERO) return null

        val atrPercent = computeAtrPercent(candles, atrPeriod, close)
        val relVolume = computeRelVolume(candles, volumePeriod)
        val trendSlope = computeTrendSlope(htfCandles, slopeLookbackBars)
        val maSpread = computeMaSpread(htfCandles, close)

        return RegimeFeatures(
            trendSlope = trendSlope,
            maSpread = maSpread,
            atrPercent = atrPercent,
            relVolume = relVolume,
        )
    }

    private fun computeAtrPercent(candles: List<Candle>, period: Int, close: BigDecimal): BigDecimal? {
        if (candles.size < period + 1) return null
        val atrValues = atrCalculator.calculate(candles, period)
        val atr = atrValues.lastOrNull { it != null } ?: return null
        if (close <= BigDecimal.ZERO) return null
        return atr.divide(close, 6, RoundingMode.HALF_UP)
    }

    private fun computeRelVolume(candles: List<Candle>, period: Int): BigDecimal? {
        if (candles.size < period + 1) return null
        val recent = candles.takeLast(period)
        val avg = recent.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.volume) }
            .divide(BigDecimal(period), 6, RoundingMode.HALF_UP)
        if (avg <= BigDecimal.ZERO) return null
        val lastVolume = candles.last().volume
        return lastVolume.divide(avg, 6, RoundingMode.HALF_UP)
    }

    private fun computeTrendSlope(htfCandles: List<Candle>, lookbackBars: Int): BigDecimal? {
        if (htfCandles.size < 50 + lookbackBars) return null
        val current = sma(htfCandles.takeLast(50))
        val pastSlice = htfCandles.dropLast(lookbackBars)
        if (pastSlice.size < 50) return null
        val past = sma(pastSlice.takeLast(50))
        if (past == BigDecimal.ZERO) return null
        return current.subtract(past).divide(past, 6, RoundingMode.HALF_UP)
    }

    private fun computeMaSpread(htfCandles: List<Candle>, close: BigDecimal): BigDecimal? {
        if (htfCandles.size < 200) return null
        val sma50 = sma(htfCandles.takeLast(50))
        val sma200 = sma(htfCandles.takeLast(200))
        if (close == BigDecimal.ZERO) return null
        return sma50.subtract(sma200).divide(close, 6, RoundingMode.HALF_UP)
    }

    private fun sma(candles: List<Candle>): BigDecimal {
        val sum = candles.fold(BigDecimal.ZERO) { acc, candle -> acc.add(candle.close) }
        return sum.divide(BigDecimal(candles.size), 8, RoundingMode.HALF_UP)
    }
}
