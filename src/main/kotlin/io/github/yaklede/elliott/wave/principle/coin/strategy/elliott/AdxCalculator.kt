package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max

class AdxCalculator {
    fun compute(candles: List<Candle>, period: Int): BigDecimal? {
        if (period <= 0) return null
        if (candles.size < period * 2) return null

        val highs = candles.map { it.high.toDouble() }
        val lows = candles.map { it.low.toDouble() }
        val closes = candles.map { it.close.toDouble() }

        val trList = ArrayList<Double>()
        val plusDmList = ArrayList<Double>()
        val minusDmList = ArrayList<Double>()

        for (i in 1 until candles.size) {
            val highDiff = highs[i] - highs[i - 1]
            val lowDiff = lows[i - 1] - lows[i]
            val plusDm = if (highDiff > lowDiff && highDiff > 0) highDiff else 0.0
            val minusDm = if (lowDiff > highDiff && lowDiff > 0) lowDiff else 0.0
            val tr = max(highs[i] - lows[i], max(abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])))
            trList.add(tr)
            plusDmList.add(plusDm)
            minusDmList.add(minusDm)
        }

        var tr14 = trList.subList(0, period).sum()
        var plusDm14 = plusDmList.subList(0, period).sum()
        var minusDm14 = minusDmList.subList(0, period).sum()

        val dxValues = ArrayList<Double>()
        for (i in period until trList.size) {
            val plusDi = if (tr14 == 0.0) 0.0 else 100.0 * (plusDm14 / tr14)
            val minusDi = if (tr14 == 0.0) 0.0 else 100.0 * (minusDm14 / tr14)
            val dx = if (plusDi + minusDi == 0.0) 0.0 else 100.0 * abs(plusDi - minusDi) / (plusDi + minusDi)
            dxValues.add(dx)

            if (i + 1 < trList.size) {
                tr14 = tr14 - (tr14 / period) + trList[i]
                plusDm14 = plusDm14 - (plusDm14 / period) + plusDmList[i]
                minusDm14 = minusDm14 - (minusDm14 / period) + minusDmList[i]
            }
        }

        if (dxValues.size < period) return null
        var adx = dxValues.subList(0, period).average()
        for (i in period until dxValues.size) {
            adx = ((adx * (period - 1)) + dxValues[i]) / period
        }
        return BigDecimal(adx).setScale(4, RoundingMode.HALF_UP)
    }
}
