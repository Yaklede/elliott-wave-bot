package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.ZigZagMode
import io.github.yaklede.elliott.wave.principle.coin.config.ZigZagProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal

class ZigZagExtractor(
    private val properties: ZigZagProperties,
) {
    private val atrCalculator = ATRCalculator()

    fun extract(candles: List<Candle>): List<SwingPoint> {
        if (candles.size < 2) return emptyList()
        val swings = mutableListOf<SwingPoint>()
        val atrValues = if (properties.mode == ZigZagMode.ATR) {
            atrCalculator.calculate(candles, properties.atrPeriod)
        } else {
            emptyList()
        }

        var direction: SwingType? = null
        var pivotPrice = candles.first().close
        var pivotTime = candles.first().timeOpenMs
        var extremePrice = pivotPrice
        var extremeTime = pivotTime

        for (i in 1 until candles.size) {
            val candle = candles[i]
            val price = candle.close
            val threshold = thresholdForIndex(i, pivotPrice, atrValues)

            if (direction == null) {
                if (price >= pivotPrice.add(threshold)) {
                    swings.add(SwingPoint(pivotTime, pivotPrice, SwingType.LOW))
                    direction = SwingType.HIGH
                    extremePrice = price
                    extremeTime = candle.timeOpenMs
                } else if (price <= pivotPrice.subtract(threshold)) {
                    swings.add(SwingPoint(pivotTime, pivotPrice, SwingType.HIGH))
                    direction = SwingType.LOW
                    extremePrice = price
                    extremeTime = candle.timeOpenMs
                }
                continue
            }

            if (direction == SwingType.HIGH) {
                if (price > extremePrice) {
                    extremePrice = price
                    extremeTime = candle.timeOpenMs
                }
                val reversalThreshold = thresholdForIndex(i, extremePrice, atrValues)
                if (price <= extremePrice.subtract(reversalThreshold)) {
                    swings.add(SwingPoint(extremeTime, extremePrice, SwingType.HIGH))
                    direction = SwingType.LOW
                    pivotPrice = extremePrice
                    pivotTime = extremeTime
                    extremePrice = price
                    extremeTime = candle.timeOpenMs
                }
            } else {
                if (price < extremePrice) {
                    extremePrice = price
                    extremeTime = candle.timeOpenMs
                }
                val reversalThreshold = thresholdForIndex(i, extremePrice, atrValues)
                if (price >= extremePrice.add(reversalThreshold)) {
                    swings.add(SwingPoint(extremeTime, extremePrice, SwingType.LOW))
                    direction = SwingType.HIGH
                    pivotPrice = extremePrice
                    pivotTime = extremeTime
                    extremePrice = price
                    extremeTime = candle.timeOpenMs
                }
            }
        }

        if (direction != null) {
            val lastType = if (direction == SwingType.HIGH) SwingType.HIGH else SwingType.LOW
            if (swings.isEmpty() || swings.last().type != lastType) {
                swings.add(SwingPoint(extremeTime, extremePrice, lastType))
            }
        }
        return swings
    }

    private fun thresholdForIndex(
        index: Int,
        basePrice: BigDecimal,
        atrValues: List<BigDecimal?>,
    ): BigDecimal {
        return when (properties.mode) {
            ZigZagMode.PERCENT -> basePrice.multiply(properties.percentThreshold)
            ZigZagMode.ATR -> {
                val atr = atrValues.getOrNull(index) ?: return basePrice.multiply(properties.percentThreshold)
                atr.multiply(properties.atrMultiplier)
            }
        }
    }
}
