package io.github.yaklede.elliott.wave.principle.coin.marketdata

import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class CandleResampler {
    fun resample(candles: List<Candle>, intervalMs: Long): List<Candle> {
        if (candles.isEmpty() || intervalMs <= 0) return emptyList()
        val sorted = candles.sortedBy { it.timeOpenMs }
        val result = mutableListOf<Candle>()

        var bucketStart = bucketTime(sorted.first().timeOpenMs, intervalMs)
        var open = sorted.first().open
        var high = sorted.first().high
        var low = sorted.first().low
        var close = sorted.first().close
        var volume = sorted.first().volume

        for (i in 1 until sorted.size) {
            val candle = sorted[i]
            val candleBucket = bucketTime(candle.timeOpenMs, intervalMs)
            if (candleBucket != bucketStart) {
                result.add(
                    Candle(
                        timeOpenMs = bucketStart,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume,
                    )
                )
                bucketStart = candleBucket
                open = candle.open
                high = candle.high
                low = candle.low
                close = candle.close
                volume = candle.volume
            } else {
                if (candle.high > high) high = candle.high
                if (candle.low < low) low = candle.low
                close = candle.close
                volume = volume.add(candle.volume)
            }
        }

        result.add(
            Candle(
                timeOpenMs = bucketStart,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume,
            )
        )

        return result
    }

    private fun bucketTime(timeMs: Long, intervalMs: Long): Long =
        (timeMs / intervalMs) * intervalMs
}
