package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.ElliottProperties
import io.github.yaklede.elliott.wave.principle.coin.config.VolumeProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode

class ElliottScorer {
    fun scoreWave2(
        setup: Wave2Setup,
        candles: List<Candle>,
        htfCandles: List<Candle>,
        elliott: ElliottProperties,
        volume: VolumeProperties,
        isLong: Boolean,
    ): BigDecimal {
        val fibScore = fibScore(setup, elliott, isLong)
        val trendScore = trendScore(htfCandles, isLong)
        val volumeScore = volumeScore(setup, candles, volume)
        return fibScore.add(trendScore).add(volumeScore)
            .divide(BigDecimal("3.0"), 4, RoundingMode.HALF_UP)
    }

    fun confidenceScore(
        setup: Wave2Setup,
        candles: List<Candle>,
        htfCandles: List<Candle>,
        elliott: ElliottProperties,
        volume: VolumeProperties,
        atrValue: BigDecimal?,
        isLong: Boolean,
    ): BigDecimal {
        val fibScore = fibScore(setup, elliott, isLong)
        val trendScore = trendScore(htfCandles, isLong)
        val volumeScore = volumeScore(setup, candles, volume)
        val swingScore = swingStrengthScore(setup, atrValue, elliott.swingAtrMultiplier)
        return fibScore.add(trendScore).add(volumeScore).add(swingScore)
            .divide(BigDecimal("4.0"), 4, RoundingMode.HALF_UP)
    }

    private fun fibScore(setup: Wave2Setup, properties: ElliottProperties, isLong: Boolean): BigDecimal {
        val wave1Size = setup.wave1End.price.subtract(setup.wave1Start.price).abs()
        if (wave1Size == BigDecimal.ZERO) return BigDecimal.ZERO
        val retrace = if (isLong) {
            setup.wave1End.price.subtract(setup.wave2End.price)
        } else {
            setup.wave2End.price.subtract(setup.wave1End.price)
        }
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

    private fun trendScore(htfCandles: List<Candle>, isLong: Boolean): BigDecimal {
        if (htfCandles.size < 200) return BigDecimal("0.5")
        val sma50 = sma(htfCandles.takeLast(50))
        val sma200 = sma(htfCandles.takeLast(200))
        return if (isLong) {
            if (sma50 > sma200) BigDecimal.ONE else BigDecimal.ZERO
        } else {
            if (sma50 < sma200) BigDecimal.ONE else BigDecimal.ZERO
        }
    }

    private fun volumeScore(setup: Wave2Setup, candles: List<Candle>, volume: VolumeProperties): BigDecimal {
        if (candles.isEmpty()) return BigDecimal("0.5")
        val wave1Candles = candles.filter { it.timeOpenMs in setup.wave1Start.timeMs..setup.wave1End.timeMs }
        if (wave1Candles.isEmpty()) return BigDecimal("0.5")
        val baseline = candles.takeLast(volume.period)
        if (baseline.isEmpty()) return BigDecimal("0.5")

        val wave1Avg = wave1Candles.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.volume) }
            .divide(BigDecimal(wave1Candles.size), 6, RoundingMode.HALF_UP)
        val baselineAvg = baseline.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.volume) }
            .divide(BigDecimal(baseline.size), 6, RoundingMode.HALF_UP)
        if (baselineAvg <= BigDecimal.ZERO) return BigDecimal("0.5")

        val ratio = wave1Avg.divide(baselineAvg, 6, RoundingMode.HALF_UP)
        val normalized = ratio.divide(volume.minMultiplier, 6, RoundingMode.HALF_UP)
        return normalized.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
    }

    private fun swingStrengthScore(
        setup: Wave2Setup,
        atrValue: BigDecimal?,
        atrMultiplier: BigDecimal,
    ): BigDecimal {
        if (atrValue == null || atrValue == BigDecimal.ZERO) return BigDecimal("0.5")
        val wave1Size = setup.wave1End.price.subtract(setup.wave1Start.price).abs()
        val baseline = atrValue.multiply(atrMultiplier)
        if (baseline == BigDecimal.ZERO) return BigDecimal("0.5")
        val ratio = wave1Size.divide(baseline, 6, RoundingMode.HALF_UP)
        return ratio.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)
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
